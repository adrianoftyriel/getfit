package org.getfit.app.nutrition

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import org.getfit.app.store.JsonStore

/** The meal log, and the memory of what has already been taken off the inbox. */
@Serializable
data class MealLog(
    val meals: List<MealEntry> = emptyList(),
    /**
     * Every id ever imported, including meals since deleted.
     *
     * Kept apart from [meals] because the app cannot write to the inbox: an
     * imported meal stays published forever, so without this, deleting a meal
     * would mean importing it again on the next poll and no amount of deleting
     * would make it stay gone.
     */
    val importedIds: Set<String> = emptySet(),
)

/**
 * Meals, whether typed in, arrived by link, or collected from the inbox.
 *
 * The three routes converge here on purpose: a meal is a meal, and the only
 * thing that differs is [MealEntry.source].
 */
class NutritionRepository(context: Context) {

    private val store = JsonStore(
        context = context,
        fileName = "nutrition.json",
        serializer = MealLog.serializer(),
        empty = MealLog(),
    )

    val log: Flow<MealLog?> = store.data

    /** Newest first, which is the order every screen wants them in. */
    val meals: Flow<List<MealEntry>> =
        store.data.map { it?.meals.orEmpty().sortedByDescending { meal -> meal.capturedAt } }

    suspend fun load(): MealLog = store.load()

    suspend fun importedIds(): Set<String> = store.load().importedIds

    /**
     * Files a meal, remembering its id whether or not it is new.
     *
     * Returns true if the log actually gained a meal. A second call with the
     * same id is a no-op rather than a duplicate row: the inbox re-offers every
     * meal on every poll, so this is the ordinary case and not an error worth
     * reporting.
     */
    suspend fun add(entry: MealEntry): Boolean {
        var added = false
        store.update { log ->
            if (log.meals.any { it.id == entry.id }) {
                // Already held. Still record the id, in case this arrived by a
                // route that had not marked it.
                log.copy(importedIds = log.importedIds + entry.id)
            } else {
                added = true
                log.copy(meals = log.meals + entry, importedIds = log.importedIds + entry.id)
            }
        }
        return added
    }

    /** Files a batch from the inbox and reports how many were genuinely new. */
    suspend fun addAll(entries: List<MealEntry>): Int {
        var added = 0
        store.update { log ->
            val held = log.meals.mapTo(mutableSetOf()) { it.id }
            val fresh = entries.filter { it.id !in held }
            added = fresh.size
            log.copy(
                meals = log.meals + fresh,
                importedIds = log.importedIds + entries.map { it.id },
            )
        }
        return added
    }

    /**
     * Removes a meal from the log while keeping its id in [MealLog.importedIds],
     * so the inbox does not simply hand it back.
     */
    suspend fun delete(mealId: String) {
        store.update { log -> log.copy(meals = log.meals.filterNot { it.id == mealId }) }
    }
}
