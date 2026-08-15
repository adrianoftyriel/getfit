package org.getfit.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.getfit.app.workout.Exercise
import org.getfit.app.workout.ExerciseCatalog
import org.getfit.app.workout.MuscleGroup

/**
 * Picking a movement out of the catalogue, grouped so it can be scanned.
 *
 * Shared by the plan editor and the session screen rather than written twice.
 * The two are choosing for different reasons — one is writing down what is
 * meant to happen, the other is recording what just did — but the question is
 * the same question, and a list of movements that had drifted apart between
 * them would be worse than either.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisePicker(
    alreadyIn: Set<String>,
    onPick: (Exercise) -> Unit,
    onDismiss: () -> Unit,
) {
    var group by remember { mutableStateOf<MuscleGroup?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add an exercise") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Scrolls sideways rather than wrapping: eight groups will not
                // fit across a phone, and a wrapped row would push the list
                // itself off the bottom of the dialog.
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item {
                        FilterChip(
                            selected = group == null,
                            onClick = { group = null },
                            label = { Text("All") },
                        )
                    }
                    items(ExerciseCatalog.populatedGroups) { entry ->
                        FilterChip(
                            selected = group == entry,
                            onClick = { group = entry },
                            label = { Text(entry.label) },
                        )
                    }
                }

                HorizontalDivider()

                val shown = ExerciseCatalog.all.filter { group == null || it.group == group }
                LazyColumn(modifier = Modifier.height(320.dp)) {
                    items(shown, key = { it.id }) { exercise ->
                        val already = exercise.id in alreadyIn
                        TextButton(
                            onClick = { onPick(exercise) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(exercise.name)
                                // Not disabled: the same movement twice in one
                                // session is a real thing to do, so this says
                                // "already in" rather than refusing.
                                if (already) {
                                    Text(
                                        "already in",
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
