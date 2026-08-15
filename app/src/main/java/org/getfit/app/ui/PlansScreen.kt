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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.getfit.app.settings.UnitSystem
import org.getfit.app.settings.fromKg
import org.getfit.app.workout.ExerciseCatalog
import org.getfit.app.workout.WorkoutPlan
import org.getfit.app.workout.asDisplayWeight
import java.util.UUID

/**
 * The plans, and the way into a session.
 *
 * Each plan shows the targets it prescribes rather than only the movement
 * names, because "3 × 5 at 60" is what tells somebody whether this is the plan
 * they meant to open.
 */
@Composable
fun PlansScreen(
    env: AppEnv,
    onEditPlan: (String?) -> Unit,
    onOpenSession: () -> Unit,
    onShowDemo: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val plans by env.workouts.plans.collectAsState(initial = emptyList())
    val active by env.workouts.activeSession.collectAsState(initial = null)
    var units by remember { mutableStateOf(UnitSystem.METRIC) }
    var confirmDelete by remember { mutableStateOf<WorkoutPlan?>(null) }

    LaunchedEffect(Unit) {
        units = env.settingsRepository.settings.first().units
    }

    confirmDelete?.let { plan ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete ${plan.name}?") },
            text = {
                Text(
                    "Sessions already logged against it are kept — they are a " +
                        "record of what was done, not of the plan."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        env.workouts.deletePlan(plan.id)
                        confirmDelete = null
                    }
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("Keep") }
            },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Plans",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                TextButton(onClick = { onEditPlan(null) }) { Text("New") }
            }
        }

        if (active != null) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "A session is already running. Finish it before starting another.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Button(
                            onClick = onOpenSession,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Open it") }
                    }
                }
            }
        }

        items(plans, key = { it.id }) { plan ->
            PlanCard(
                plan = plan,
                units = units,
                canStart = active == null,
                onStart = {
                    scope.launch {
                        env.workouts.startSession(
                            plan = plan,
                            now = System.currentTimeMillis() / 1000,
                            id = UUID.randomUUID().toString(),
                        )
                        onOpenSession()
                    }
                },
                onEdit = { onEditPlan(plan.id) },
                onDelete = { confirmDelete = plan },
                onShowDemo = onShowDemo,
            )
        }

        if (plans.isEmpty()) {
            item {
                Text(
                    "No plans yet. Tap New to build one.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun PlanCard(
    plan: WorkoutPlan,
    units: UnitSystem,
    canStart: Boolean,
    onStart: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onShowDemo: (String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    plan.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text("${plan.totalSets} sets", style = MaterialTheme.typography.labelMedium)
            }

            plan.exercises.forEach { planned ->
                // An exercise the catalogue does not know is shown by its id
                // rather than skipped: a plan with a silently missing row would
                // be a plan nobody could tell was broken.
                val exercise = ExerciseCatalog.byId(planned.exerciseId)
                val first = planned.sets.firstOrNull()
                val load = first?.weightKg?.takeIf { it > 0 }
                    ?.let { " at ${units.fromKg(it).asDisplayWeight()} ${units.weightSuffix}" }
                    .orEmpty()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${exercise?.name ?: planned.exerciseId} — " +
                            "${planned.sets.size} × ${first?.reps ?: 0}$load",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { onShowDemo(planned.exerciseId) }) { Text("How") }
                }
            }

            if (plan.notes.isNotBlank()) {
                Text(
                    plan.notes,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Button(onClick = onStart, enabled = canStart, modifier = Modifier.weight(1f)) {
                    Text("Start")
                }
                OutlinedButton(onClick = onEdit) { Text("Edit") }
                OutlinedButton(onClick = onDelete) { Text("Delete") }
            }
        }
    }
}
