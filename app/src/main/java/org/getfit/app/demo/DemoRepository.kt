package org.getfit.app.demo

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.getfit.app.store.JsonStore
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Demonstrations, fetched once and then kept.
 *
 * Caching is not an optimisation here. This gets opened in a gym, which is
 * frequently a basement with no signal, and a demonstration that only works on
 * wifi is one that does not work when it is needed. So the index is trimmed to
 * the exercises the app actually knows and stored, and every frame is written
 * to disk the first time it is seen.
 *
 * Nothing is prefetched. Downloading 56 photographs on first launch to serve
 * the two somebody will look at is a poor trade on a phone plan.
 */
class DemoRepository(private val context: Context) {

    private val store = JsonStore(
        context = context,
        fileName = "demos.json",
        serializer = DemoCache.serializer(),
        empty = DemoCache(),
    )

    private val frameDir = File(context.filesDir, "demo-frames")

    // One download at a time. Opening two exercises quickly would otherwise
    // fetch and parse the whole 1 MB index twice.
    private val indexLock = Mutex()

    /**
     * The demonstration for one of our exercises, or null if there is none.
     *
     * Null is a real answer and the screen says so plainly. It covers an
     * exercise absent from [DemoCatalog], an id renamed upstream, and a first
     * lookup made with no network — none of which are worth inventing a
     * movement over.
     */
    suspend fun demoFor(exerciseId: String): ExerciseDemo? {
        val sourceId = DemoCatalog.sourceIdFor(exerciseId) ?: return null

        store.load().demos[sourceId]?.let { return it }

        return indexLock.withLock {
            // Another caller may have fetched it while this one waited.
            store.load().demos[sourceId] ?: run {
                val fetched = fetchIndex() ?: return@run null
                store.update { DemoCache(demos = fetched, fetchedAt = nowSeconds()) }
                fetched[sourceId]
            }
        }
    }

    /** True once the index has been fetched, which is what the settings screen reports. */
    suspend fun isCached(): Boolean = store.load().demos.isNotEmpty()

    suspend fun cachedCount(): Int = store.load().demos.size

    /**
     * Fetches every frame of [demo] so it can be viewed without a signal later.
     *
     * Returns how many are now on disk, which is what lets the UI say "ready
     * offline" rather than leaving somebody to find out in the basement.
     */
    suspend fun cacheFrames(demo: ExerciseDemo): Int = withContext(Dispatchers.IO) {
        demo.frameUrls.count { frameFile(it).exists() || download(it) != null }
    }

    /**
     * One frame, decoded, from disk if it is there and from the network if not.
     *
     * Returns null rather than a placeholder: a frame that failed to load is
     * the caller's problem to describe, and a stand-in image in a form
     * demonstration would be actively misleading.
     */
    suspend fun frame(url: String): ImageBitmap? = withContext(Dispatchers.IO) {
        val bytes = frameFile(url).takeIf { it.exists() }?.readBytes() ?: download(url)
        bytes ?: return@withContext null
        runCatching {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }.getOrNull()
    }

    // -- The network ---------------------------------------------------------

    private fun fetchIndex(): Map<String, ExerciseDemo>? {
        val body = getText(DemoSource.INDEX_URL) ?: return null
        return parseDemos(body, DemoCatalog.wantedSourceIds).takeIf { it.isNotEmpty() }
    }

    private fun download(url: String): ByteArray? {
        val bytes = getBytes(url) ?: return null
        // Written through a temp file and renamed, so a download cut off
        // halfway cannot leave a half-image that every later read trusts.
        runCatching {
            frameDir.mkdirs()
            val target = frameFile(url)
            val temp = File("${target.path}.tmp")
            temp.writeBytes(bytes)
            if (!temp.renameTo(target)) {
                target.delete()
                temp.renameTo(target)
            }
        }
        return bytes
    }

    /** Named by a hash of the URL, so a path from the dataset cannot escape the cache dir. */
    private fun frameFile(url: String): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(url.toByteArray())
        return File(frameDir, digest.joinToString("") { "%02x".format(it) } + ".jpg")
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("User-Agent", "GetFit-Demos")
            connectTimeout = 20_000
            readTimeout = 20_000
        }

    private fun getText(url: String): String? = runCatching {
        val connection = open(url)
        try {
            if (connection.responseCode != 200) return null
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }.getOrNull()

    private fun getBytes(url: String): ByteArray? = runCatching {
        val connection = open(url)
        try {
            if (connection.responseCode != 200) return null
            // Capped: a frame is a photograph, and anything far larger is not
            // one. Without this a wrong URL could pull an arbitrary file into
            // memory on a phone.
            val bytes = connection.inputStream.use { it.readBytes() }
            if (bytes.size > MAX_FRAME_BYTES) null else bytes
        } finally {
            connection.disconnect()
        }
    }.getOrNull()

    private fun nowSeconds(): Long = System.currentTimeMillis() / 1000

    private companion object {
        const val MAX_FRAME_BYTES = 8 * 1024 * 1024
    }
}
