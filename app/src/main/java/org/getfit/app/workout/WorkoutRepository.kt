package org.getfit.app.workout

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import org.getfit.app.store.JsonStore

/** Everything the app knows about this person's training, in one document. */
@Serializable
data class WorkoutLibrary(
    val plans: List<WorkoutPlan> = emptyList(),
    val sessions: List<WorkoutSession> = emptyList(),
    /**
     * Set once the starter plans have been offered, so deleting them all does
     * not simply bring them back on the next launch.
     */
    val seeded: Boolean = false,
)

/**
 * Plans and sessions.
 *
 * Sessions are held newest-first and never edited once finished — see
 * [WorkoutSession]. The only mutable one is the session in progress, which is
 * the head of the list when [activeSession] is non-null.
 */
class WorkoutRepository(context: Context) {

    private val store = JsonStore(
        context = context,
        fileName = "workouts.json",
        serializer = WorkoutLibrary.serializer(),
        empty = WorkoutLibrary(),
    )

    val library: Flow<WorkoutLibrary?> = store.data

    val plans: Flow<List<WorkoutPlan>> = store.data.map { it?.plans.orEmpty() }

    val sessions: Flow<List<WorkoutSession>> = store.data.map { it?.sessions.orEmpty() }

    /** The one unfinished session, if there is one. There is never more than one. */
    val activeSession: Flow<WorkoutSession?> =
        store.data.map { lib -> lib?.sessions?.firstOrNull { it.inProgress } }

    /** Loads the document, planting the starter plans the first time only. */
    suspend fun load(): WorkoutLibrary {
        val current = store.load()
        if (current.seeded) return current
        return store.update { lib ->
            if (lib.seeded) lib
            else lib.copy(plans = lib.plans + StarterPlans.all, seeded = true)
        }
    }

    suspend fun savePlan(plan: WorkoutPlan) {
        store.update { lib ->
            val existing = lib.plans.indexOfFirst { it.id == plan.id }
            lib.copy(
                plans = if (existing >= 0) {
                    lib.plans.toMutableList().also { it[existing] = plan }
                } else {
                    lib.plans + plan
                }
            )
        }
    }

    suspend fun deletePlan(planId: String) {
        // Sessions keep their planId even once the plan is gone. It is a record
        // of what was followed at the time, and a session that silently
        // forgot which plan it came from would be worse than a dangling id.
        store.update { lib -> lib.copy(plans = lib.plans.filterNot { it.id == planId }) }
    }

    /**
     * Starts a session, from a plan or from nothing.
     *
     * Refuses if one is already running: two sessions open at once is how sets
     * end up filed against the wrong workout, and the honest answer is to
     * finish or abandon the first.
     */
    suspend fun startSession(plan: WorkoutPlan?, now: Long, id: String): WorkoutSession? {
        var started: WorkoutSession? = null
        store.update { lib ->
            if (lib.sessions.any { it.inProgress }) return@update lib
            val session = WorkoutSession(
                id = id,
                planId = plan?.id,
                name = plan?.name ?: "Ad-hoc session",
                startedAt = now,
                // Pre-filled from the plan so the sets to be done are already on
                // screen, marked incomplete until they are actually performed.
                exercises = plan?.exercises.orEmpty().map { planned ->
                    LoggedExercise(
                        exerciseId = planned.exerciseId,
                        sets = planned.sets.map { set ->
                            LoggedSet(reps = set.reps, weightKg = set.weightKg, completed = false)
                        },
                    )
                },
            )
            started = session
            lib.copy(sessions = listOf(session) + lib.sessions)
        }
        return started
    }

    /** Replaces the running session. Does nothing if [session] is not the running one. */
    suspend fun updateSession(session: WorkoutSession) {
        store.update { lib ->
            val index = lib.sessions.indexOfFirst { it.id == session.id }
            if (index < 0 || !lib.sessions[index].inProgress) return@update lib
            lib.copy(sessions = lib.sessions.toMutableList().also { it[index] = session })
        }
    }

    suspend fun finishSession(sessionId: String, now: Long) {
        store.update { lib ->
            val index = lib.sessions.indexOfFirst { it.id == sessionId }
            if (index < 0) return@update lib
            val done = lib.sessions[index].copy(finishedAt = now)
            lib.copy(sessions = lib.sessions.toMutableList().also { it[index] = done })
        }
    }

    /** Throws away a session that was started and not really done. */
    suspend fun discardSession(sessionId: String) {
        store.update { lib ->
            lib.copy(sessions = lib.sessions.filterNot { it.id == sessionId })
        }
    }
}
