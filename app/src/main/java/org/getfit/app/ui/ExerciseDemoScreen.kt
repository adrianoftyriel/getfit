package org.getfit.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.getfit.app.demo.ExerciseDemo
import org.getfit.app.workout.ExerciseCatalog

/**
 * How to perform an exercise.
 *
 * Everything on this screen except the labels comes from the dataset named at
 * the bottom of it. The app writes no form guidance of its own — see
 * [org.getfit.app.demo.ExerciseDemo] for why — so when there is no entry, this
 * says so instead of describing the movement.
 *
 * The "animation" is honest about what it is: the source publishes two
 * photographs, the start and end of the movement, and this cross-fades between
 * them on a loop. That shows the path of the movement, which is what somebody
 * checking their form needs, and it does not pretend to be video.
 */
@Composable
fun ExerciseDemoScreen(env: AppEnv, exerciseId: String, onBack: () -> Unit) {
    val exercise = ExerciseCatalog.byId(exerciseId)
    var demo by remember { mutableStateOf<ExerciseDemo?>(null) }
    var loading by remember { mutableStateOf(true) }
    var animate by remember { mutableStateOf(true) }

    LaunchedEffect(exerciseId) {
        loading = true
        demo = env.demos.demoFor(exerciseId)
        loading = false
        // Pull the frames down in the background so this exercise still works
        // in a basement next week.
        demo?.let { env.demos.cacheFrames(it) }
    }

    ScreenScaffold(title = exercise?.name ?: "Exercise", onBack = onBack) { modifier ->
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val current = demo

            when {
                loading -> item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }
                }

                current == null -> item { NoDemo() }

                else -> {
                    item {
                        DemoFrames(
                            env = env,
                            demo = current,
                            animate = animate,
                        )
                    }

                    if (current.animatable) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("Animate", style = MaterialTheme.typography.bodyMedium)
                                Switch(checked = animate, onCheckedChange = { animate = it })
                            }
                        }
                    }

                    item { DemoFacts(current) }

                    if (current.instructions.isNotEmpty()) {
                        item {
                            Text(
                                "Instructions",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        itemsIndexedInstructions(current.instructions)
                    }

                    item { AttributionCard(current) }
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

/** Numbered steps, verbatim. */
private fun LazyListScope.itemsIndexedInstructions(steps: List<String>) {
    itemsIndexed(steps) { index, step ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "${index + 1}.",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(step, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/**
 * The frames, cross-faded.
 *
 * Held in a fixed-ratio box so the card does not resize as each frame arrives,
 * which would otherwise make the whole list jump while it loads.
 */
@Composable
private fun DemoFrames(env: AppEnv, demo: ExerciseDemo, animate: Boolean) {
    var frames by remember(demo.sourceId) { mutableStateOf<List<ImageBitmap>>(emptyList()) }
    var failed by remember(demo.sourceId) { mutableStateOf(false) }

    LaunchedEffect(demo.sourceId) {
        val loaded = demo.frameUrls.mapNotNull { env.demos.frame(it) }
        frames = loaded
        failed = loaded.isEmpty()
    }

    // A slow cross-fade rather than a cut: the two photographs are the ends of
    // one movement, and fading between them reads as the movement where a hard
    // switch reads as a flicker.
    val transition = rememberInfiniteTransition(label = "demo")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "phase",
    )

    Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.5f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            when {
                failed -> Text(
                    "The demonstration images could not be loaded. " +
                        "They need a connection the first time.",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(24.dp),
                )

                frames.isEmpty() -> CircularProgressIndicator()

                else -> {
                    // Stacked, with the second faded over the first. With one
                    // frame the alpha is pinned at 0 and it simply shows.
                    Image(
                        bitmap = frames[0],
                        contentDescription = "${demo.name}, start position",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                    frames.getOrNull(1)?.let { second ->
                        Image(
                            bitmap = second,
                            contentDescription = "${demo.name}, end position",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .alpha(if (animate) phase else 0f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DemoFacts(demo: ExerciseDemo) {
    val facts = buildList {
        demo.level.takeIf { it.isNotBlank() }?.let { add("Level" to it.replaceFirstChar(Char::uppercase)) }
        demo.equipment.takeIf { it.isNotBlank() }?.let { add("Equipment" to it) }
        demo.primaryMuscles.takeIf { it.isNotEmpty() }?.let { add("Primary" to it.joinToString(", ")) }
        demo.secondaryMuscles.takeIf { it.isNotEmpty() }?.let { add("Secondary" to it.joinToString(", ")) }
    }
    if (facts.isEmpty()) return

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            facts.forEach { (label, value) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(label, style = MaterialTheme.typography.labelMedium)
                    Text(
                        value.replaceFirstChar(Char::uppercase),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

/**
 * Where this came from, and what it is worth.
 *
 * On the screen rather than buried in an about page, because the honest
 * description of the source — a community-compiled dataset, not clinically
 * reviewed — is exactly what somebody needs when they are deciding how much to
 * trust a cue about their back.
 */
@Composable
private fun AttributionCard(demo: ExerciseDemo) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Source", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
            HorizontalDivider()
            Text(demo.attribution.sourceName, style = MaterialTheme.typography.bodyMedium)
            Text(demo.attribution.provenance, style = MaterialTheme.typography.bodySmall)
            Text(
                "${demo.attribution.licence} · ${demo.attribution.sourceUrl}",
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun NoDemo() {
    Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("No demonstration for this exercise", fontWeight = FontWeight.Bold)
            Text(
                "GetFit only shows form guidance published by an outside source, " +
                    "and there is no entry for this movement. Nothing is shown rather " +
                    "than something invented.",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "If this is the first time you have opened a demonstration, it may " +
                    "just need a connection.",
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
