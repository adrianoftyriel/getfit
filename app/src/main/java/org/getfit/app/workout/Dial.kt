package org.getfit.app.workout

import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * The maths behind the logging dial: a ring turned with a thumb, where one
 * whole revolution is worth ten of whatever is being counted.
 *
 * Turning past twelve o'clock does not wrap back to the start. The sweep
 * accumulates, so a second time round reads eleven, twelve, … twenty, and a
 * third goes on to thirty. That is the entire point of the control: eighteen
 * reps and eight reps are a turn apart rather than the same position on a clock
 * face, and neither can be logged as the other by stopping in the wrong place.
 *
 * None of this is Android, so none of it is in the composable. The parts that
 * can quietly go wrong — the seam at twelve o'clock, snapping to a detent, the
 * bounds, and a dial that was opened but never turned — are exactly the parts
 * worth pinning in a test, and the screen cannot be tested here at all.
 */

/** A whole revolution of the finger. */
const val DEGREES_PER_TURN = 360.0

/**
 * How a dial is calibrated: what one detent is worth, and how many detents make
 * up a turn.
 *
 * [min] and [max] are the ends of the scale and the dial does not travel past
 * them, so a thumb that keeps going round parks on the ceiling rather than
 * rolling over to nothing.
 */
data class DialSpec(
    val step: Double,
    val detentsPerTurn: Int,
    val min: Double,
    val max: Double,
) {
    /** What one revolution is worth: ten reps on the rep dial. */
    val perTurn: Double get() = step * detentsPerTurn

    /** The sweep that lands on [max]. The dial stops here. */
    val maxSweep: Double get() = (max - min) / perTurn * DEGREES_PER_TURN
}

/**
 * Reps: one to a detent, ten to a turn, which is what the ten marks on the rim
 * mean. A hundred is the ceiling — past that it is a cardio interval being
 * logged as a set, and thumbing round ten times to say so is not the way.
 */
val REP_DIAL = DialSpec(step = 1.0, detentsPerTurn = 10, min = 0.0, max = 100.0)

/**
 * Load, in whichever units are on screen. Ten detents to a turn as well, so
 * both dials feel the same under a thumb, and a detent is the smallest change
 * anybody actually makes to a bar: a 1.25 kg pair, or a 2.5 lb pair.
 *
 * The two ceilings are the same weight either side of the conversion, near
 * enough — 660 lb is 299.4 kg — so switching units does not change what the app
 * will let somebody log.
 */
val KG_DIAL = DialSpec(step = 2.5, detentsPerTurn = 10, min = 0.0, max = 300.0)
val LB_DIAL = DialSpec(step = 5.0, detentsPerTurn = 10, min = 0.0, max = 660.0)

/** Where a value sits on the dial: how far round from [DialSpec.min] it is. */
fun DialSpec.sweepFor(value: Double): Double =
    ((value - min) / perTurn * DEGREES_PER_TURN).coerceIn(0.0, maxSweep)

/**
 * The value a sweep has landed on, snapped to the nearest detent.
 *
 * Snapping is measured from [DialSpec.min] rather than from zero, so a scale
 * that does not start on a multiple of its own step still has its detents where
 * the marks on the rim say they are.
 */
fun DialSpec.valueAt(sweep: Double): Double {
    val raw = min + sweep.coerceIn(0.0, maxSweep) / DEGREES_PER_TURN * perTurn
    val snapped = min + ((raw - min) / step).roundToInt() * step
    return snapped.coerceIn(min, max)
}

/**
 * A dial as it currently stands.
 *
 * [turned] is the difference between a dial somebody moved and one they only
 * opened, and it is load-bearing. A set logged at 132.3 lb, opened on a dial
 * whose detents are five pounds apart, sits between two of them; snapping it on
 * sight would rewrite a weight nobody touched. So an untouched dial reports
 * exactly what it was opened on, and only starts snapping once it has been
 * turned — at which point the number under the thumb is the one being asked
 * for.
 *
 * For the same reason [start] can sit outside the scale: a set logged at some
 * absurd weight through the keyboard shows that weight until the dial is
 * turned, rather than being silently clamped by a screen that was only opened
 * to look at it.
 */
data class DialState(
    val spec: DialSpec,
    val start: Double,
    val sweep: Double,
    val turned: Boolean = false,
) {
    val value: Double get() = if (turned) spec.valueAt(sweep) else start

    /** Whole revolutions made so far, which the rings inside the track show. */
    val turns: Int get() = floor(sweep / DEGREES_PER_TURN).toInt()

    /** How far round the current revolution is, in 0..1, for the arc. */
    val turnProgress: Double get() = sweep / DEGREES_PER_TURN - turns
}

/** A dial opened on the value that is already logged. */
fun dialAt(spec: DialSpec, start: Double): DialState =
    DialState(spec, start, spec.sweepFor(start))

/** One movement of the thumb, in degrees; positive is clockwise. */
fun DialState.turnedBy(degrees: Float): DialState =
    copy(sweep = (sweep + degrees).coerceIn(0.0, spec.maxSweep), turned = true)

/** Reps are whole, whatever the arithmetic did on the way. */
fun DialState.asReps(): Int = value.roundToInt().coerceIn(0, 500)

/**
 * Where a touch is on the ring: degrees clockwise from twelve o'clock, in
 * 0 until 360.
 *
 * Screen y grows downward, which is why the vertical component is negated —
 * without that the dial would count anticlockwise and every turn would run
 * backwards.
 */
fun dialAngle(dx: Float, dy: Float): Float {
    val degrees = Math.toDegrees(atan2(dx.toDouble(), -dy.toDouble()))
    return ((degrees + DEGREES_PER_TURN) % DEGREES_PER_TURN).toFloat()
}

/**
 * How far the thumb moved between two samples: signed, positive clockwise, and
 * never more than half a turn either way.
 *
 * The short way round is always the right answer here because the samples come
 * from a finger that has not left the glass: crossing twelve o'clock is a
 * couple of degrees of movement, and reading it as the 358 degrees the raw
 * subtraction gives would spin the dial through most of a turn at the top of
 * every revolution.
 */
fun angleDelta(from: Float, to: Float): Float {
    var delta = (to - from) % DEGREES_PER_TURN.toFloat()
    if (delta > 180f) delta -= DEGREES_PER_TURN.toFloat()
    if (delta <= -180f) delta += DEGREES_PER_TURN.toFloat()
    return delta
}
