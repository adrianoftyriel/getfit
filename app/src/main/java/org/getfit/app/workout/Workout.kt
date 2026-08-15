package org.getfit.app.workout

import kotlinx.serialization.Serializable
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The workout model: what was planned, and what was actually done.
 *
 * The two are kept apart deliberately. A [WorkoutPlan] is a template and does
 * not change when a session goes badly; a [WorkoutSession] is a record of one
 * attempt at it and never changes again once finished. Editing history to match
 * the plan — or the plan to match history — would lose exactly the difference
 * that progress is measured in.
 */

/** One movement. Weights are kilograms throughout; display converts. */
@Serializable
data class Exercise(
    val id: String,
    val name: String,
    val group: MuscleGroup,
    val equipment: Equipment = Equipment.BARBELL,
    /**
     * Whether the load is external. A pull-up is logged in reps and the weight
     * column means added weight, so anything summing volume has to know.
     */
    val bodyweight: Boolean = false,
)

enum class MuscleGroup(val label: String) {
    CHEST("Chest"),
    BACK("Back"),
    LEGS("Legs"),
    SHOULDERS("Shoulders"),
    ARMS("Arms"),
    CORE("Core"),
    FULL_BODY("Full body"),
    CARDIO("Cardio"),
}

enum class Equipment(val label: String) {
    BARBELL("Barbell"),
    DUMBBELL("Dumbbell"),
    MACHINE("Machine"),
    CABLE("Cable"),
    BODYWEIGHT("Bodyweight"),
    KETTLEBELL("Kettlebell"),
    NONE("None"),
}

// ---------------------------------------------------------------------------
// Planning
// ---------------------------------------------------------------------------

/** A set as prescribed: how many reps at what load. */
@Serializable
data class PlannedSet(
    val reps: Int,
    val weightKg: Double = 0.0,
)

/** One exercise within a plan, and the sets called for. */
@Serializable
data class PlanExercise(
    val exerciseId: String,
    val sets: List<PlannedSet>,
    val restSeconds: Int = 90,
    val notes: String = "",
)

/** A named, reusable session template: "Push A", "Leg day". */
@Serializable
data class WorkoutPlan(
    val id: String,
    val name: String,
    val exercises: List<PlanExercise> = emptyList(),
    val notes: String = "",
) {
    val totalSets: Int get() = exercises.sumOf { it.sets.size }
}

// ---------------------------------------------------------------------------
// Tracking
// ---------------------------------------------------------------------------

/**
 * A set as performed.
 *
 * [completed] rather than deleting the row: a set that was attempted and
 * abandoned is information, and dropping it would quietly flatter the volume
 * numbers.
 */
@Serializable
data class LoggedSet(
    val reps: Int,
    val weightKg: Double = 0.0,
    val completed: Boolean = true,
    /** Reps in reserve, if recorded — how many were left in the tank. */
    val rir: Int? = null,
)

@Serializable
data class LoggedExercise(
    val exerciseId: String,
    val sets: List<LoggedSet> = emptyList(),
)

/** One attempt at a plan, or at nothing in particular. */
@Serializable
data class WorkoutSession(
    val id: String,
    /** Null for an ad-hoc session that followed no plan. */
    val planId: String? = null,
    val name: String,
    val startedAt: Long,
    val finishedAt: Long? = null,
    val exercises: List<LoggedExercise> = emptyList(),
    val notes: String = "",
) {
    val inProgress: Boolean get() = finishedAt == null

    val durationSeconds: Long?
        get() = finishedAt?.let { it - startedAt }
}

// ---------------------------------------------------------------------------
// The numbers
// ---------------------------------------------------------------------------

/**
 * Tonnage: reps times load, summed over the sets that were actually completed.
 *
 * Abandoned sets do not count — the whole reason they are kept rather than
 * deleted is so they can be excluded here honestly rather than never recorded.
 *
 * Bodyweight movements contribute only their added load, which is why an
 * exercise has to be looked up rather than inferred from the set. A pull-up at
 * zero added weight is real work and this reports it as zero tonnage; that is a
 * known limit of tonnage as a measure, not a bug to paper over by inventing a
 * bodyweight the app has not been told.
 */
fun volumeKg(exercises: List<LoggedExercise>, catalog: (String) -> Exercise?): Double =
    exercises.sumOf { logged ->
        val bodyweight = catalog(logged.exerciseId)?.bodyweight ?: false
        logged.sets
            .filter { it.completed }
            .sumOf { set ->
                if (bodyweight && set.weightKg <= 0.0) 0.0 else set.reps * set.weightKg
            }
    }

/** Completed working sets, which is the count that matters for weekly volume. */
fun completedSets(exercises: List<LoggedExercise>): Int =
    exercises.sumOf { logged -> logged.sets.count { it.completed } }

/**
 * Estimated one-rep max, by Epley.
 *
 * Only meaningful in the rep range it was fitted for — past about ten reps it
 * drifts high — so anything beyond that returns null rather than a number that
 * would be quoted as if it meant something. A single rep is its own max.
 */
fun estimatedOneRepMax(reps: Int, weightKg: Double): Double? = when {
    reps < 1 || weightKg <= 0.0 -> null
    reps == 1 -> weightKg
    reps > EPLEY_REP_LIMIT -> null
    else -> weightKg * (1 + reps / 30.0)
}

/** Past this the formula is extrapolation rather than estimation. */
const val EPLEY_REP_LIMIT = 10

/** The best estimated max across a session, for a single exercise. */
fun bestOneRepMax(sets: List<LoggedSet>): Double? =
    sets.filter { it.completed }
        .mapNotNull { estimatedOneRepMax(it.reps, it.weightKg) }
        .maxOrNull()

/**
 * The last set of a movement that was actually completed, for seeding a new one.
 *
 * An exercise added mid-session has no plan behind it and therefore no targets,
 * and starting its dial at zero would mean thumbing round three times before
 * the first set of something done every week. Last time's numbers are the
 * honest guess at this time's, and they are only a starting position — the dial
 * still has to be tapped for anything to be logged.
 *
 * [sessions] is expected newest-first, which is the order the repository keeps
 * them in; the first session with a completed set of this movement wins.
 * Abandoned sets are skipped, because a set that was not finished is not
 * evidence of what can be lifted.
 */
fun lastCompletedSet(sessions: List<WorkoutSession>, exerciseId: String): LoggedSet? =
    sessions.firstNotNullOfOrNull { session ->
        session.exercises
            .filter { it.exerciseId == exerciseId }
            .flatMap { it.sets }
            .lastOrNull { it.completed }
    }

/**
 * Rounded for display — a max estimated to three decimal places is a fiction.
 *
 * Pinned to [Locale.US] so the separator is a point wherever the phone is set.
 * Not a style choice: a gym weight written with a comma reads as a different
 * number to half the people who would see it, and it would also make this
 * function's own tests pass or fail on the runner's locale.
 */
fun Double.asDisplayWeight(): String =
    if (this % 1.0 == 0.0) "${roundToInt()}" else String.format(Locale.US, "%.1f", this)
