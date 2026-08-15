package org.getfit.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.getfit.app.settings.UnitSystem
import org.getfit.app.settings.fromKg
import org.getfit.app.settings.fromMetres
import org.getfit.app.settings.toKg
import org.getfit.app.settings.toMetres
import org.getfit.app.workout.ExerciseCatalog
import org.getfit.app.workout.Measure
import org.getfit.app.workout.PlanExercise
import org.getfit.app.workout.PlannedSet
import org.getfit.app.workout.WorkoutPlan
import org.getfit.app.workout.asDisplayAmount
import org.getfit.app.workout.asDisplayWeight
import java.util.UUID
import kotlin.math.roundToInt

/**
 * Building a plan: the exercises, and the targets for each.
 *
 * The targets are the point of the screen. A plan without them is a list of
 * names, and the logging screen has nothing to prefill from — which is what
 * makes logging a set one tap instead of three fields.
 *
 * Edits are held here and written once, on Save. A plan half-edited into the
 * store would be a plan the next session could start from.
 */
@Composable
fun PlanEditorScreen(env: AppEnv, planId: String?, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var loaded by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    val rows = remember { mutableStateListOf<EditableExercise>() }
    var picking by remember { mutableStateOf(false) }
    var units by remember { mutableStateOf(UnitSystem.METRIC) }

    LaunchedEffect(planId) {
        units = env.settingsRepository.settings.first().units
        val existing = planId?.let { id -> env.workouts.load().plans.firstOrNull { it.id == id } }
        name = existing?.name ?: ""
        notes = existing?.notes ?: ""
        rows.clear()
        existing?.exercises?.forEach { rows.add(EditableExercise.from(it, units)) }
        loaded = true
    }

    if (picking) {
        ExercisePicker(
            alreadyIn = rows.map { it.exerciseId }.toSet(),
            onPick = { exercise ->
                rows.add(EditableExercise.blank(exercise.id))
                picking = false
            },
            onDismiss = { picking = false },
        )
    }

    ScreenScaffold(
        title = if (planId == null) "New plan" else "Edit plan",
        onBack = onDone,
        backLabel = "Cancel",
        actions = {
            TextButton(
                enabled = loaded && name.isNotBlank() && rows.isNotEmpty(),
                onClick = {
                    scope.launch {
                        env.workouts.savePlan(
                            WorkoutPlan(
                                id = planId ?: UUID.randomUUID().toString(),
                                name = name.trim(),
                                notes = notes.trim(),
                                exercises = rows.mapNotNull { it.toPlanExercise(units) },
                            )
                        )
                        onDone()
                    }
                },
            ) { Text("Save") }
        },
    ) { modifier ->
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Plan name") },
                    placeholder = { Text("Push A") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }

            items(rows, key = { it.key }) { row ->
                ExerciseTargetCard(
                    row = row,
                    units = units,
                    onRemove = { rows.remove(row) },
                )
            }

            item {
                OutlinedButton(
                    onClick = { picking = true },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Add an exercise") }
            }

            item {
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (rows.isEmpty()) {
                item {
                    Text(
                        "A plan needs at least one exercise before it can be saved.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

/**
 * One exercise's targets while they are being edited.
 *
 * Text rather than numbers, because a field being cleared mid-edit is a normal
 * thing to do and parsing on every keystroke would rewrite "1" to 1 and refuse
 * the "2" that was about to follow. Parsing happens once, on save.
 */
private class EditableExercise(
    val key: String,
    val exerciseId: String,
    sets: String,
    reps: String,
    weight: String,
) {
    var sets by mutableStateOf(sets)

    /** Reps, or minutes where the movement is counted on a clock. */
    var reps by mutableStateOf(reps)

    /** Load, or distance, for the same reason. */
    var weight by mutableStateOf(weight)

    val cardio: Boolean
        get() = ExerciseCatalog.byId(exerciseId)?.measure == Measure.CARDIO

    fun toPlanExercise(units: UnitSystem): PlanExercise? {
        val setCount = sets.toIntOrNull()?.coerceIn(1, MAX_SETS) ?: return null
        if (cardio) {
            // A target of no minutes is not a target, so an unparseable or
            // blank field refuses the row rather than planning a zero.
            val minutes = reps.toDoubleOrNull()?.coerceIn(0.0, MAX_MINUTES) ?: return null
            val distance = weight.toDoubleOrNull() ?: 0.0
            return PlanExercise(
                exerciseId = exerciseId,
                sets = List(setCount) {
                    PlannedSet(
                        reps = 0,
                        seconds = (minutes * 60).roundToInt(),
                        metres = units.toMetres(distance).takeIf { it > 0 },
                    )
                },
            )
        }
        val repCount = reps.toIntOrNull()?.coerceIn(1, MAX_REPS) ?: return null
        val load = weight.toDoubleOrNull() ?: 0.0
        return PlanExercise(
            exerciseId = exerciseId,
            sets = List(setCount) {
                PlannedSet(reps = repCount, weightKg = units.toKg(load))
            },
        )
    }

    companion object {
        const val MAX_SETS = 20
        const val MAX_REPS = 500
        const val MAX_MINUTES = 180.0

        fun blank(exerciseId: String) = EditableExercise(
            key = UUID.randomUUID().toString(),
            exerciseId = exerciseId,
            sets = if (ExerciseCatalog.byId(exerciseId)?.measure == Measure.CARDIO) "1" else "3",
            reps = if (ExerciseCatalog.byId(exerciseId)?.measure == Measure.CARDIO) "20" else "8",
            weight = "",
        )

        /**
         * Collapses a plan's sets back to one row of targets.
         *
         * The model allows every set its own reps and load; this editor offers
         * "3 × 8 at 60" because that is what a plan is written as. A plan built
         * elsewhere with varying sets shows its first set here, and saving
         * flattens the rest to match — which is why the editor says so rather
         * than doing it silently.
         */
        fun from(planned: PlanExercise, units: UnitSystem): EditableExercise {
            val first = planned.sets.firstOrNull()
            val cardio = ExerciseCatalog.byId(planned.exerciseId)?.measure == Measure.CARDIO
            return EditableExercise(
                key = UUID.randomUUID().toString(),
                exerciseId = planned.exerciseId,
                sets = planned.sets.size.toString(),
                reps = if (cardio) {
                    ((first?.seconds ?: 0) / 60.0).asDisplayAmount()
                } else {
                    (first?.reps ?: 8).toString()
                },
                weight = if (cardio) {
                    first?.metres
                        ?.takeIf { it > 0 }
                        ?.let { units.fromMetres(it).asDisplayAmount() }
                        ?: ""
                } else {
                    first?.weightKg
                        ?.takeIf { it > 0 }
                        ?.let { units.fromKg(it).asDisplayWeight() }
                        ?: ""
                },
            )
        }
    }
}

@Composable
private fun ExerciseTargetCard(
    row: EditableExercise,
    units: UnitSystem,
    onRemove: () -> Unit,
) {
    val exercise = ExerciseCatalog.byId(row.exerciseId)

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
                    Text(
                        exercise?.name ?: row.exerciseId,
                        fontWeight = FontWeight.Bold,
                    )
                    exercise?.let {
                        Text(
                            "${it.group.label} · ${it.equipment.label}",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
                TextButton(onClick = onRemove) { Text("Remove") }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(
                    value = row.sets,
                    onValueChange = { row.sets = it },
                    label = "Sets",
                    modifier = Modifier.weight(1f),
                )
                NumberField(
                    value = row.reps,
                    onValueChange = { row.reps = it },
                    label = if (row.cardio) "Minutes" else "Reps",
                    allowDecimal = row.cardio,
                    modifier = Modifier.weight(1f),
                )
                NumberField(
                    value = row.weight,
                    onValueChange = { row.weight = it },
                    label = if (row.cardio) units.distanceSuffix else units.weightSuffix,
                    allowDecimal = true,
                    modifier = Modifier.weight(1.2f),
                )
            }

            when {
                row.cardio -> Text(
                    "Counted in minutes, with distance if there is one to aim for.",
                    style = MaterialTheme.typography.labelSmall,
                )
                exercise?.bodyweight == true -> Text(
                    "Bodyweight — leave the load blank, or enter added weight only.",
                    style = MaterialTheme.typography.labelSmall,
                )
                else -> {}
            }
        }
    }
}

@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    allowDecimal: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { typed ->
            // Filtered at the point of entry rather than validated afterwards,
            // so a stray letter never reaches the field at all.
            val cleaned = typed.filter { it.isDigit() || (allowDecimal && it == '.') }
                .let { if (allowDecimal) it.take(6) else it.take(3) }
            onValueChange(cleaned)
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (allowDecimal) KeyboardType.Decimal else KeyboardType.Number,
        ),
        modifier = modifier,
    )
}
