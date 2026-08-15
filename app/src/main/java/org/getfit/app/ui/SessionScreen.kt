package org.getfit.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.getfit.app.settings.UnitSystem
import org.getfit.app.settings.fromKg
import org.getfit.app.settings.toKg
import org.getfit.app.ui.theme.CompletedGreen
import org.getfit.app.workout.ExerciseCatalog
import org.getfit.app.workout.LoggedExercise
import org.getfit.app.workout.LoggedSet
import org.getfit.app.workout.WorkoutSession
import org.getfit.app.workout.asDisplayWeight
import org.getfit.app.workout.completedSets
import org.getfit.app.workout.volumeKg

/**
 * Logging a session while it happens.
 *
 * Built around one number: taps per set. A set that went to target is the
 * common case by a wide margin, so it costs exactly one tap — the targets came
 * from the plan and are already on the chip. Anything else is a long press,
 * which opens the set to be corrected. Making every set cost three fields would
 * mean the phone comes out between sets, which is how logging gets abandoned
 * halfway through a session.
 *
 * Every tap writes through to the store rather than being held until the end.
 * A session is forty minutes long and a phone can be killed at any point in it.
 */
@Composable
fun SessionScreen(env: AppEnv, onBack: () -> Unit, onShowDemo: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val session by env.workouts.activeSession.collectAsState(initial = null)
    var units by remember { mutableStateOf(UnitSystem.METRIC) }
    var editing by remember { mutableStateOf<SetAddress?>(null) }
    var confirmFinish by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        units = env.settingsRepository.settings.first().units
    }

    val current = session
    if (current == null) {
        ScreenScaffold(title = "Session", onBack = onBack) { modifier ->
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No session is running. Start one from a plan.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
        }
        return
    }

    editing?.let { address ->
        val set = current.exercises.getOrNull(address.exerciseIndex)
            ?.sets?.getOrNull(address.setIndex)
        if (set != null) {
            EditSetDialog(
                set = set,
                units = units,
                onDismiss = { editing = null },
                onSave = { updated ->
                    scope.launch {
                        env.workouts.updateSession(current.withSet(address, updated))
                        editing = null
                    }
                },
            )
        }
    }

    if (confirmFinish) {
        val done = completedSets(current.exercises)
        val total = current.exercises.sumOf { it.sets.size }
        AlertDialog(
            onDismissRequest = { confirmFinish = false },
            title = { Text("Finish session?") },
            text = {
                Text(
                    if (done < total) {
                        "$done of $total sets are marked done. The rest stay " +
                            "logged as not completed."
                    } else {
                        "All $total sets done."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        env.workouts.finishSession(current.id, System.currentTimeMillis() / 1000)
                        confirmFinish = false
                        onBack()
                    }
                }) { Text("Finish") }
            },
            dismissButton = {
                TextButton(onClick = { confirmFinish = false }) { Text("Keep going") }
            },
        )
    }

    ScreenScaffold(
        title = current.name,
        onBack = onBack,
        backLabel = "Close",
        actions = {
            TextButton(onClick = { confirmFinish = true }) { Text("Finish") }
        },
    ) { modifier ->
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SessionSummary(current, units) }

            current.exercises.forEachIndexed { exerciseIndex, logged ->
                item(key = "ex-$exerciseIndex-${logged.exerciseId}") {
                    ExerciseBlock(
                        logged = logged,
                        units = units,
                        onToggleSet = { setIndex ->
                            scope.launch {
                                val address = SetAddress(exerciseIndex, setIndex)
                                val set = logged.sets[setIndex]
                                env.workouts.updateSession(
                                    current.withSet(address, set.copy(completed = !set.completed))
                                )
                            }
                        },
                        onEditSet = { setIndex ->
                            editing = SetAddress(exerciseIndex, setIndex)
                        },
                        onAddSet = {
                            scope.launch {
                                // A set beyond the plan copies the last one's
                                // targets, because an extra set is nearly
                                // always the same set again.
                                val template = logged.sets.lastOrNull()
                                    ?: LoggedSet(reps = 8, weightKg = 0.0)
                                env.workouts.updateSession(
                                    current.withExercise(
                                        exerciseIndex,
                                        logged.copy(
                                            sets = logged.sets +
                                                template.copy(completed = true),
                                        ),
                                    )
                                )
                            }
                        },
                        onShowDemo = { onShowDemo(logged.exerciseId) },
                    )
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

/** Which set, in a session. */
private data class SetAddress(val exerciseIndex: Int, val setIndex: Int)

private fun WorkoutSession.withExercise(index: Int, replacement: LoggedExercise): WorkoutSession =
    copy(exercises = exercises.toMutableList().also { it[index] = replacement })

private fun WorkoutSession.withSet(address: SetAddress, replacement: LoggedSet): WorkoutSession {
    val exercise = exercises[address.exerciseIndex]
    val sets = exercise.sets.toMutableList().also { it[address.setIndex] = replacement }
    return withExercise(address.exerciseIndex, exercise.copy(sets = sets))
}

@Composable
private fun SessionSummary(session: WorkoutSession, units: UnitSystem) {
    val volume = volumeKg(session.exercises) { ExerciseCatalog.byId(it) }
    val done = completedSets(session.exercises)
    val total = session.exercises.sumOf { it.sets.size }

    Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Stat("Sets", "$done / $total")
            Stat("Volume", "${units.fromKg(volume).asDisplayWeight()} ${units.weightSuffix}")
            Stat("Exercises", "${session.exercises.size}")
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ExerciseBlock(
    logged: LoggedExercise,
    units: UnitSystem,
    onToggleSet: (Int) -> Unit,
    onEditSet: (Int) -> Unit,
    onAddSet: () -> Unit,
    onShowDemo: () -> Unit,
) {
    val exercise = ExerciseCatalog.byId(logged.exerciseId)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(exercise?.name ?: logged.exerciseId, fontWeight = FontWeight.Bold)
                    Text(
                        "${logged.sets.count { it.completed }} of ${logged.sets.size} done",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                TextButton(onClick = onShowDemo) { Text("How") }
            }

            // One chip per set. Tap to complete at target, long press to correct.
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(logged.sets.size) { index ->
                    SetChip(
                        set = logged.sets[index],
                        number = index + 1,
                        units = units,
                        bodyweight = exercise?.bodyweight == true,
                        onTap = { onToggleSet(index) },
                        onLongPress = { onEditSet(index) },
                    )
                }
                item {
                    OutlinedButton(onClick = onAddSet) { Text("+") }
                }
            }
        }
    }
}

/**
 * One set.
 *
 * Filled when it is done, outlined when it is not, so the state of the whole
 * exercise reads at a glance from across a rack.
 */
// Surface has a clickable overload but no long-press one, so the gesture goes
// on a modifier instead of into the component.
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SetChip(
    set: LoggedSet,
    number: Int,
    units: UnitSystem,
    bodyweight: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
) {
    Surface(
        color = if (set.completed) CompletedGreen else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (set.completed) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.combinedClickable(onClick = onTap, onLongClick = onLongPress),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Set $number", style = MaterialTheme.typography.labelSmall)
            Text(
                text = buildString {
                    append("${set.reps}")
                    if (!bodyweight || set.weightKg > 0) {
                        append(" × ${units.fromKg(set.weightKg).asDisplayWeight()}")
                    }
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** Correcting a set that did not go to target. */
@Composable
private fun EditSetDialog(
    set: LoggedSet,
    units: UnitSystem,
    onDismiss: () -> Unit,
    onSave: (LoggedSet) -> Unit,
) {
    var reps by remember { mutableStateOf(set.reps.toString()) }
    var weight by remember {
        mutableStateOf(
            set.weightKg.takeIf { it > 0 }?.let { units.fromKg(it).asDisplayWeight() } ?: ""
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Correct this set") },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = reps,
                    onValueChange = { reps = it.filter(Char::isDigit).take(3) },
                    label = { Text("Reps") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = weight,
                    onValueChange = {
                        weight = it.filter { c -> c.isDigit() || c == '.' }.take(6)
                    },
                    label = { Text(units.weightSuffix) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    set.copy(
                        // A blank or unparseable field keeps what was there,
                        // rather than silently zeroing a set that was done.
                        reps = reps.toIntOrNull()?.coerceIn(0, 500) ?: set.reps,
                        weightKg = weight.toDoubleOrNull()
                            ?.let { units.toKg(it) }
                            ?: set.weightKg,
                        completed = true,
                    )
                )
            }) { Text("Save as done") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
