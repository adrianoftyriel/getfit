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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.getfit.app.workout.ExerciseCatalog
import org.getfit.app.workout.WorkoutPlan
import java.util.UUID

/**
 * The plans, and the way into a session.
 *
 * Editing a plan is not built yet — the scaffold ships the starter templates
 * and the ability to train from them, which is the loop worth having working
 * before the editor is worth writing.
 */
@Composable
fun PlansScreen(env: AppEnv) {
    val scope = rememberCoroutineScope()
    val plans by env.workouts.plans.collectAsState(initial = emptyList())
    val active by env.workouts.activeSession.collectAsState(initial = null)

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                "Plans",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 20.dp),
            )
        }

        if (active != null) {
            item {
                Text(
                    "A session is already running. Finish it before starting another.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        items(plans) { plan ->
            PlanCard(
                plan = plan,
                canStart = active == null,
                onStart = {
                    scope.launch {
                        env.workouts.startSession(
                            plan = plan,
                            now = System.currentTimeMillis() / 1000,
                            id = UUID.randomUUID().toString(),
                        )
                    }
                },
            )
        }

        if (plans.isEmpty()) {
            item {
                Text("No plans yet.", style = MaterialTheme.typography.bodySmall)
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun PlanCard(plan: WorkoutPlan, canStart: Boolean, onStart: () -> Unit) {
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
                Text(plan.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("${plan.totalSets} sets", style = MaterialTheme.typography.labelMedium)
            }

            plan.exercises.forEach { planned ->
                // An exercise the catalogue does not know is shown by its id
                // rather than skipped: a plan with a silently missing row would
                // be a plan nobody could tell was broken.
                val name = ExerciseCatalog.byId(planned.exerciseId)?.name ?: planned.exerciseId
                val reps = planned.sets.firstOrNull()?.reps ?: 0
                Text(
                    text = "$name — ${planned.sets.size} × $reps",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (plan.notes.isNotBlank()) {
                Text(
                    plan.notes,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            Button(
                onClick = onStart,
                enabled = canStart,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) { Text("Start") }
        }
    }
}
