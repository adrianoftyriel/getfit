package org.getfit.app.progress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Choosing which two photographs to put beside each other.
 *
 * This is the part of the feature that can be wrong without looking wrong. A
 * comparison drawn across too short a span shows lighting and water weight as
 * if they were progress; one that never changes stops being looked at; and an
 * index taken carelessly off the clock is an out-of-bounds waiting for whoever
 * has the wrong number of entries on the wrong day.
 */
class ProgressTest {

    private fun entry(
        id: String,
        daysAgo: Int,
        weightKg: Double? = null,
        photos: List<String> = listOf("$id.jpg"),
    ) = ProgressEntry(
        id = id,
        recordedAt = NOW - daysAgo * SECONDS_PER_DAY,
        weightKg = weightKg,
        photos = photos,
    )

    // -- When there is nothing worth showing ----------------------------------

    @Test
    fun `nothing to compare against yet`() {
        assertNull(progressComparison(emptyList(), NOW))
        assertNull(
            "one photograph is not a before and after",
            progressComparison(listOf(entry("a", daysAgo = 60)), NOW),
        )
    }

    @Test
    fun `entries without photographs are not a comparison`() {
        val entries = listOf(
            entry("a", daysAgo = 90, weightKg = 90.0, photos = emptyList()),
            entry("b", daysAgo = 0, weightKg = 84.0, photos = emptyList()),
        )
        assertNull(progressComparison(entries, NOW))
    }

    @Test
    fun `two photographs taken the same week are not progress`() {
        // Below a fortnight the difference is lighting, posture and water.
        // Showing it as a result teaches somebody to distrust the whole view.
        val entries = listOf(entry("a", daysAgo = 6), entry("b", daysAgo = 0))
        assertNull(progressComparison(entries, NOW))
    }

    @Test
    fun `exactly the minimum span does count`() {
        val entries = listOf(
            entry("a", daysAgo = MIN_COMPARISON_DAYS),
            entry("b", daysAgo = 0),
        )
        val comparison = progressComparison(entries, NOW)
        assertNotNull(comparison)
        assertEquals(MIN_COMPARISON_DAYS, comparison!!.days)
    }

    // -- Which pair gets shown -------------------------------------------------

    @Test
    fun `the after is always the most recent photograph`() {
        val entries = listOf(
            entry("oldest", daysAgo = 120),
            entry("middle", daysAgo = 60),
            entry("newest", daysAgo = 1),
        )
        assertEquals("newest", progressComparison(entries, NOW)!!.after.id)
    }

    @Test
    fun `a photograph too close to the newest is never used as the before`() {
        val entries = listOf(
            entry("old", daysAgo = 90),
            entry("yesterday", daysAgo = 1),
            entry("today", daysAgo = 0),
        )
        // Over any day, the only eligible before is the ninety-day-old one.
        repeat(30) { day ->
            val comparison = progressComparison(entries, NOW + day * SECONDS_PER_DAY)!!
            assertEquals("old", comparison.before.id)
        }
    }

    @Test
    fun `the before rotates by the day rather than becoming wallpaper`() {
        val entries = listOf(
            entry("a", daysAgo = 200),
            entry("b", daysAgo = 150),
            entry("c", daysAgo = 100),
            entry("now", daysAgo = 0),
        )
        val shown = (0 until 6).map { day ->
            progressComparison(entries, NOW + day * SECONDS_PER_DAY)!!.before.id
        }
        assertTrue(
            "every eligible before should come up: $shown",
            shown.toSet() == setOf("a", "b", "c"),
        )
        assertEquals("and it should be stable within a day", shown[0], shown[0])
    }

    @Test
    fun `a clock before the epoch does not index backwards off the list`() {
        // Nobody's phone should report this, and an index of -2 would take the
        // screen down with it if one did.
        val entries = listOf(
            ProgressEntry("a", recordedAt = -400 * SECONDS_PER_DAY, photos = listOf("a.jpg")),
            ProgressEntry("b", recordedAt = -300 * SECONDS_PER_DAY, photos = listOf("b.jpg")),
            ProgressEntry("c", recordedAt = -100 * SECONDS_PER_DAY, photos = listOf("c.jpg")),
        )
        val comparison = progressComparison(entries, now = -100 * SECONDS_PER_DAY)
        assertNotNull(comparison)
        assertEquals("c", comparison!!.after.id)
    }

    // -- The arithmetic between them -------------------------------------------

    @Test
    fun `the span is counted in whole days`() {
        val comparison = progressComparison(
            listOf(entry("a", daysAgo = 84), entry("b", daysAgo = 0)),
            NOW,
        )!!
        assertEquals(84, comparison.days)
    }

    @Test
    fun `a weight change needs a weight at both ends`() {
        val weighed = Comparison(
            before = entry("a", daysAgo = 84, weightKg = 90.0),
            after = entry("b", daysAgo = 0, weightKg = 85.5),
        )
        assertEquals(-4.5, weighed.weightDeltaKg!!, 0.001)

        val halfWeighed = Comparison(
            before = entry("a", daysAgo = 84),
            after = entry("b", daysAgo = 0, weightKg = 85.5),
        )
        assertNull(
            "a change from an unknown weight is not a number anybody should see",
            halfWeighed.weightDeltaKg,
        )
    }

    @Test
    fun `gaining reads as a gain rather than as a failure`() {
        // The app does not know whether somebody is cutting or gaining, so the
        // delta carries its sign and no opinion.
        val comparison = Comparison(
            before = entry("a", daysAgo = 84, weightKg = 70.0),
            after = entry("b", daysAgo = 0, weightKg = 74.0),
        )
        assertEquals(4.0, comparison.weightDeltaKg!!, 0.001)
    }

    // -- The list and the line -------------------------------------------------

    @Test
    fun `the latest weight ignores entries that were photographs only`() {
        val entries = listOf(
            entry("weighed", daysAgo = 3, weightKg = 82.0),
            entry("photos only", daysAgo = 0),
        )
        assertEquals(82.0, latestWeightKg(entries)!!, 0.001)
        assertNull(latestWeightKg(listOf(entry("a", daysAgo = 0))))
    }

    @Test
    fun `the sparkline is shaped to the range it actually covers`() {
        // Bodyweight moves by a percent or two. Drawn from zero it would be a
        // flat line every time, which is the one thing it must not be.
        val entries = listOf(
            entry("a", daysAgo = 30, weightKg = 80.0),
            entry("b", daysAgo = 20, weightKg = 82.0),
            entry("c", daysAgo = 10, weightKg = 84.0),
        )
        assertEquals(listOf(0f, 0.5f, 1f), weightSparkline(entries))
    }

    @Test
    fun `an unchanged weight sits down the middle rather than dividing by nothing`() {
        val entries = listOf(
            entry("a", daysAgo = 20, weightKg = 80.0),
            entry("b", daysAgo = 0, weightKg = 80.0),
        )
        assertEquals(listOf(0.5f, 0.5f), weightSparkline(entries))
    }

    @Test
    fun `one point is not a line`() {
        assertEquals(emptyList<Float>(), weightSparkline(listOf(entry("a", daysAgo = 0, weightKg = 80.0))))
        assertEquals(emptyList<Float>(), weightSparkline(emptyList()))
    }

    @Test
    fun `the line is drawn oldest first however the entries arrive`() {
        val entries = listOf(
            entry("newest", daysAgo = 0, weightKg = 84.0),
            entry("oldest", daysAgo = 30, weightKg = 80.0),
        )
        assertEquals(listOf(0f, 1f), weightSparkline(entries))
    }

    // -- Decoding a photograph without holding all of it -----------------------

    @Test
    fun `a photograph is shrunk by powers of two until it fits`() {
        // Only powers of two, so the result undershoots rather than landing on
        // the limit: a 4000-pixel frame comes back at 1000, not 2000. Under is
        // the side to be on — the point is not to hold a camera frame in
        // memory to draw a thumbnail two inches wide.
        assertEquals(1, sampleSize(1000, 800, maxEdge = 1440))
        assertEquals(4, sampleSize(4000, 3000, maxEdge = 1440))
        assertEquals(8, sampleSize(8000, 6000, maxEdge = 1440))
        assertEquals("a thumbnail shrinks much further", 32, sampleSize(4000, 3000, maxEdge = 200))
    }

    @Test
    fun `whatever comes back is inside the limit it was asked for`() {
        listOf(1000 to 800, 4000 to 3000, 8000 to 6000, 6000 to 8000).forEach { (w, h) ->
            listOf(200, 600, 1440).forEach { maxEdge ->
                val longer = maxOf(w, h) / sampleSize(w, h, maxEdge)
                assertTrue("${w}x$h at $maxEdge came back at $longer", longer <= maxEdge)
            }
        }
    }

    @Test
    fun `a nonsense size does not spin forever inside an image decode`() {
        // A frozen app rather than a crash, which is the worse of the two.
        assertEquals(1, sampleSize(4000, 3000, maxEdge = 0))
        assertEquals(1, sampleSize(4000, 3000, maxEdge = -1))
        assertEquals(1, sampleSize(0, 0, maxEdge = 1440))
    }

    private companion object {
        /** A fixed clock: 2026-01-01T00:00:00Z, so nothing here depends on today. */
        const val NOW = 1_767_225_600L
    }
}
