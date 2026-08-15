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
import org.getfit.app.settings.toKg
import org.getfit.app.workout.DialState
import org.getfit.app.workout.KG_DIAL
import org.getfit.app.workout.LB_DIAL
import org.getfit.app.workout.LoggedSet
import org.getfit.app.workout.REP_DIAL
import org.getfit.app.workout.angleDelta
import org.getfit.app.workout.asDisplayWeight
import org.getfit.app.workout.asReps
import org.getfit.app.workout.dialAngle
import org.getfit.app.workout.dialAt
import org.getfit.app.workout.turnedBy
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
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
 * Weight gets the same ring when the movement has a load, on a second turn of
 * the same control, so nothing about logging a set involves typing.
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
    bodyweight: Boolean,
    units: UnitSystem,
    /** Offered only on a set already logged, as the way to take it back. */
    onNotDone: (() -> Unit)?,
    onDismiss: () -> Unit,
    onLog: (LoggedSet) -> Unit,
) {
    val loadSpec = if (units == UnitSystem.IMPERIAL) LB_DIAL else KG_DIAL
    var reps by remember { mutableStateOf(dialAt(REP_DIAL, set.reps.toDouble())) }
    var load by remember { mutableStateOf(dialAt(loadSpec, units.fromKg(set.weightKg))) }
    var addingLoad by remember { mutableStateOf(false) }
    var onLoadStep by remember { mutableStateOf(false) }

    // Whether there is a load worth dialling. A pull-up has none unless weight
    // has been hung off it, and asking for a second turn of the ring to confirm
    // a zero every time would be a tap charged on every set for the rare one.
    val loaded = !bodyweight || set.weightKg > 0.0 || addingLoad

    val commit: () -> Unit = {
        if (!onLoadStep && loaded) {
            onLoadStep = true
        } else {
            onLog(
                set.copy(
                    reps = reps.asReps(),
                    // A dial nobody turned leaves the weight exactly as it was,
                    // rather than snapping a figure typed through the keyboard
                    // onto the nearest detent behind their back.
                    weightKg = if (load.turned) units.toKg(load.value) else set.weightKg,
                    completed = true,
                )
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
                        !onLoadStep -> "Reps"
                        bodyweight -> "Added weight"
                        else -> "Weight"
                    },
                    style = MaterialTheme.typography.labelMedium,
                )

                Spacer(Modifier.height(12.dp))

                Dial(
                    state = if (onLoadStep) load else reps,
                    valueText = if (onLoadStep) {
                        load.value.asDisplayWeight()
                    } else {
                        reps.asReps().toString()
                    },
                    unitText = if (onLoadStep) units.weightSuffix else "reps",
                    action = if (!onLoadStep && loaded) "Tap for weight" else "Tap to log",
                    onTurn = { degrees ->
                        if (onLoadStep) {
                            load = load.turnedBy(degrees)
                        } else {
                            reps = reps.turnedBy(degrees)
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
                            onLoadStep ->
                                TextButton(onClick = { onLoadStep = false }) { Text("Reps") }
                            bodyweight && !loaded ->
                                TextButton(onClick = {
                                    addingLoad = true
                                    onLoadStep = true
                                }) { Text("Add weight") }
                        }
                        if (onNotDone != null && !onLoadStep) {
                            TextButton(onClick = onNotDone) { Text("Not done") }
                        }
                    }
                }
            }
        }
    }
}

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
