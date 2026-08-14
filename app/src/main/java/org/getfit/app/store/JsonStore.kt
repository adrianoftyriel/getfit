package org.getfit.app.store

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * A whole document held in one JSON file, kept in memory and written back
 * atomically.
 *
 * Chosen over a database on purpose, and the reason is size: a training log is
 * a few hundred kilobytes after years of use, so there is nothing here worth
 * querying and nothing worth an annotation processor in the build. What there
 * is worth having is being able to read the file, and to swap this for Room
 * later without any screen noticing — which is why every repository above this
 * exposes flows and suspend functions rather than the file.
 *
 * Writes go to a temporary file and are renamed over the target, so a process
 * killed mid-write leaves the previous document intact rather than a truncated
 * one. That is the failure this design would otherwise be exposed to: rewriting
 * the whole document on every change means every change is a chance to lose all
 * of it.
 */
class JsonStore<T>(
    context: Context,
    fileName: String,
    private val serializer: KSerializer<T>,
    private val empty: T,
) {
    private val file = File(context.filesDir, fileName)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    // Serialises writers against each other. Two coroutines reading the same
    // state, both editing it and both writing back would silently drop one of
    // the two edits.
    private val writeLock = Mutex()

    private val state = MutableStateFlow<T?>(null)

    /** Null until the first load has finished, so callers can tell "empty" from "not read yet". */
    val data: StateFlow<T?> = state.asStateFlow()

    /** The document, loading it from disk on first use. */
    suspend fun load(): T {
        state.value?.let { return it }
        return writeLock.withLock {
            // Checked again inside the lock: two callers can both have found
            // null above, and the second must not read the file a second time.
            state.value ?: readFromDisk().also { state.value = it }
        }
    }

    /**
     * Applies [edit] to the document and persists the result.
     *
     * The read and the write happen under one lock, so [edit] always sees the
     * document as it currently is rather than as it was when the caller decided
     * to change it.
     */
    suspend fun update(edit: (T) -> T): T = writeLock.withLock {
        val current = state.value ?: readFromDisk()
        val next = edit(current)
        state.value = next
        writeToDisk(next)
        next
    }

    private suspend fun readFromDisk(): T = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext empty
        runCatching { json.decodeFromString(serializer, file.readText()) }
            // A corrupt document is kept rather than deleted: it is the only
            // copy of whatever it holds, and a person may want it back. The app
            // carries on from empty instead of refusing to start.
            .onFailure { runCatching { file.copyTo(File("${file.path}.corrupt"), overwrite = true) } }
            .getOrDefault(empty)
    }

    private suspend fun writeToDisk(value: T) = withContext(Dispatchers.IO) {
        runCatching {
            val temp = File("${file.path}.tmp")
            temp.writeText(json.encodeToString(serializer, value))
            if (!temp.renameTo(file)) {
                // Rename can fail on some filesystems when the target exists.
                file.delete()
                temp.renameTo(file)
            }
        }
        Unit
    }
}
