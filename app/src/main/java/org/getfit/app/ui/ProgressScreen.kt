package org.getfit.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.getfit.app.nutrition.dayLabel
import org.getfit.app.progress.Comparison
import org.getfit.app.progress.MAX_PHOTOS_PER_ENTRY
import org.getfit.app.progress.PhotoStore
import org.getfit.app.progress.ProgressEntry
import org.getfit.app.progress.latestWeightKg
import org.getfit.app.progress.progressComparison
import org.getfit.app.progress.weightSparkline
import org.getfit.app.settings.Settings
import org.getfit.app.settings.UnitSystem
import org.getfit.app.settings.fromKg
import org.getfit.app.settings.toKg
import org.getfit.app.workout.asDisplayWeight
import java.util.UUID
import kotlin.math.abs

/**
 * Weight and photographs over time.
 *
 * The list is the smaller half of this screen. A body changes on a timescale
 * nobody can see in a mirror, and the thing that makes that visible is two
 * photographs far enough apart — which is what the card at the top is for, and
 * why it is the first thing here rather than a feature buried under a history.
 *
 * Nothing on this screen says whether a change is good. The app does not know
 * whether somebody is cutting, gaining, or holding through a block, and a
 * number wearing a judgement it has not earned is how a tracker becomes an app
 * a person stops opening.
 */
@Composable
fun ProgressScreen(env: AppEnv, settings: Settings) {
    val scope = rememberCoroutineScope()
    val entries by env.progress.entries.collectAsState(initial = emptyList())
    val units = settings.units

    val now = System.currentTimeMillis() / 1000
    val comparison = progressComparison(entries, now)

    // Photographs are written as they are picked, so what is on screen is the
    // file that will be kept. Anything abandoned is collected at next launch.
    val draftPhotos = remember { mutableStateListOf<String>() }
    var weight by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var problem by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_PHOTOS_PER_ENTRY)
    ) { picked: List<Uri> ->
        scope.launch {
            val room = MAX_PHOTOS_PER_ENTRY - draftPhotos.size
            val saved = picked.take(room).mapNotNull { env.progress.photos.save(it) }
            draftPhotos.addAll(saved)
            problem = when {
                saved.size < picked.take(room).size -> "Some photos could not be read."
                picked.size > room -> "Kept $room — that is the limit for one entry."
                else -> null
            }
        }
    }

    // Where the camera is writing, held across the trip out to it and back:
    // the result is only a yes or no, and the frame itself is at this URI.
    var capturing by remember { mutableStateOf<Uri?>(null) }

    val camera = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { taken: Boolean ->
        val target = capturing
        capturing = null
        scope.launch {
            if (!taken || target == null) {
                env.progress.photos.discardCaptures()
                return@launch
            }
            val stored = env.progress.photos.keepCapture(target)
            if (stored == null) {
                problem = "That photo could not be read."
            } else {
                draftPhotos.add(stored)
                problem = null
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                "Progress",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 20.dp),
            )
        }

        if (comparison != null) {
            item { ComparisonCard(env.progress.photos, comparison, units) }
        }

        item {
            WeighInCard(
                photos = env.progress.photos,
                draftPhotos = draftPhotos,
                weight = weight,
                onWeight = { weight = it },
                note = note,
                onNote = { note = it },
                units = units,
                onUnits = { chosen ->
                    scope.launch { env.settingsRepository.setUnits(chosen) }
                },
                problem = problem,
                onAddPhotos = {
                    problem = null
                    picker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onTakePhoto = {
                    problem = null
                    val target = env.progress.photos.newCaptureUri()
                    if (target == null) {
                        problem = "Could not make room for a photo."
                    } else {
                        capturing = target
                        // A phone with no camera app at all resolves nothing
                        // and throws, which is a message rather than a crash.
                        runCatching { camera.launch(target) }.onFailure {
                            capturing = null
                            problem = "No camera app to open."
                        }
                    }
                },
                onRemovePhoto = { name ->
                    scope.launch {
                        draftPhotos.remove(name)
                        env.progress.photos.delete(listOf(name))
                    }
                },
                onSave = {
                    scope.launch {
                        env.progress.add(
                            ProgressEntry(
                                id = UUID.randomUUID().toString(),
                                recordedAt = System.currentTimeMillis() / 1000,
                                weightKg = weight.toDoubleOrNull()?.let { units.toKg(it) },
                                photos = draftPhotos.toList(),
                                note = note.trim(),
                            )
                        )
                        draftPhotos.clear()
                        weight = ""
                        note = ""
                        problem = null
                    }
                },
            )
        }

        val line = weightSparkline(entries)
        if (line.size >= 2) {
            item {
                WeightTrendCard(
                    line = line,
                    latestKg = latestWeightKg(entries),
                    unitSuffix = units.weightSuffix,
                    toDisplay = { units.fromKg(it) },
                )
            }
        }

        if (entries.isEmpty()) {
            item {
                Text(
                    "Nothing recorded yet. A weight, some photographs, or both — " +
                        "the comparison needs a fortnight between two of them before " +
                        "it can show you anything.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        } else {
            item {
                Text(
                    "History",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(entries, key = { it.id }) { entry ->
                EntryRow(
                    photos = env.progress.photos,
                    entry = entry,
                    unitSuffix = units.weightSuffix,
                    toDisplay = { units.fromKg(it) },
                    onDelete = { scope.launch { env.progress.delete(entry.id) } },
                )
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

/**
 * Then and now.
 *
 * Shared with the Today screen, which is where it does its actual job: turning
 * up unasked, on a day somebody was not thinking about it.
 */
@Composable
fun ComparisonCard(
    photos: PhotoStore,
    comparison: Comparison,
    units: UnitSystem,
    title: String = "Then and now",
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, fontWeight = FontWeight.Bold)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(comparison.before, comparison.after).forEach { entry ->
                    Column(modifier = Modifier.weight(1f)) {
                        Photo(
                            photos = photos,
                            name = entry.photos.firstOrNull(),
                            modifier = Modifier.fillMaxWidth().aspectRatio(0.75f),
                        )
                        Text(
                            dayLabel(entry.recordedAt),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        entry.weightKg?.let { kg ->
                            Text(
                                "${units.fromKg(kg).asDisplayWeight()} ${units.weightSuffix}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }

            // The span and the change, and nothing about whether either is
            // good. Down is not a win for somebody adding muscle, and up is not
            // a failure for them either.
            Text(
                text = buildString {
                    val weeks = comparison.days / 7
                    append(if (weeks >= 2) "$weeks weeks apart" else "${comparison.days} days apart")
                    comparison.weightDeltaKg?.let { delta ->
                        val shown = units.fromKg(abs(delta)).asDisplayWeight()
                        val sign = if (delta < 0) "−" else "+"
                        append(" · $sign$shown ${units.weightSuffix}")
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun WeighInCard(
    photos: PhotoStore,
    draftPhotos: List<String>,
    weight: String,
    onWeight: (String) -> Unit,
    note: String,
    onNote: (String) -> Unit,
    units: UnitSystem,
    onUnits: (UnitSystem) -> Unit,
    problem: String?,
    onAddPhotos: () -> Unit,
    onTakePhoto: () -> Unit,
    onRemovePhoto: (String) -> Unit,
    onSave: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Record where you are", fontWeight = FontWeight.Bold)

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = weight,
                    onValueChange = { typed ->
                        onWeight(typed.filter { it.isDigit() || it == '.' }.take(6))
                    },
                    label = { Text("Weight") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                // The same setting the Settings screen holds, offered here
                // because this is where somebody notices it is wrong. It
                // changes what is shown and never what is stored: weights are
                // kilograms underneath, so switching relabels a history rather
                // than rewriting it.
                UnitSystem.entries.forEach { unit ->
                    FilterChip(
                        selected = units == unit,
                        onClick = { onUnits(unit) },
                        label = { Text(unit.weightSuffix) },
                    )
                }
            }

            if (draftPhotos.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(draftPhotos, key = { it }) { name ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Photo(photos, name, Modifier.size(72.dp))
                            TextButton(onClick = { onRemovePhoto(name) }) {
                                Text("Remove", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            val room = draftPhotos.size < MAX_PHOTOS_PER_ENTRY
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onTakePhoto,
                    enabled = room,
                    modifier = Modifier.weight(1f),
                ) { Text("Take a photo") }
                OutlinedButton(
                    onClick = onAddPhotos,
                    enabled = room,
                    modifier = Modifier.weight(1f),
                ) { Text("Choose photos") }
            }

            Text(
                text = if (draftPhotos.isEmpty()) {
                    "Up to $MAX_PHOTOS_PER_ENTRY per entry."
                } else {
                    "${draftPhotos.size} of $MAX_PHOTOS_PER_ENTRY added."
                },
                style = MaterialTheme.typography.labelSmall,
            )

            OutlinedTextField(
                value = note,
                onValueChange = onNote,
                label = { Text("Note") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            problem?.let {
                Text(it, style = MaterialTheme.typography.labelSmall)
            }

            Button(
                // A weight, photographs, or both — but not an entry that
                // records nothing at all.
                enabled = weight.toDoubleOrNull() != null || draftPhotos.isNotEmpty(),
                onClick = onSave,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save") }

            Text(
                "Photos stay on this phone. Nothing here is uploaded or shared.",
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun WeightTrendCard(
    line: List<Float>,
    latestKg: Double?,
    unitSuffix: String,
    toDisplay: (Double) -> Double,
) {
    val accent = MaterialTheme.colorScheme.primary
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
                Text("Weight", fontWeight = FontWeight.Bold)
                latestKg?.let {
                    Text(
                        "${toDisplay(it).asDisplayWeight()} $unitSuffix",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            // Shaped to its own range rather than drawn from zero — bodyweight
            // moves by a percent or two, and from zero every line is flat.
            Canvas(modifier = Modifier.fillMaxWidth().height(64.dp)) {
                if (line.size < 2) return@Canvas
                val step = size.width / (line.size - 1)
                val points = line.mapIndexed { index, value ->
                    Offset(index * step, size.height - value * size.height)
                }
                points.zipWithNext { from, to ->
                    drawLine(
                        color = accent,
                        start = from,
                        end = to,
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
                drawCircle(color = accent, radius = 4.dp.toPx(), center = points.last())
            }

            Text(
                "Oldest to newest, scaled to the range recorded.",
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun EntryRow(
    photos: PhotoStore,
    entry: ProgressEntry,
    unitSuffix: String,
    toDisplay: (Double) -> Double,
    onDelete: () -> Unit,
) {
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
                    Text(dayLabel(entry.recordedAt), fontWeight = FontWeight.Bold)
                    Text(
                        text = entry.weightKg
                            ?.let { "${toDisplay(it).asDisplayWeight()} $unitSuffix" }
                            ?: "Photos only",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (entry.note.isNotBlank()) {
                        Text(entry.note, style = MaterialTheme.typography.labelSmall)
                    }
                }
                TextButton(onClick = onDelete) { Text("Delete") }
            }

            if (entry.photos.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(entry.photos, key = { it }) { name ->
                        Photo(photos, name, Modifier.size(72.dp))
                    }
                }
            }
        }
    }
}

/**
 * One photograph, decoded off the main thread.
 *
 * Decoding a full frame on the main thread is a visible stutter on a list that
 * scrolls, so this starts blank and fills in. There is no image library in this
 * project to do it, and adding one to show a handful of local files would be a
 * dependency earning its keep only here.
 */
@Composable
private fun Photo(photos: PhotoStore, name: String?, modifier: Modifier = Modifier) {
    var image by remember(name) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(name) {
        image = name?.let { photos.decode(it, THUMBNAIL_EDGE)?.asImageBitmap() }
    }

    Box(
        // A filled block rather than nothing while it decodes, so a row does
        // not jump as each photograph arrives.
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        image?.let { bitmap ->
            Image(
                bitmap = bitmap,
                contentDescription = "Progress photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * Wide enough for the comparison card on any phone, small enough that a list
 * of them is not carrying whole camera frames in memory.
 */
private const val THUMBNAIL_EDGE = 600
