package org.getfit.app.nutrition

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.getfit.app.BuildConfig
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

/**
 * Meals the `meal-photo` skill has published, picked up from the repository.
 *
 * The online half of how a meal analysed at a desk reaches the phone. The skill
 * commits one JSON file per meal to `nutrition/inbox/` on the [BRANCH] branch;
 * the app lists that directory and reads whatever it has not already imported.
 *
 * The direction of travel is the point. The app only ever reads, over the same
 * public, tokenless path the updater uses — there is no credential in the APK
 * and there is nothing here that could write to the repository even if there
 * were. Which also means the app cannot tidy up after itself: an imported meal
 * stays in the inbox, and every poll sees it again. Dedupe is therefore local
 * and permanent, by [MealEntry.id] — see [selectNew].
 *
 * The inbox lives on its own branch rather than on dev, so publishing lunch is
 * not a commit to the code and cannot appear in a diff or a release.
 */
class MealInbox(
    private val owner: String = BuildConfig.REPO_OWNER,
    private val repo: String = BuildConfig.REPO_NAME,
) {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Fetches the inbox and returns the meals not in [seenIds].
     *
     * One request for the listing, then one per genuinely new meal. Anonymous
     * GitHub API calls are limited to sixty an hour from an address, so a poll
     * that finds nothing new — which is nearly all of them — must cost exactly
     * one of those, and this is why the listing is not simply followed by
     * reading everything in it.
     */
    suspend fun fetchNew(seenIds: Set<String>): InboxResult = withContext(Dispatchers.IO) {
        try {
            val listing = getJsonArray(
                "https://api.github.com/repos/$owner/$repo/contents/$INBOX_PATH?ref=$BRANCH"
            ) ?: return@withContext InboxResult.Empty

            val candidates = (0 until listing.length())
                .map { listing.getJSONObject(it) }
                .filter { it.optString("type") == "file" && it.optString("name").endsWith(".json") }
                // A meal is a small object. Anything large is not one, and
                // fetching it would only be a way to stall the phone.
                .filter { it.optInt("size") in 1..MAX_ENTRY_BYTES }
                // Named <id>.json by the skill, so the listing alone is enough
                // to tell whether a file has already been imported — without
                // this the request count would be one per meal ever published,
                // every poll, and the hourly limit would be gone by lunchtime.
                .filter { it.optString("name").removeSuffix(".json") !in seenIds }
                .mapNotNull { it.optString("download_url").ifBlank { null } }

            if (candidates.isEmpty()) return@withContext InboxResult.Empty

            val entries = candidates
                .take(MAX_PER_POLL)
                .mapNotNull { url -> readEntry(url) }
                // The file name is a claim about identity; the entry inside is
                // the truth. Filter again on what was actually read, so a
                // mismatched pair cannot slip a duplicate through.
                .let { selectNew(it, seenIds) }

            if (entries.isEmpty()) InboxResult.Empty else InboxResult.Found(entries)
        } catch (e: Exception) {
            InboxResult.Failed(e.message ?: "Could not reach the meal inbox.")
        }
    }

    private fun readEntry(url: String): MealEntry? {
        val body = getText(url) ?: return null
        val entry = runCatching {
            json.decodeFromString(MealEntry.serializer(), body)
        }.getOrNull() ?: return null
        // Same validation the deep link goes through. A meal is no more trusted
        // for having come from the repository.
        return (MealLink.validate(entry) as? MealLinkResult.Ok)?.entry
    }

    private fun openConnection(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "GetFit-MealInbox")
            connectTimeout = 15_000
            readTimeout = 15_000
        }

    private fun getText(url: String): String? {
        val connection = openConnection(url)
        try {
            if (connection.responseCode != 200) return null
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun getJsonArray(url: String): JSONArray? {
        val text = getText(url) ?: return null
        return runCatching { JSONArray(text) }.getOrNull()
    }

    companion object {
        const val BRANCH = "nutrition-inbox"
        const val INBOX_PATH = "nutrition/inbox"

        /** Generous for a meal, small enough that a stray file cannot stall a poll. */
        const val MAX_ENTRY_BYTES = 64 * 1024

        /**
         * A cap on requests per poll, not on meals. The rest arrive on the next
         * one — a backlog is worth draining slowly, and burning the hourly
         * allowance on it would mean no polls at all for a while afterwards.
         */
        const val MAX_PER_POLL = 20
    }
}

/**
 * Which of [remote] have not been imported yet, newest first.
 *
 * Pulled out of the fetch because it is the part with a rule in it, and the
 * part worth testing without a network: importing the same meal twice is the
 * failure this exists to prevent, and the app has no way to mark the inbox.
 */
fun selectNew(remote: List<MealEntry>, seenIds: Set<String>): List<MealEntry> =
    remote.asSequence()
        .filter { it.id !in seenIds }
        // A duplicate id inside one batch is still a duplicate.
        .distinctBy { it.id }
        .sortedByDescending { it.capturedAt }
        .toList()

sealed interface InboxResult {
    data class Found(val entries: List<MealEntry>) : InboxResult
    data object Empty : InboxResult
    data class Failed(val reason: String) : InboxResult
}
