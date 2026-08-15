package org.getfit.app.progress

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.getfit.app.store.JsonStore

/**
 * Weigh-ins and their photographs.
 *
 * The document holds the numbers and the file names; [photos] holds the files.
 * They are kept in step here rather than by the screens, because the failure
 * mode is asymmetric: an entry pointing at a file that is gone shows a blank
 * square, while a file no entry points at is invisible and never reclaimed.
 */
class ProgressRepository(context: Context) {

    private val store = JsonStore(
        context = context,
        fileName = "progress.json",
        serializer = ProgressLog.serializer(),
        empty = ProgressLog(),
    )

    val photos = PhotoStore(context)

    val log: Flow<ProgressLog?> = store.data

    /** Newest first, which is the order every screen wants them in. */
    val entries: Flow<List<ProgressEntry>> =
        store.data.map { it?.entries.orEmpty().sortedByDescending { entry -> entry.recordedAt } }

    suspend fun load(): ProgressLog = store.load()

    /**
     * Files a weigh-in, keeping at most [MAX_PHOTOS_PER_ENTRY] photographs.
     *
     * Anything over the cap is deleted rather than dropped, so trimming here
     * cannot leave a file behind that nothing refers to.
     */
    suspend fun add(entry: ProgressEntry) {
        val kept = entry.photos.take(MAX_PHOTOS_PER_ENTRY)
        val discarded = entry.photos.drop(MAX_PHOTOS_PER_ENTRY)
        if (discarded.isNotEmpty()) photos.delete(discarded)
        store.update { log -> log.copy(entries = log.entries + entry.copy(photos = kept)) }
    }

    /** Removes an entry and the photographs that belonged only to it. */
    suspend fun delete(entryId: String) {
        var orphaned = emptyList<String>()
        store.update { log ->
            val going = log.entries.filter { it.id == entryId }
            val staying = log.entries.filterNot { it.id == entryId }
            val stillReferenced = staying.flatMap { it.photos }.toSet()
            orphaned = going.flatMap { it.photos }.filterNot { it in stillReferenced }
            log.copy(entries = staying)
        }
        if (orphaned.isNotEmpty()) photos.delete(orphaned)
    }

    /** Every file name the log refers to, for the launch-time tidy-up. */
    suspend fun referencedPhotos(): Set<String> =
        store.load().entries.flatMapTo(mutableSetOf()) { it.photos }
}
