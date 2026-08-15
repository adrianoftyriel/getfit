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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import org.getfit.app.nutrition.Nutrition
import org.getfit.app.nutrition.dayKey
import org.getfit.app.progress.progressComparison
import org.getfit.app.settings.Settings
import org.getfit.app.workout.ExerciseCatalog
import org.getfit.app.workout.WorkoutSession
import org.getfit.app.workout.asDisplayWeight
import org.getfit.app.workout.completedSets
import org.getfit.app.workout.volumeKg
import java.util.UUID
import kotlin.math.roundToInt

/**
 * The screen the app opens on: what is happening now, and what happened
 * recently.
 *
 * Deliberately not a dashboard. One thing is actionable here — the session,
 * either running or not started — and everything below it is there to be
 * glanced at rather than tapped.
 */
@Composable
fun TodayScreen(
    env: AppEnv,
    settings: Settings,
    onOpenSession: () -> Unit,
    onBrowsePlans: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val active by env.workouts.activeSession.collectAsState(initial = null)
    val sessions by env.workouts.sessions.collectAsState(initial = emptyList())
    val meals by env.nutrition.meals.collectAsState(initial = emptyList())
    val progressEntries by env.progress.entries.collectAsState(initial = emptyList())

    val now = System.currentTimeMillis() / 1000
    val today = dayKey(now)
    val todaysMeals = meals.filter { dayKey(it.capturedAt) == today }
    val eaten = todaysMeals.fold(Nutrition.ZERO) { sum, meal -> sum + meal.totals }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = "Hello, ${settings.displayName}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 20.dp),
            )
        }

        item {
            val running = active
            if (running != null) {
                ActiveSessionCard(
                    session = running,
                    onOpen = onOpenSession,
                    onDiscard = { scope.launch { env.workouts.discardSession(running.id) } },
                )
            } else {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("No session running", fontWeight = FontWeight.Bold)
                        Text(
                            "Start one from a plan, or train without one and log as you go.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Button(
                            onClick = onBrowsePlans,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Start from a plan") }
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    env.workouts.startSession(
                                        plan = null,
                                        now = System.currentTimeMillis() / 1000,
                                        id = UUID.randomUUID().toString(),
                                    )
                                    onOpenSession()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Train without a plan") }
                    }
                }
            }
        }

        // Turns up unasked, on a day nobody went looking for it, and only when
        // there are two photographs far enough apart to mean something. Which
        // "before" it uses rotates by the day, so it is a different pair this
        // week from last rather than an image that stops being looked at.
        val comparison = progressComparison(progressEntries, now)
        if (comparison != null) {
            item {
                ComparisonCard(
                    photos = env.progress.photos,
                    comparison = comparison,
                    units = settings.units,
                    title = "How far you have come",
                )
            }
        }

        item { CalorieCard(eaten = eaten, target = settings.calorieTarget, mealCount = todaysMeals.size) }

        item {
            Text(
                "Recent sessions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        val finished = sessions.filter { !it.inProgress }
        if (finished.isEmpty()) {
            item {
                Text(
                    "Nothing logged yet.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 24.dp),
                )
            }
        } else {
            items(finished.take(10)) { session -> SessionRow(session) }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun ActiveSessionCard(
    session: WorkoutSession,
    onOpen: () -> Unit,
    onDiscard: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("In progress", style = MaterialTheme.typography.labelMedium)
            Text(session.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "${completedSets(session.exercises)} of " +
                    "${session.exercises.sumOf { it.sets.size }} sets done",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onOpen) { Text("Continue") }
                OutlinedButton(onClick = onDiscard) { Text("Discard") }
            }
        }
    }
}

@Composable
private fun CalorieCard(eaten: Nutrition, target: Int?, mealCount: Int) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Today's food", fontWeight = FontWeight.Bold)
                Text(
                    text = if (mealCount == 1) "1 meal" else "$mealCount meals",
                    style = MaterialTheme.typography.labelMedium,
                )
            }

            Text(
                text = if (target != null) "${eaten.displayCalories} / $target kcal"
                else "${eaten.displayCalories} kcal",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )

            if (target != null && target > 0) {
                // Clamped, so going over the target fills the bar rather than
                // overflowing it. The number above already says by how much.
                LinearProgressIndicator(
                    progress = { (eaten.calories / target).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Text(
                text = "P ${eaten.proteinG.roundToInt()}g · " +
                    "C ${eaten.carbsG.roundToInt()}g · " +
                    "F ${eaten.fatG.roundToInt()}g",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun SessionRow(session: WorkoutSession) {
    val volume = volumeKg(session.exercises) { ExerciseCatalog.byId(it) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(session.name, fontWeight = FontWeight.Bold)
            Text(
                text = "${completedSets(session.exercises)} sets · " +
                    "${volume.asDisplayWeight()} kg total" +
                    (session.durationSeconds?.let { " · ${it / 60} min" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
