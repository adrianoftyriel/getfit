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
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import kotlinx.coroutines.launch
import org.getfit.app.nutrition.Confidence
import org.getfit.app.nutrition.InboxResult
import org.getfit.app.nutrition.MealEntry
import org.getfit.app.nutrition.dayLabel
import org.getfit.app.nutrition.groupByDay
import org.getfit.app.nutrition.timeLabel
import org.getfit.app.settings.Settings
import kotlin.math.roundToInt

/**
 * The food log, and the two doors a meal comes in by.
 *
 * A meal from a link arrives as [pendingMeal] and is confirmed before it is
 * filed — the app was opened by a tap on a link, which is not the same as being
 * told to record what the link contains. A meal from the repository inbox is
 * fetched here on request and filed without asking, because asking for the
 * inbox *is* the confirmation.
 */
@Composable
fun NutritionScreen(
    env: AppEnv,
    settings: Settings,
    pendingMeal: MealEntry?,
    onMealHandled: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val meals by env.nutrition.meals.collectAsState(initial = emptyList())
    var syncing by remember { mutableStateOf(false) }
    var syncStatus by remember { mutableStateOf<String?>(null) }

    pendingMeal?.let { meal ->
        MealArrivedDialog(
            meal = meal,
            onAccept = {
                scope.launch {
                    env.nutrition.add(meal)
                    onMealHandled()
                }
            },
            onDismiss = onMealHandled,
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                "Food",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 20.dp),
            )
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Meals from Claude", fontWeight = FontWeight.Bold)
                    Text(
                        "Photograph a meal, ask Claude to analyse it, and it is " +
                            "published to this app's inbox. Collect it here.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedButton(
                        enabled = !syncing,
                        onClick = {
                            scope.launch {
                                syncing = true
                                syncStatus = "Checking…"
                                syncStatus = when (
                                    val result = env.inbox.fetchNew(env.nutrition.importedIds())
                                ) {
                                    is InboxResult.Found -> {
                                        val added = env.nutrition.addAll(result.entries)
                                        if (added == 1) "1 meal added." else "$added meals added."
                                    }

                                    InboxResult.Empty -> "Nothing new."
                                    is InboxResult.Failed -> result.reason
                                }
                                syncing = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (syncing) "Checking…" else "Check for new meals") }

                    syncStatus?.let {
                        Text(it, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        val days = groupByDay(meals)
        if (days.isEmpty()) {
            item {
                Text(
                    "No meals logged yet.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        days.forEach { day ->
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text(
                        dayLabel(day.meals.first().capturedAt),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = settings.calorieTarget
                            ?.let { "${day.totals.displayCalories} / $it kcal" }
                            ?: "${day.totals.displayCalories} kcal",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            items(day.meals) { meal ->
                MealCard(meal = meal, onDelete = { scope.launch { env.nutrition.delete(meal.id) } })
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

// Card's onClick overload is still experimental in this Material 3 version.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MealCard(meal: MealEntry, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        onClick = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.padding(end = 8.dp)) {
                    Text(meal.label, fontWeight = FontWeight.Bold)
                    Text(
                        "${timeLabel(meal.capturedAt)} · ${meal.confidence.label}",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                Text(
                    "${meal.totals.displayCalories} kcal",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            Text(
                text = "P ${meal.totals.proteinG.roundToInt()}g · " +
                    "C ${meal.totals.carbsG.roundToInt()}g · " +
                    "F ${meal.totals.fatG.roundToInt()}g",
                style = MaterialTheme.typography.bodySmall,
            )

            if (expanded) {
                meal.items.forEach { item ->
                    Text(
                        text = "· ${item.name}" +
                            (if (item.quantity.isNotBlank()) " (${item.quantity})" else "") +
                            " — ${item.nutrition.displayCalories} kcal",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (meal.notes.isNotBlank()) {
                    Text(
                        meal.notes,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                // Only worth saying when it is true, and worth saying plainly
                // when it is: the itemised numbers not adding up to the stated
                // total means the estimate disagreed with itself.
                if (!meal.itemsAgreeWithTotals) {
                    Text(
                        "The items add up to ${meal.derivedTotals.displayCalories} kcal, " +
                            "not ${meal.totals.displayCalories}. Treat both as rough.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                TextButton(onClick = onDelete) { Text("Delete") }
            }
        }
    }
}

/** Confirmation for a meal that arrived by link. */
@Composable
private fun MealArrivedDialog(meal: MealEntry, onAccept: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log this meal?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(meal.label, fontWeight = FontWeight.Bold)
                Text("${meal.totals.displayCalories} kcal")
                Text(
                    "P ${meal.totals.proteinG.roundToInt()}g · " +
                        "C ${meal.totals.carbsG.roundToInt()}g · " +
                        "F ${meal.totals.fatG.roundToInt()}g",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (meal.confidence == Confidence.LOW) {
                    Text(
                        meal.confidence.blurb,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onAccept) { Text("Log it") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Discard") } },
    )
}
