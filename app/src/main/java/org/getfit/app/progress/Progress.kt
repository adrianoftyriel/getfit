package org.getfit.app.progress

import kotlinx.serialization.Serializable

/**
 * Weigh-ins and progress photographs.
 *
 * A body changes on a timescale that a person standing in front of a mirror
 * cannot see. Two photographs eight weeks apart can, which is the whole reason
 * this exists and the reason the comparison below is the interesting part
 * rather than the list.
 *
 * The photographs themselves are files under the app's own storage and never
 * leave the phone: there is no upload, no backup and no sharing, because the
 * app has no server and nothing here belongs anywhere else. What is held in the
 * document is their file names.
 */

/** One weigh-in: a number, some photographs, or both. */
@Serializable
data class ProgressEntry(
    val id: String,
    /** Epoch seconds, as everything else in this app records time. */
    val recordedAt: Long,
    /**
     * Kilograms, like every other weight here — display converts. Null rather
     * than zero when only photographs were taken, because a weigh-in that did
     * not happen is not a weight of nothing.
     */
    val weightKg: Double? = null,
    /** File names under the photo directory, in the order they were added. */
    val photos: List<String> = emptyList(),
    val note: String = "",
)

@Serializable
data class ProgressLog(val entries: List<ProgressEntry> = emptyList())

/**
 * The cap the user asked for.
 *
 * Five is enough for the angles that make a comparison honest — front, back,
 * each side, one spare — and few enough that a year of weekly entries is still
 * a sane amount of storage on a phone.
 */
const val MAX_PHOTOS_PER_ENTRY = 5

const val SECONDS_PER_DAY = 86_400L

/**
 * How far apart two photographs have to be before comparing them means
 * anything.
 *
 * A fortnight, because below that the difference between two photographs is
 * lighting, posture, water and the time of day. Showing that as progress would
 * be showing noise and calling it a result, which is worse than showing
 * nothing — it teaches someone to distrust the one view that was supposed to be
 * more honest than the mirror.
 */
const val MIN_COMPARISON_DAYS = 14

/**
 * A before and an after, with the arithmetic between them.
 *
 * Deliberately says nothing about whether the change is good. The app does not
 * know whether somebody is cutting, gaining, or holding steady through a
 * training block, and a number wearing a judgement it has not earned is how a
 * tracker turns into something a person avoids opening.
 */
data class Comparison(
    val before: ProgressEntry,
    val after: ProgressEntry,
) {
    val days: Int get() = ((after.recordedAt - before.recordedAt) / SECONDS_PER_DAY).toInt()

    /** Null unless both ends were weighed: a change needs two numbers. */
    val weightDeltaKg: Double?
        get() {
            val from = before.weightKg ?: return null
            val to = after.weightKg ?: return null
            return to - from
        }
}

/**
 * The before-and-after to show, or null when there is not one worth showing.
 *
 * The *after* is always the most recent entry with a photograph. The *before*
 * rotates, by the day, through every entry old enough to be worth comparing
 * against — so the card is a different pair this week from last, rather than
 * one image that stops being looked at by the third day.
 *
 * Returns null rather than something weaker when there is only one photograph,
 * or when everything was taken inside [MIN_COMPARISON_DAYS]. Nothing is the
 * honest answer at that point.
 */
fun progressComparison(
    entries: List<ProgressEntry>,
    now: Long,
    minDays: Int = MIN_COMPARISON_DAYS,
): Comparison? {
    val withPhotos = entries.filter { it.photos.isNotEmpty() }.sortedBy { it.recordedAt }
    val after = withPhotos.lastOrNull() ?: return null
    val cutoff = after.recordedAt - minDays * SECONDS_PER_DAY
    val candidates = withPhotos.filter { it.recordedAt <= cutoff }
    if (candidates.isEmpty()) return null
    // `mod` rather than `%`: the remainder of a negative is negative, and an
    // index of -2 into this list would take the screen down.
    return Comparison(before = candidates[(now / SECONDS_PER_DAY).mod(candidates.size)], after = after)
}

/**
 * The power-of-two shrink factor that brings the longer edge of an image under
 * [maxEdge], for decoding a photograph without holding the whole thing.
 *
 * Here rather than beside the decoder because of the loop: a maximum of zero or
 * less would never be reached, and an infinite loop inside an image decode is a
 * frozen app rather than a crash with a stack trace. Guarded, and pinned below.
 */
fun sampleSize(width: Int, height: Int, maxEdge: Int): Int {
    if (maxEdge <= 0 || width <= 0 || height <= 0) return 1
    var sample = 1
    while (maxOf(width, height) / sample > maxEdge) sample *= 2
    return sample
}

/** The most recent weight recorded, ignoring entries that were photos only. */
fun latestWeightKg(entries: List<ProgressEntry>): Double? =
    entries.filter { it.weightKg != null }.maxByOrNull { it.recordedAt }?.weightKg

/**
 * Weights normalised into 0..1 for a sparkline, oldest first.
 *
 * The line is shaped to whatever range is actually in it, because bodyweight
 * moves by a percent or two and a line drawn from zero would be a flat line
 * every time. A run of identical weights sits down the middle rather than
 * dividing by nothing.
 */
fun weightSparkline(entries: List<ProgressEntry>): List<Float> {
    val weights = entries
        .filter { it.weightKg != null }
        .sortedBy { it.recordedAt }
        .map { it.weightKg!! }
    if (weights.size < 2) return emptyList()
    val low = weights.min()
    val high = weights.max()
    if (high == low) return weights.map { 0.5f }
    return weights.map { ((it - low) / (high - low)).toFloat() }
}
