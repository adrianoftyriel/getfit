package org.getfit.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import org.getfit.app.settings.UnitSystem
import org.getfit.app.settings.fromKg
import org.getfit.app.settings.fromMetres
import org.getfit.app.settings.toKg
import org.getfit.app.settings.toMetres
import org.getfit.app.workout.DialState
import org.getfit.app.workout.Exercise
import org.getfit.app.workout.KG_DIAL
import org.getfit.app.workout.KM_DIAL
import org.getfit.app.workout.LB_DIAL
import org.getfit.app.workout.LoggedSet
import org.getfit.app.workout.MILE_DIAL
import org.getfit.app.workout.MINUTE_DIAL
import org.getfit.app.workout.Measure
import org.getfit.app.workout.REP_DIAL
import org.getfit.app.workout.angleDelta
import org.getfit.app.workout.asDisplayAmount
import org.getfit.app.workout.asReps
import org.getfit.app.workout.dialAngle
import org.getfit.app.workout.dialAt
import org.getfit.app.workout.turnedBy
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Logging a set by turning a ring with a thumb.
 *
 * A ring rather than a keyboard because of where this is used: standing up,
 * one-handed, halfway through a session, often without looking straight at the
 * screen. Ten reps to a revolution means the common range is a flick, going
 * round again means eleven through twenty, and the number under the thumb is
 * never a keyboard away. Every detent is a haptic tick, so the count can be
 * felt as well as read.
 *
 * The ring turns two numbers, and which two depends on the movement: reps then
 * load for a lift, minutes then distance for cardio. A treadmill has no rep
 * count, so it is not asked for one. Either way it is the same control and the
 * same two taps, and nothing about logging a set involves typing.
 *
 * The arithmetic — what a turn is worth, where a touch is on the ring, and what
 * happens at the seam at twelve o'clock — is all in [DialState] and its
 * neighbours in Dial.kt, and tested there. Nothing in this file decides a
 * number, because nothing in this file can be tested.
 */
@Composable
fun SetDialDialog(
    exerciseName: String,
    setNumber: Int,
    set: LoggedSet,
    exercise: Exercise?,
    units: UnitSystem,
    /** Offered only on a set already logged, as the way to take it back. */
    onNotDone: (() -> Unit)?,
    onDismiss: () -> Unit,
    onLog: (LoggedSet) -> Unit,
) {
    val cardio = exercise?.measure == Measure.CARDIO
    val bodyweight = exercise?.bodyweight == true

    // Two numbers either way, on the same ring, in the same two taps: reps then
    // load for a lift, minutes then distance for cardio. The movement decides
    // which pair it is, and nothing below has to ask twice.
    val primarySpec = if (cardio) MINUTE_DIAL else REP_DIAL
    val secondarySpec = when {
        cardio -> if (units == UnitSystem.IMPERIAL) MILE_DIAL else KM_DIAL
        units == UnitSystem.IMPERIAL -> LB_DIAL
        else -> KG_DIAL
    }

    var primary by remember {
        mutableStateOf(
            dialAt(
                primarySpec,
                if (cardio) (set.seconds ?: 0) / SECONDS_PER_MINUTE else set.reps.toDouble(),
            )
        )
    }
    var secondary by remember {
        mutableStateOf(
            dialAt(
                secondarySpec,
                if (cardio) units.fromMetres(set.metres ?: 0) else units.fromKg(set.weightKg),
            )
        )
    }
    var addingSecond by remember { mutableStateOf(false) }
    var onSecondStep by remember { mutableStateOf(false) }

    // Whether there is a second number worth dialling at all. A pull-up has no
    // load unless weight has been hung off it; a twenty-minute bike ride often
    // has no distance worth recording. Charging every set a tap to confirm a
    // zero, for the sake of the set that needs it, is the thing to avoid in
    // both cases — so it is offered as a button instead.
    val hasSecond = when {
        cardio -> (set.metres ?: 0) > 0 || addingSecond
        else -> !bodyweight || set.weightKg > 0.0 || addingSecond
    }

    val commit: () -> Unit = {
        if (!onSecondStep && hasSecond) {
            onSecondStep = true
        } else {
            // A dial nobody turned leaves its number exactly as it was, rather
            // than snapping a figure that came from the keyboard onto the
            // nearest detent behind their back.
            onLog(
                if (cardio) {
                    set.copy(
                        seconds = if (primary.turned) {
                            (primary.value * SECONDS_PER_MINUTE).roundToInt()
                        } else {
                            set.seconds
                        },
                        metres = if (secondary.turned) {
                            units.toMetres(secondary.value)
                        } else {
                            set.metres
                        },
                        completed = true,
                    )
                } else {
                    set.copy(
                        reps = primary.asReps(),
                        weightKg = if (secondary.turned) {
                            units.toKg(secondary.value)
                        } else {
                            set.weightKg
                        },
                        completed = true,
                    )
                }
            )
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    exerciseName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    "Set $setNumber · " + when {
                        !onSecondStep && cardio -> "Minutes"
                        !onSecondStep -> "Reps"
                        cardio -> "Distance"
                        bodyweight -> "Added weight"
                        else -> "Weight"
                    },
                    style = MaterialTheme.typography.labelMedium,
                )

                Spacer(Modifier.height(12.dp))

                Dial(
                    state = if (onSecondStep) secondary else primary,
                    valueText = when {
                        onSecondStep -> secondary.value.asDisplayAmount()
                        cardio -> primary.value.asDisplayAmount()
                        else -> primary.asReps().toString()
                    },
                    unitText = when {
                        !onSecondStep && cardio -> "min"
                        !onSecondStep -> "reps"
                        cardio -> units.distanceSuffix
                        else -> units.weightSuffix
                    },
                    action = when {
                        onSecondStep || !hasSecond -> "Tap to log"
                        cardio -> "Tap for distance"
                        else -> "Tap for weight"
                    },
                    onTurn = { degrees ->
                        if (onSecondStep) {
                            secondary = secondary.turnedBy(degrees)
                        } else {
                            primary = primary.turnedBy(degrees)
                        }
                    },
                    onCommit = commit,
                )

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Row {
                        when {
                            onSecondStep -> TextButton(
                                onClick = { onSecondStep = false },
                            ) { Text(if (cardio) "Minutes" else "Reps") }

                            cardio && !hasSecond -> TextButton(
                                onClick = {
                                    addingSecond = true
                                    onSecondStep = true
                                },
                            ) { Text("Add distance") }

                            bodyweight && !hasSecond -> TextButton(
                                onClick = {
                                    addingSecond = true
                                    onSecondStep = true
                                },
                            ) { Text("Add weight") }
                        }
                        if (onNotDone != null && !onSecondStep) {
                            TextButton(onClick = onNotDone) { Text("Not done") }
                        }
                    }
                }
            }
        }
    }
}

/** Minutes are the display unit; seconds are what gets stored. */
private const val SECONDS_PER_MINUTE = 60.0

/** The ring itself: a track to turn, and a hub to tap when the number is right. */
@Composable
private fun Dial(
    state: DialState,
    valueText: String,
    unitText: String,
    action: String,
    onTurn: (Float) -> Unit,
    onCommit: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val accent = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant
    val marks = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    val hub = MaterialTheme.colorScheme.surface

    // A tick per detent, so the count can be felt without looking at the phone.
    // Guarded on `turned` so opening the dialog is silent.
    val value = state.value
    LaunchedEffect(value) {
        if (state.turned) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    // The gesture below is started once and lives across every recomposition,
    // so it must not capture the callback it was created with — that one goes
    // stale on the first degree of movement, which is the first thing that
    // happens.
    val turn by rememberUpdatedState(onTurn)

    Box(
        modifier = Modifier
            .widthIn(max = MAX_DIAL)
            .fillMaxWidth()
            .aspectRatio(1f)
            .pointerInput(Unit) {
                val middle = Offset(size.width / 2f, size.height / 2f)
                val deadZone = DEAD_ZONE.toPx()
                // Where the thumb last was, so each move is measured as a step
                // round the ring rather than as an absolute position. Absolute
                // would mean the number jumped to wherever the ring was first
                // touched.
                var previous: Float? = null
                detectDragGestures(
                    onDragStart = { at -> previous = ringAngle(middle, at, deadZone) },
                    onDragEnd = { previous = null },
                    onDragCancel = { previous = null },
                ) { change, _ ->
                    val angle = ringAngle(middle, change.position, deadZone)
                    if (angle != null) {
                        previous?.let { turn(angleDelta(it, angle)) }
                        previous = angle
                    }
                    change.consume()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 20.dp.toPx()
            val radius = (size.minDimension - stroke) / 2f
            val middle = center

            drawCircle(color = track, radius = radius, center = middle, style = Stroke(stroke))

            repeat(state.spec.detentsPerTurn) { index ->
                val radians = Math.toRadians(
                    index * 360.0 / state.spec.detentsPerTurn - QUARTER_TURN
                )
                drawCircle(
                    color = marks,
                    radius = 2.dp.toPx(),
                    center = Offset(
                        middle.x + (cos(radians) * radius).toFloat(),
                        middle.y + (sin(radians) * radius).toFloat(),
                    ),
                )
            }

            // One ring inside the track per whole revolution already made, so
            // the difference between eight reps and eighteen is visible and not
            // only readable. Past a handful they would collide, and by then the
            // number in the middle is doing the work anyway.
            repeat(min(state.turns, MAX_RINGS)) { index ->
                drawCircle(
                    color = accent.copy(alpha = 0.4f),
                    radius = radius - stroke / 2f - RING_GAP.toPx() * (index + 1),
                    center = middle,
                    style = Stroke(2.dp.toPx()),
                )
            }

            val swept = (state.turnProgress * 360.0).toFloat()
            if (swept > 0f) {
                drawArc(
                    color = accent,
                    startAngle = -QUARTER_TURN.toFloat(),
                    sweepAngle = swept,
                    useCenter = false,
                    topLeft = Offset(middle.x - radius, middle.y - radius),
                    size = Size(radius * 2f, radius * 2f),
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }

            val knobAt = Math.toRadians(swept - QUARTER_TURN)
            val knob = Offset(
                middle.x + (cos(knobAt) * radius).toFloat(),
                middle.y + (sin(knobAt) * radius).toFloat(),
            )
            drawCircle(color = accent, radius = stroke / 2f + 3.dp.toPx(), center = knob)
            drawCircle(color = hub, radius = 4.dp.toPx(), center = knob)
        }

        Surface(
            shape = CircleShape,
            color = hub,
            border = BorderStroke(1.dp, accent),
            modifier = Modifier
                .size(HUB)
                .clip(CircleShape)
                .clickable(onClick = onCommit),
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    valueText,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                Text(unitText, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    action,
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * The angle of a touch on the ring, or null if it is too near the middle to
 * have one.
 *
 * Right at the centre every direction is a rounding error away from every
 * other, so a drag passing over the pivot would otherwise fling the number
 * somewhere arbitrary. Samples in there are dropped rather than guessed at.
 */
private fun ringAngle(middle: Offset, at: Offset, deadZone: Float): Float? {
    val dx = at.x - middle.x
    val dy = at.y - middle.y
    return if (hypot(dx, dy) < deadZone) null else dialAngle(dx, dy)
}

/** Twelve o'clock, in the degrees-clockwise-from-three that Canvas draws in. */
private const val QUARTER_TURN = 90.0

private val MAX_DIAL = 300.dp
private val HUB = 136.dp
private val DEAD_ZONE = 36.dp
private val RING_GAP = 6.dp

/** Past four, the rings would reach the hub. By then the number is the point. */
private const val MAX_RINGS = 4
