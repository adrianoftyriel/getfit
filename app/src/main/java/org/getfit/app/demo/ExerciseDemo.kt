package org.getfit.app.demo

import kotlinx.serialization.Serializable

/**
 * How an exercise is performed, as published by somebody else.
 *
 * **Nothing in here is written by this app or by the assistant that built it.**
 * Form guidance is the one thing in a training app that can injure somebody if
 * it is invented, so the app carries none of its own: every instruction shown
 * is text fetched verbatim from [attribution], and an exercise with no entry in
 * that source shows nothing rather than something plausible.
 *
 * That is also why [attribution] is a constructor parameter and not a constant
 * somewhere in the UI. A demonstration cannot exist in this app without saying
 * where it came from — the type will not let it.
 */
@Serializable
data class ExerciseDemo(
    /** The id in the upstream dataset, which is also the cache key. */
    val sourceId: String,
    val name: String,
    /** Verbatim from the source. Never edited, never summarised, never added to. */
    val instructions: List<String>,
    /**
     * The frames of the movement, in order — start position then end position.
     * Two, in the dataset as it stands; the renderer does not assume two.
     */
    val frameUrls: List<String>,
    val primaryMuscles: List<String> = emptyList(),
    val secondaryMuscles: List<String> = emptyList(),
    val level: String = "",
    val equipment: String = "",
    val attribution: Attribution,
) {
    /** A single frame cannot be animated, and neither can none. */
    val animatable: Boolean get() = frameUrls.size >= 2
}

/** Where a demonstration came from, and under what terms. */
@Serializable
data class Attribution(
    val sourceName: String,
    val sourceUrl: String,
    val licence: String,
    /**
     * What the source actually is, in a sentence the reader can weigh.
     *
     * Stated rather than implied because the honest description is not
     * "expert-reviewed": see [DemoSource.FREE_EXERCISE_DB].
     */
    val provenance: String,
)

/**
 * The one source the app reads demonstrations from.
 *
 * Kept as data rather than scattered through the fetch code so that adding a
 * second source later is adding a value here, and so the attribution shown on
 * screen cannot drift from the URL actually fetched.
 */
object DemoSource {

    val FREE_EXERCISE_DB = Attribution(
        sourceName = "Free Exercise DB",
        sourceUrl = "https://github.com/yuhonas/free-exercise-db",
        licence = "Unlicense (public domain dedication)",
        // Deliberately not dressed up. The dataset is a re-packaging of
        // wrkout/exercises.json, which is itself compiled from a commercial
        // fitness site's exercise library. It is a real, citable, widely used
        // dataset and it is not a clinical or professional body, and a reader
        // deciding whether to trust a cue about their spine should be told
        // which of those they are looking at.
        provenance = "Community-compiled open dataset, not clinically reviewed. " +
            "Check anything that affects your safety against a qualified coach.",
    )

    private const val RAW_ROOT =
        "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main"

    /** The whole dataset, fetched once and cached. About 1 MB. */
    const val INDEX_URL = "$RAW_ROOT/dist/exercises.json"

    /** Frame paths in the dataset are relative to this. */
    fun frameUrl(path: String): String = "$RAW_ROOT/exercises/$path"
}
