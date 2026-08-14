package org.getfit.app.nutrition

import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream

/**
 * A meal carried in a `getfit://` link.
 *
 * The offline half of how the skill reaches the phone: no network, no
 * repository, nothing to poll — the whole meal travels inside the link, so it
 * works from a QR code, a message to yourself, or the share sheet. The other
 * half is [MealInbox], which is what makes a meal analysed at a desk turn up on
 * the phone without anyone tapping anything.
 *
 * ```
 * getfit://meal?v=1&d=<base64url of the MealEntry JSON>
 * ```
 *
 * The host is `meal` on a production install and `meal-dev` on a dev one, so
 * with both apps on one phone a link opens the build it was made for instead of
 * raising a chooser.
 *
 * Parsing the URI itself is left to the caller, which on Android means
 * `android.net.Uri` — it is better at that than anything written here would be.
 * What this owns is the part worth testing: turning the payload back into a
 * meal, and refusing one that has been mangled.
 */
object MealLink {

    const val SCHEME = "getfit"
    const val PARAM_VERSION = "v"
    const val PARAM_DATA = "d"

    /** Link format version, which is not the same thing as the meal schema version. */
    const val VERSION = 1

    private const val HOST_PRODUCTION = "meal"
    private const val HOST_DEV = "meal-dev"

    /**
     * Lenient on the way in: a link that carries an unexpected field is a link
     * from a newer skill, and dropping the field beats refusing the meal.
     */
    private val json = Json { ignoreUnknownKeys = true }

    fun hostFor(isDevBuild: Boolean): String = if (isDevBuild) HOST_DEV else HOST_PRODUCTION

    /** Builds the link for [entry]. Base64url needs no escaping, so this is the whole URI. */
    fun encode(entry: MealEntry, isDevBuild: Boolean): String {
        val payload = base64UrlEncode(json.encodeToString(MealEntry.serializer(), entry).toByteArray())
        return "$SCHEME://${hostFor(isDevBuild)}?$PARAM_VERSION=$VERSION&$PARAM_DATA=$payload"
    }

    /**
     * Turns the `d` parameter back into a meal.
     *
     * Everything here is untrusted — a link can arrive from anywhere and may
     * have been truncated by whatever carried it — so every failure has its own
     * answer rather than a null that the caller has to guess the meaning of.
     */
    fun decodePayload(data: String?): MealLinkResult {
        if (data.isNullOrBlank()) return MealLinkResult.Malformed("The link carried no meal.")

        val bytes = base64UrlDecode(data)
            ?: return MealLinkResult.Malformed("The link is damaged — it may have been cut short.")

        val entry = try {
            json.decodeFromString(MealEntry.serializer(), bytes.toString(Charsets.UTF_8))
        } catch (e: Exception) {
            return MealLinkResult.Malformed("The link does not contain a meal.")
        }

        if (entry.schemaVersion > MealEntry.SCHEMA_VERSION) {
            return MealLinkResult.TooNew(entry.schemaVersion)
        }

        return validate(entry)
    }

    /**
     * The checks worth making on a meal from anywhere at all.
     *
     * Not a guard against a hostile link — there is nothing here to attack, the
     * app only ever displays these numbers. It is a guard against a garbled
     * one, where the useful outcome is saying so rather than writing a meal of
     * 4 billion calories into the log.
     */
    fun validate(entry: MealEntry): MealLinkResult = when {
        entry.id.isBlank() ->
            MealLinkResult.Malformed("The meal has no identity, so it cannot be filed.")

        entry.label.isBlank() ->
            MealLinkResult.Malformed("The meal has no name.")

        entry.totals.calories < 0 || entry.totals.calories > MAX_PLAUSIBLE_CALORIES ->
            MealLinkResult.Malformed(
                "${entry.totals.displayCalories} kcal is not a meal — the estimate is wrong."
            )

        entry.capturedAt <= 0 ->
            MealLinkResult.Malformed("The meal is not dated.")

        else -> MealLinkResult.Ok(entry)
    }

    /**
     * Well past any real meal and well short of a number that would only come
     * from a decoding fault. The point is to catch the fault, not to police
     * lunch.
     */
    const val MAX_PLAUSIBLE_CALORIES = 20_000.0
}

sealed interface MealLinkResult {
    data class Ok(val entry: MealEntry) : MealLinkResult
    data class Malformed(val reason: String) : MealLinkResult

    /** Written by a newer skill than this build understands. Says so plainly. */
    data class TooNew(val schemaVersion: Int) : MealLinkResult {
        val reason: String
            get() = "This meal was written for a newer version of GetFit " +
                "(format $schemaVersion). Update the app to read it."
    }
}

// ---------------------------------------------------------------------------
// Base64url
// ---------------------------------------------------------------------------
//
// Written out rather than borrowed. java.util.Base64 needs API 26 and minSdk is
// 24; android.util.Base64 would do, but it is stubbed out under unit tests —
// with returnDefaultValues on it hands back null instead of failing, so the
// tests below would pass without ever encoding anything. A codec the tests
// actually exercise is worth the forty lines.

private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

fun base64UrlEncode(bytes: ByteArray): String {
    val out = StringBuilder((bytes.size + 2) / 3 * 4)
    var i = 0
    while (i + 2 < bytes.size) {
        val n = ((bytes[i].toInt() and 0xFF) shl 16) or
            ((bytes[i + 1].toInt() and 0xFF) shl 8) or
            (bytes[i + 2].toInt() and 0xFF)
        out.append(ALPHABET[(n ushr 18) and 63])
        out.append(ALPHABET[(n ushr 12) and 63])
        out.append(ALPHABET[(n ushr 6) and 63])
        out.append(ALPHABET[n and 63])
        i += 3
    }
    // The tail, unpadded: '=' would only have to be stripped again by anything
    // that put the link in a query string.
    when (bytes.size - i) {
        1 -> {
            val n = (bytes[i].toInt() and 0xFF) shl 16
            out.append(ALPHABET[(n ushr 18) and 63])
            out.append(ALPHABET[(n ushr 12) and 63])
        }

        2 -> {
            val n = ((bytes[i].toInt() and 0xFF) shl 16) or ((bytes[i + 1].toInt() and 0xFF) shl 8)
            out.append(ALPHABET[(n ushr 18) and 63])
            out.append(ALPHABET[(n ushr 12) and 63])
            out.append(ALPHABET[(n ushr 6) and 63])
        }
    }
    return out.toString()
}

/**
 * Decodes base64url, or null if [text] is not base64 at all.
 *
 * Accepts padding and the standard `+/` alphabet as well as the URL-safe one,
 * because a link that has been through a mail client may come back either way.
 */
fun base64UrlDecode(text: String): ByteArray? {
    val clean = text.trim().trimEnd('=')
    val out = ByteArrayOutputStream(clean.length * 3 / 4)
    var buffer = 0
    var bits = 0
    for (ch in clean) {
        val value = when (ch) {
            in 'A'..'Z' -> ch - 'A'
            in 'a'..'z' -> ch - 'a' + 26
            in '0'..'9' -> ch - '0' + 52
            '-', '+' -> 62
            '_', '/' -> 63
            else -> return null
        }
        buffer = (buffer shl 6) or value
        bits += 6
        if (bits >= 8) {
            bits -= 8
            out.write((buffer ushr bits) and 0xFF)
        }
    }
    return out.toByteArray()
}
