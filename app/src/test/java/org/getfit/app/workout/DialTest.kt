package org.getfit.app.workout

import org.getfit.app.settings.KG_PER_LB
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The dial that logs a set.
 *
 * A control whose whole promise is "one turn is ten reps" is only worth having
 * if that holds at the seam, on the second time round, and at the ends of the
 * scale. It cannot be checked by hand here — there is no emulator and no build
 * — so what the thumb is supposed to do to the number is pinned here instead.
 */
class DialTest {

    // -- One turn is ten ------------------------------------------------------

    @Test
    fun `a full turn of the rep dial is ten reps`() {
        val dial = dialAt(REP_DIAL, 0.0).turnedBy(360f)
        assertEquals(10, dial.asReps())
    }

    @Test
    fun `going round a second time keeps counting rather than starting over`() {
        // The reason the control exists: twenty is two turns from nothing, not
        // the same place on the rim as ten.
        val once = dialAt(REP_DIAL, 0.0).turnedBy(360f)
        val twice = once.turnedBy(360f)
        val thrice = twice.turnedBy(360f)
        assertEquals(10, once.asReps())
        assertEquals(20, twice.asReps())
        assertEquals(30, thrice.asReps())
    }

    @Test
    fun `each mark on the rim is one rep`() {
        assertEquals(10, REP_DIAL.detentsPerTurn)
        assertEquals(1, dialAt(REP_DIAL, 0.0).turnedBy(36f).asReps())
        assertEquals(5, dialAt(REP_DIAL, 0.0).turnedBy(180f).asReps())
    }

    @Test
    fun `a turn lands between two marks on the nearer of them`() {
        // Twenty degrees is just over half a detent, so it reads as one rep and
        // not as a fraction of one.
        assertEquals(1, dialAt(REP_DIAL, 0.0).turnedBy(20f).asReps())
        assertEquals(0, dialAt(REP_DIAL, 0.0).turnedBy(16f).asReps())
    }

    @Test
    fun `turning back gives the previous number back`() {
        val dial = dialAt(REP_DIAL, 0.0).turnedBy(360f).turnedBy(-36f)
        assertEquals(9, dial.asReps())
    }

    // -- The ends of the scale ------------------------------------------------

    @Test
    fun `a thumb that keeps going parks on the ceiling`() {
        // Rolling over to nothing would log a set of zero at the moment
        // somebody is trying to log their heaviest.
        val dial = dialAt(REP_DIAL, 0.0).turnedBy(360f * 40)
        assertEquals(REP_DIAL.max.toInt(), dial.asReps())
    }

    @Test
    fun `winding backwards stops at nothing rather than going negative`() {
        val dial = dialAt(REP_DIAL, 5.0).turnedBy(-360f * 3)
        assertEquals(0, dial.asReps())
        assertEquals(0.0, dial.sweep, 0.001)
    }

    // -- Opening on what is already there -------------------------------------

    @Test
    fun `the dial opens on the set it was opened from`() {
        val dial = dialAt(REP_DIAL, 8.0)
        assertEquals(8, dial.asReps())
        assertEquals("eight of ten reps is most of the way round", 288.0, dial.sweep, 0.001)
    }

    @Test
    fun `a dial that was only looked at does not rewrite the number`() {
        // 132.3 lb sits between two five-pound detents. Opening the dial on it
        // and closing it again must leave it alone; only a thumb on the ring
        // means the number is being asked to change.
        val dial = dialAt(LB_DIAL, 132.3)
        assertFalse(dial.turned)
        assertEquals(132.3, dial.value, 0.0001)
        assertTrue("but once it moves, it snaps to a detent", dial.turnedBy(1f).value % LB_DIAL.step == 0.0)
    }

    @Test
    fun `a weight beyond the scale still shows until the dial is turned`() {
        val dial = dialAt(KG_DIAL, 400.0)
        assertEquals(400.0, dial.value, 0.0001)
        assertEquals("turning it brings it back onto the scale", KG_DIAL.max, dial.turnedBy(1f).value, 0.0001)
    }

    // -- Load ------------------------------------------------------------------

    @Test
    fun `a full turn of the load dial is ten plate changes`() {
        assertEquals(25.0, KG_DIAL.perTurn, 0.0001)
        assertEquals(50.0, LB_DIAL.perTurn, 0.0001)
        assertEquals(25.0, dialAt(KG_DIAL, 0.0).turnedBy(360f).value, 0.0001)
        assertEquals(2.5, dialAt(KG_DIAL, 0.0).turnedBy(36f).value, 0.0001)
    }

    @Test
    fun `the two load scales stop at the same weight either side of the conversion`() {
        // 660 lb is 299.4 kg. Switching units must not change what can be
        // logged, or a lifter would find their top set refused in one and
        // accepted in the other.
        val imperialCeilingInKg = LB_DIAL.max * KG_PER_LB
        assertTrue(
            "$imperialCeilingInKg kg should be within a plate of ${KG_DIAL.max} kg",
            abs(imperialCeilingInKg - KG_DIAL.max) < 2.0,
        )
    }

    @Test
    fun `a working weight is a nudge from where it opens`() {
        val dial = dialAt(KG_DIAL, 60.0)
        assertEquals(2, dial.turns)
        assertEquals(0.4, dial.turnProgress, 0.0001)
        assertEquals(62.5, dial.turnedBy(36f).value, 0.0001)
    }

    // -- Drawing the ring ------------------------------------------------------

    @Test
    fun `turns and progress split the sweep for the ring to draw`() {
        val dial = dialAt(REP_DIAL, 0.0).turnedBy(360f + 180f)
        assertEquals(1, dial.turns)
        assertEquals(0.5, dial.turnProgress, 0.0001)
        assertEquals(15, dial.asReps())
    }

    @Test
    fun `an untouched dial has no turns to draw`() {
        assertEquals(0, dialAt(REP_DIAL, 4.0).turns)
        assertEquals(0.4, dialAt(REP_DIAL, 4.0).turnProgress, 0.0001)
    }

    // -- Reading the thumb -----------------------------------------------------

    @Test
    fun `twelve o'clock is nothing and the angle runs clockwise`() {
        assertEquals(0f, dialAngle(0f, -1f), 0.01f)
        assertEquals(90f, dialAngle(1f, 0f), 0.01f)
        assertEquals(180f, dialAngle(0f, 1f), 0.01f)
        assertEquals(270f, dialAngle(-1f, 0f), 0.01f)
    }

    @Test
    fun `crossing twelve o'clock is a small move, not most of a turn`() {
        // The seam. Read the long way round, the dial would jump nine reps
        // every time a thumb passed the top.
        assertEquals(20f, angleDelta(350f, 10f), 0.01f)
        assertEquals(-20f, angleDelta(10f, 350f), 0.01f)
    }

    @Test
    fun `an ordinary move is just the difference`() {
        assertEquals(30f, angleDelta(100f, 130f), 0.01f)
        assertEquals(-30f, angleDelta(130f, 100f), 0.01f)
        assertEquals(0f, angleDelta(42f, 42f), 0.01f)
    }

    @Test
    fun `a turn accumulated a degree at a time comes out where it should`() {
        // What actually happens: a stream of small deltas from a moving finger,
        // including one across the seam.
        var dial = dialAt(REP_DIAL, 0.0)
        var previous = 0f
        for (degree in 1..720) {
            val angle = (degree % 360).toFloat()
            dial = dial.turnedBy(angleDelta(previous, angle))
            previous = angle
        }
        assertEquals("two full revolutions of the finger", 20, dial.asReps())
    }
}
