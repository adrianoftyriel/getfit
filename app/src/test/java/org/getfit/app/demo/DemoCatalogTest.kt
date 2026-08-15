package org.getfit.app.demo

import org.getfit.app.workout.ExerciseCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reading demonstrations out of the published dataset.
 *
 * This is the one part of the app that shows somebody how to move a loaded
 * barbell, so the rules it must not break are: never show a movement that is
 * not the one asked for, and never show anything without saying where it came
 * from.
 */
class DemoCatalogTest {

    /**
     * Shaped exactly like the real file, including the parts that vary: a null
     * `equipment`, an unknown field, and an entry we did not ask for.
     */
    private val sample = """
        [
          {
            "id": "Barbell_Squat",
            "name": "Barbell Squat",
            "force": "push",
            "level": "beginner",
            "mechanic": "compound",
            "equipment": "barbell",
            "primaryMuscles": ["quadriceps"],
            "secondaryMuscles": ["glutes", "hamstrings"],
            "instructions": ["Begin with the barbell on your traps.", "  Squat.  "],
            "category": "strength",
            "images": ["Barbell_Squat/0.jpg", "Barbell_Squat/1.jpg"]
          },
          {
            "id": "Plank",
            "name": "Plank",
            "force": null,
            "level": "beginner",
            "mechanic": null,
            "equipment": null,
            "primaryMuscles": ["abdominals"],
            "secondaryMuscles": [],
            "instructions": ["Hold the position."],
            "category": "strength",
            "images": ["Plank/0.jpg", "Plank/1.jpg"]
          },
          {
            "id": "Some_Other_Lift",
            "name": "Some Other Lift",
            "level": "expert",
            "equipment": "barbell",
            "primaryMuscles": [],
            "secondaryMuscles": [],
            "instructions": ["Not one of ours."],
            "category": "strength",
            "images": ["Some_Other_Lift/0.jpg"]
          },
          {
            "id": "No_Pictures",
            "name": "No Pictures",
            "level": "beginner",
            "equipment": null,
            "primaryMuscles": [],
            "secondaryMuscles": [],
            "instructions": ["Described but never photographed."],
            "category": "strength",
            "images": []
          }
        ]
    """.trimIndent()

    private val wanted = setOf("Barbell_Squat", "Plank", "No_Pictures")

    // -- Reading the dataset -------------------------------------------------

    @Test
    fun `only the exercises asked for are kept`() {
        val demos = parseDemos(sample, wanted)
        assertFalse(
            "an entry nobody asked for was kept",
            demos.containsKey("Some_Other_Lift"),
        )
        assertTrue(demos.containsKey("Barbell_Squat"))
    }

    @Test
    fun `an entry with no images is dropped rather than shown as text`() {
        // The screen exists to show the movement. Instructions alone are what
        // it already falls back to.
        assertNull(parseDemos(sample, wanted)["No_Pictures"])
    }

    @Test
    fun `a null equipment does not lose the entry`() {
        // 77 of the 873 real entries have this, so parsing it as a hard failure
        // would silently cost a tenth of the dataset.
        val plank = parseDemos(sample, wanted)["Plank"]!!
        assertEquals("", plank.equipment)
        assertEquals("Plank", plank.name)
    }

    @Test
    fun `frame paths become full urls`() {
        val squat = parseDemos(sample, wanted)["Barbell_Squat"]!!
        assertEquals(2, squat.frameUrls.size)
        assertTrue(
            "frame url should be absolute: ${squat.frameUrls[0]}",
            squat.frameUrls[0].startsWith("https://"),
        )
        assertTrue(squat.frameUrls[0].endsWith("/exercises/Barbell_Squat/0.jpg"))
    }

    @Test
    fun `instructions are trimmed but not otherwise touched`() {
        val squat = parseDemos(sample, wanted)["Barbell_Squat"]!!
        assertEquals(
            listOf("Begin with the barbell on your traps.", "Squat."),
            squat.instructions,
        )
    }

    @Test
    fun `two frames can be animated and one cannot`() {
        val squat = parseDemos(sample, wanted)["Barbell_Squat"]!!
        assertTrue(squat.animatable)
        assertFalse(squat.copy(frameUrls = squat.frameUrls.take(1)).animatable)
    }

    // -- Failing safely ------------------------------------------------------

    @Test
    fun `a truncated or invalid file yields nothing rather than throwing`() {
        assertTrue(parseDemos(sample.take(sample.length / 2), wanted).isEmpty())
        assertTrue(parseDemos("", wanted).isEmpty())
        assertTrue(parseDemos("{\"not\": \"a list\"}", wanted).isEmpty())
    }

    @Test
    fun `asking for nothing returns nothing`() {
        assertTrue(parseDemos(sample, emptySet()).isEmpty())
    }

    // -- Attribution ---------------------------------------------------------

    /**
     * The app must never show form guidance without saying where it came from.
     * [ExerciseDemo] makes attribution a constructor parameter so it cannot be
     * left out; this checks the parser fills it in rather than leaving a blank
     * that would satisfy the type and tell the reader nothing.
     */
    @Test
    fun `every parsed demonstration carries its source`() {
        parseDemos(sample, wanted).values.forEach { demo ->
            assertEquals(DemoSource.FREE_EXERCISE_DB, demo.attribution)
            assertTrue(demo.attribution.sourceName.isNotBlank())
            assertTrue(demo.attribution.sourceUrl.startsWith("https://"))
            assertTrue(demo.attribution.licence.isNotBlank())
            assertTrue(demo.attribution.provenance.isNotBlank())
        }
    }

    /**
     * The description shown to the reader has to admit what the source is not.
     * If this ever reads as an endorsement, somebody has quietly upgraded a
     * community dataset into medical advice.
     */
    @Test
    fun `the stated provenance does not claim clinical review`() {
        val provenance = DemoSource.FREE_EXERCISE_DB.provenance.lowercase()
        assertTrue(
            "provenance should say it is not clinically reviewed: $provenance",
            provenance.contains("not clinically reviewed"),
        )
    }

    // -- The mapping ---------------------------------------------------------

    @Test
    fun `every mapped exercise exists in our own catalogue`() {
        DemoCatalog.sourceIds.keys.forEach { ourId ->
            assertTrue(
                "$ourId is mapped to a demonstration but is not an exercise",
                ExerciseCatalog.byId(ourId) != null,
            )
        }
    }

    @Test
    fun `every exercise we ship has a demonstration mapped`() {
        // Not a rule the app depends on — a missing mapping shows "no
        // demonstration" and nothing breaks — but a new exercise added without
        // one is nearly always an oversight, and this is where it gets noticed.
        ExerciseCatalog.all.forEach { exercise ->
            assertTrue(
                "${exercise.id} has no demonstration mapped",
                DemoCatalog.sourceIdFor(exercise.id) != null,
            )
        }
    }

    @Test
    fun `no two exercises point at the same demonstration`() {
        // Two of ours resolving to one entry means one of them is showing the
        // wrong movement.
        val ids = DemoCatalog.sourceIds.values
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `an unmapped exercise resolves to nothing`() {
        assertNull(DemoCatalog.sourceIdFor("not-an-exercise"))
    }
}
