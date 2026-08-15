package org.getfit.app.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The numbers a training log is read for.
 *
 * Volume and estimated maxes are what tell somebody whether the last two months
 * did anything, so the ways they can quietly come out wrong — counting sets
 * that were abandoned, counting bodyweight as zero load in one place and not
 * another, quoting a formula outside the range it was fitted for — are what is
 * pinned here.
 */
class WorkoutMathTest {

    private val catalog: (String) -> Exercise? = { ExerciseCatalog.byId(it) }

    private fun logged(exerciseId: String, vararg sets: LoggedSet) =
        LoggedExercise(exerciseId, sets.toList())

    // -- Volume -------------------------------------------------------------

    @Test
    fun `volume is reps times load, summed`() {
        val session = listOf(
            logged(
                "back-squat",
                LoggedSet(reps = 5, weightKg = 100.0),
                LoggedSet(reps = 5, weightKg = 100.0),
            )
        )
        assertEquals(1000.0, volumeKg(session, catalog), 0.001)
    }

    @Test
    fun `an abandoned set does not count towards volume`() {
        val session = listOf(
            logged(
                "back-squat",
                LoggedSet(reps = 5, weightKg = 100.0),
                LoggedSet(reps = 5, weightKg = 100.0, completed = false),
            )
        )
        assertEquals(
            "the set that was not finished must not flatter the total",
            500.0,
            volumeKg(session, catalog),
            0.001,
        )
    }

    @Test
    fun `an abandoned set is still counted as a set that happened`() {
        // Kept rather than deleted, which is the whole reason it can be
        // excluded from volume honestly.
        val session = listOf(
            logged(
                "back-squat",
                LoggedSet(reps = 5, weightKg = 100.0),
                LoggedSet(reps = 2, weightKg = 100.0, completed = false),
            )
        )
        assertEquals(1, completedSets(session))
        assertEquals(2, session.single().sets.size)
    }

    @Test
    fun `an unloaded bodyweight movement contributes no tonnage`() {
        // A known limit of tonnage as a measure, and one this reports rather
        // than papers over by inventing a bodyweight it has not been told.
        val session = listOf(logged("pull-up", LoggedSet(reps = 8)))
        assertEquals(0.0, volumeKg(session, catalog), 0.001)
        assertEquals("but the set still happened", 1, completedSets(session))
    }

    @Test
    fun `added weight on a bodyweight movement does count`() {
        val session = listOf(logged("pull-up", LoggedSet(reps = 5, weightKg = 20.0)))
        assertEquals(100.0, volumeKg(session, catalog), 0.001)
    }

    @Test
    fun `an exercise the catalogue does not know is treated as loaded`() {
        // The safer default: a custom barbell movement counting for nothing
        // would be a silent hole in the total, where a bodyweight one counting
        // its load is visible in the number.
        val session = listOf(logged("custom-lift", LoggedSet(reps = 3, weightKg = 60.0)))
        assertEquals(180.0, volumeKg(session) { null }, 0.001)
    }

    @Test
    fun `an empty session is zero rather than an error`() {
        assertEquals(0.0, volumeKg(emptyList(), catalog), 0.001)
        assertEquals(0, completedSets(emptyList()))
    }

    // -- Estimated one-rep max ----------------------------------------------

    @Test
    fun `a single rep is its own max`() {
        assertEquals(140.0, estimatedOneRepMax(1, 140.0)!!, 0.001)
    }

    @Test
    fun `Epley is applied in the range it was fitted for`() {
        // 100 kg for 5 → 100 × (1 + 5/30) = 116.67
        assertEquals(116.667, estimatedOneRepMax(5, 100.0)!!, 0.01)
    }

    @Test
    fun `past ten reps it declines to guess`() {
        assertNotNull(estimatedOneRepMax(EPLEY_REP_LIMIT, 80.0))
        assertNull(
            "beyond the fitted range the formula drifts high and must not be quoted",
            estimatedOneRepMax(EPLEY_REP_LIMIT + 1, 80.0),
        )
    }

    @Test
    fun `nonsense in gives nothing out`() {
        assertNull(estimatedOneRepMax(0, 100.0))
        assertNull(estimatedOneRepMax(-1, 100.0))
        assertNull("an unloaded set has no max to estimate", estimatedOneRepMax(5, 0.0))
    }

    @Test
    fun `the best max across a session is the highest estimate in it`() {
        val sets = listOf(
            LoggedSet(reps = 8, weightKg = 90.0), // 114.0
            LoggedSet(reps = 3, weightKg = 110.0), // 121.0
            LoggedSet(reps = 1, weightKg = 118.0), // 118.0
        )
        assertEquals(121.0, bestOneRepMax(sets)!!, 0.01)
    }

    @Test
    fun `an abandoned set cannot set a personal best`() {
        val sets = listOf(
            LoggedSet(reps = 5, weightKg = 100.0),
            LoggedSet(reps = 1, weightKg = 200.0, completed = false),
        )
        assertEquals(116.667, bestOneRepMax(sets)!!, 0.01)
    }

    @Test
    fun `a session with nothing estimable has no best`() {
        assertNull(bestOneRepMax(listOf(LoggedSet(reps = 20, weightKg = 40.0))))
        assertNull(bestOneRepMax(emptyList()))
    }

    // -- Cardio is not counted in reps ----------------------------------------

    @Test
    fun `every movement in the cardio group is counted in minutes, not reps`() {
        // The two are separate ideas — one says what a movement trains, the
        // other how it is counted — and this is what notices if a cardio
        // machine is ever added without saying how to log it.
        ExerciseCatalog.inGroup(MuscleGroup.CARDIO).forEach { exercise ->
            assertEquals(
                "${exercise.name} would be logged in reps",
                Measure.CARDIO,
                exercise.measure,
            )
        }
        assertTrue(ExerciseCatalog.inGroup(MuscleGroup.CARDIO).isNotEmpty())
    }

    @Test
    fun `a lift is still counted in reps`() {
        assertEquals(Measure.LIFT, ExerciseCatalog.byId("back-squat")!!.measure)
        assertEquals("a bodyweight lift is still a lift", Measure.LIFT, ExerciseCatalog.byId("pull-up")!!.measure)
    }

    @Test
    fun `cardio contributes no tonnage, whatever is in its rep field`() {
        // The guard is the measure and not the load: a cardio set carrying a
        // stray rep count from anywhere must not be multiplied into a total.
        val session = listOf(
            logged("treadmill", LoggedSet(reps = 30, weightKg = 80.0, seconds = 1800, metres = 5000))
        )
        assertEquals(0.0, volumeKg(session, catalog), 0.001)
    }

    @Test
    fun `cardio time and distance are totalled apart from tonnage`() {
        val session = listOf(
            logged("back-squat", LoggedSet(reps = 5, weightKg = 100.0)),
            logged(
                "treadmill",
                LoggedSet(reps = 0, seconds = 1200, metres = 4000),
                LoggedSet(reps = 0, seconds = 600, metres = 2000),
            ),
        )
        assertEquals("the squats are still tonnage", 500.0, volumeKg(session, catalog), 0.001)
        assertEquals(1800, cardioSeconds(session, catalog))
        assertEquals(6000, cardioMetres(session, catalog))
    }

    @Test
    fun `an abandoned interval counts for no time and no distance`() {
        val session = listOf(
            logged(
                "cycling",
                LoggedSet(reps = 0, seconds = 1200, metres = 8000),
                LoggedSet(reps = 0, seconds = 300, metres = 2000, completed = false),
            )
        )
        assertEquals(1200, cardioSeconds(session, catalog))
        assertEquals(8000, cardioMetres(session, catalog))
    }

    @Test
    fun `a lift contributes no cardio time, even if something wrote seconds on it`() {
        val session = listOf(logged("bench-press", LoggedSet(reps = 5, weightKg = 60.0, seconds = 90)))
        assertEquals(0, cardioSeconds(session, catalog))
        assertEquals(0, cardioMetres(session, catalog))
    }

    @Test
    fun `cardio has no one-rep max to estimate`() {
        // Falls out of the load being zero rather than being special-cased,
        // but it is the answer that matters: there is no such thing as a
        // one-rep max on a rowing machine.
        assertNull(bestOneRepMax(listOf(LoggedSet(reps = 0, seconds = 1200, metres = 5000))))
    }

    @Test
    fun `a session of nothing but cardio still counts its sets`() {
        val session = listOf(logged("row-erg", LoggedSet(reps = 0, seconds = 1200, metres = 5000)))
        assertEquals(1, completedSets(session))
        assertEquals(0.0, volumeKg(session, catalog), 0.001)
    }

    // -- Seeding a movement added mid-session ---------------------------------

    private fun session(id: String, vararg exercises: LoggedExercise) =
        WorkoutSession(id = id, name = id, startedAt = 0, exercises = exercises.toList())

    @Test
    fun `an added movement is seeded from the last time it was done`() {
        val history = listOf(
            session("today", logged("bench-press", LoggedSet(reps = 5, weightKg = 80.0))),
            session("last week", logged("bench-press", LoggedSet(reps = 5, weightKg = 75.0))),
        )
        assertEquals(80.0, lastCompletedSet(history, "bench-press")!!.weightKg, 0.001)
    }

    @Test
    fun `the last set of the session is the one that counts, not the first`() {
        // Working up to a top set is the normal shape of an exercise, and the
        // number worth opening the dial on is where it finished.
        val history = listOf(
            session(
                "today",
                logged(
                    "back-squat",
                    LoggedSet(reps = 5, weightKg = 60.0),
                    LoggedSet(reps = 5, weightKg = 100.0),
                ),
            )
        )
        assertEquals(100.0, lastCompletedSet(history, "back-squat")!!.weightKg, 0.001)
    }

    @Test
    fun `a set that was abandoned is not evidence of what can be lifted`() {
        val history = listOf(
            session(
                "today",
                logged(
                    "deadlift",
                    LoggedSet(reps = 5, weightKg = 140.0),
                    LoggedSet(reps = 1, weightKg = 200.0, completed = false),
                ),
            )
        )
        assertEquals(140.0, lastCompletedSet(history, "deadlift")!!.weightKg, 0.001)
    }

    @Test
    fun `a session with nothing completed falls through to an older one`() {
        val history = listOf(
            session("today", logged("pull-up", LoggedSet(reps = 3, completed = false))),
            session("last week", logged("pull-up", LoggedSet(reps = 8, weightKg = 10.0))),
        )
        val seed = lastCompletedSet(history, "pull-up")!!
        assertEquals(8, seed.reps)
        assertEquals(10.0, seed.weightKg, 0.001)
    }

    @Test
    fun `a movement never done before has nothing to seed from`() {
        val history = listOf(session("today", logged("bench-press", LoggedSet(reps = 5))))
        assertNull(
            "the caller falls back to a default rather than being handed a guess",
            lastCompletedSet(history, "lateral-raise"),
        )
        assertNull(lastCompletedSet(emptyList(), "bench-press"))
    }

    // -- Display ------------------------------------------------------------

    @Test
    fun `weights lose their fictional precision on the way to the screen`() {
        assertEquals("100", 100.0.asDisplayWeight())
        assertEquals("116.7", 116.66666.asDisplayWeight())
        assertEquals("62.5", 62.5.asDisplayWeight())
    }

    // -- The catalogue ------------------------------------------------------

    @Test
    fun `every exercise has a distinct id`() {
        val ids = ExerciseCatalog.all.map { it.id }
        assertEquals("duplicate ids would make lookup pick one arbitrarily", ids.size, ids.toSet().size)
    }

    @Test
    fun `every exercise a starter plan names actually exists`() {
        StarterPlans.all.forEach { plan ->
            plan.exercises.forEach { planned ->
                assertNotNull(
                    "${plan.name} references unknown exercise ${planned.exerciseId}",
                    ExerciseCatalog.byId(planned.exerciseId),
                )
            }
        }
    }

    @Test
    fun `a plan knows how many sets it asks for`() {
        assertEquals(
            StarterPlans.fullBody.exercises.sumOf { it.sets.size },
            StarterPlans.fullBody.totalSets,
        )
        assertTrue(StarterPlans.fullBody.totalSets > 0)
    }
}
