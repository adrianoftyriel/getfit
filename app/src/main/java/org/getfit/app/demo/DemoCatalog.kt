package org.getfit.app.demo

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Which entry in the upstream dataset demonstrates each of our exercises.
 *
 * Written out by hand and checked one at a time against the real dataset rather
 * than derived from the names, because a fuzzy match is how "Dips" becomes
 * "Jerk Dip Squat" and somebody is shown the wrong movement. An exercise
 * missing from this map has no demonstration, which is the correct outcome:
 * better nothing than a confident picture of something else.
 */
object DemoCatalog {

    /** Our exercise id → the dataset's id. */
    val sourceIds: Map<String, String> = mapOf(
        // Chest
        "bench-press" to "Barbell_Bench_Press_-_Medium_Grip",
        "incline-db-press" to "Incline_Dumbbell_Press",
        "push-up" to "Pushups",
        "cable-fly" to "Cable_Crossover",

        // Back
        "deadlift" to "Barbell_Deadlift",
        "barbell-row" to "Bent_Over_Barbell_Row",
        "pull-up" to "Pullups",
        "lat-pulldown" to "Wide-Grip_Lat_Pulldown",
        "seated-row" to "Seated_Cable_Rows",

        // Legs
        "back-squat" to "Barbell_Squat",
        "front-squat" to "Front_Barbell_Squat",
        "romanian-deadlift" to "Romanian_Deadlift",
        "leg-press" to "Leg_Press",
        "walking-lunge" to "Dumbbell_Lunges",
        "calf-raise" to "Standing_Calf_Raises",

        // Shoulders
        "overhead-press" to "Standing_Military_Press",
        "lateral-raise" to "Side_Lateral_Raise",
        "face-pull" to "Face_Pull",

        // Arms
        "barbell-curl" to "Barbell_Curl",
        "hammer-curl" to "Hammer_Curls",
        "triceps-pushdown" to "Triceps_Pushdown",
        // The parallel-bar version, not Bench_Dips and not Jerk_Dip_Squat.
        "dip" to "Parallel_Bar_Dip",

        // Core
        "plank" to "Plank",
        "hanging-leg-raise" to "Hanging_Leg_Raise",
        "cable-crunch" to "Cable_Crunch",

        // Cardio
        "row-erg" to "Rowing_Stationary",
        "treadmill" to "Running_Treadmill",
        "cycling" to "Bicycling_Stationary",
    )

    fun sourceIdFor(exerciseId: String): String? = sourceIds[exerciseId]

    val wantedSourceIds: Set<String> = sourceIds.values.toSet()
}

// ---------------------------------------------------------------------------
// Parsing
// ---------------------------------------------------------------------------

/**
 * The shape of one entry as the dataset publishes it.
 *
 * `equipment`, `force` and `mechanic` are genuinely null for some entries — 77,
 * 29 and 87 of the 873 — so they are nullable here rather than defaulted, and
 * only the ones we display are read at all.
 */
@Serializable
private data class RawExercise(
    val id: String,
    val name: String,
    val instructions: List<String> = emptyList(),
    val images: List<String> = emptyList(),
    val primaryMuscles: List<String> = emptyList(),
    val secondaryMuscles: List<String> = emptyList(),
    val level: String = "",
    val equipment: String? = null,
    @SerialName("category") val category: String = "",
)

private val json = Json { ignoreUnknownKeys = true }

/**
 * Picks the wanted entries out of the published dataset.
 *
 * Kept pure and separate from the download so the awkward parts — an entry with
 * no images, an id that has been renamed upstream, a truncated file — can be
 * tested without a network.
 *
 * An entry with no frames is dropped rather than kept as a text-only
 * demonstration: this exists to show the movement, and instructions alone are
 * what the screen already falls back to.
 */
fun parseDemos(body: String, wanted: Set<String>): Map<String, ExerciseDemo> {
    val all = runCatching { json.decodeFromString<List<RawExercise>>(body) }.getOrNull()
        ?: return emptyMap()

    return all.asSequence()
        .filter { it.id in wanted }
        .filter { it.images.isNotEmpty() }
        .map { raw ->
            raw.id to ExerciseDemo(
                sourceId = raw.id,
                name = raw.name,
                instructions = raw.instructions.map { it.trim() }.filter { it.isNotBlank() },
                frameUrls = raw.images.map { DemoSource.frameUrl(it) },
                primaryMuscles = raw.primaryMuscles,
                secondaryMuscles = raw.secondaryMuscles,
                level = raw.level,
                equipment = raw.equipment.orEmpty(),
                attribution = DemoSource.FREE_EXERCISE_DB,
            )
        }
        .toMap()
}

/** The subset we keep on disk, so a 1 MB download becomes a few kilobytes. */
@Serializable
data class DemoCache(
    val demos: Map<String, ExerciseDemo> = emptyMap(),
    /** When the index was last fetched, epoch seconds. */
    val fetchedAt: Long = 0,
)
