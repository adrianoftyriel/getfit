package org.getfit.app.nutrition

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * What a meal estimated from a photograph looks like on the wire.
 *
 * This is the contract between the `meal-photo` skill and the app, so it is
 * versioned and it is deliberately dull: plain numbers, no units to guess at,
 * nothing computed on one side that the other also computes. Everything is
 * grams except [Nutrition.calories], which is kilocalories, and
 * [Nutrition.sodiumMg], which says so in its name.
 *
 * The skill writes exactly this shape, both into the repository inbox and into
 * a `getfit://` link. See `.claude/skills/meal-photo/SKILL.md`.
 */
@Serializable
data class MealEntry(
    /**
     * Stable identity, assigned by whoever created the entry.
     *
     * This is what stops a meal being imported twice. The app never writes to
     * the inbox — it has no token and the repository is public read-only — so
     * an imported meal stays in the inbox forever, and every poll sees it
     * again. Dedupe is by this id and nothing else, which is why it must not be
     * derived from anything that could change between polls.
     */
    val id: String,

    /** When the meal was eaten, as epoch seconds UTC. */
    val capturedAt: Long,

    /** A short human name: "Chicken burrito bowl". */
    val label: String,

    /** The components the estimate was built from, which is what makes it checkable. */
    val items: List<MealItem> = emptyList(),

    /** The estimate for the meal as a whole. */
    val totals: Nutrition,

    /**
     * How much the estimate should be trusted.
     *
     * A photograph cannot show what is under the sauce or how much oil went in
     * the pan, and an estimate that does not admit that is worse than one that
     * does. Carried through to the UI rather than dropped on import.
     */
    val confidence: Confidence = Confidence.MEDIUM,

    /** Anything the estimate depended on assuming. Shown with the meal. */
    val notes: String = "",

    /** Where the entry came from, for the log. */
    val source: String = SOURCE_SKILL,

    /** Wire format version, so a future change can be recognised rather than misread. */
    @SerialName("schema") val schemaVersion: Int = SCHEMA_VERSION,
) {
    /**
     * The totals implied by adding the items up.
     *
     * Kept separate from [totals] rather than replacing it: the skill estimates
     * the meal as a whole *and* itemises it, and the two disagreeing is a real
     * signal about the estimate rather than an error to paper over. See
     * [itemsAgreeWithTotals].
     */
    val derivedTotals: Nutrition
        get() = items.fold(Nutrition.ZERO) { sum, item -> sum + item.nutrition }

    /**
     * Whether the itemised numbers add up to the stated totals, within a
     * tolerance that reflects what this measurement actually is.
     *
     * Ten per cent, because these are estimates from a photograph — holding
     * them to the calorie would be pretending to a precision nothing here has.
     * An un-itemised meal has nothing to disagree with, so it passes.
     */
    val itemsAgreeWithTotals: Boolean
        get() = items.isEmpty() || run {
            val implied = derivedTotals.calories
            val stated = totals.calories
            if (stated <= 0) implied <= 0
            else abs(implied - stated).toDouble() / stated <= TOTALS_TOLERANCE
        }

    companion object {
        const val SCHEMA_VERSION = 1
        const val SOURCE_SKILL = "skill"
        const val SOURCE_MANUAL = "manual"
        const val TOTALS_TOLERANCE = 0.10
    }
}

/** One component of a meal. */
@Serializable
data class MealItem(
    val name: String,
    /** As described rather than as measured: "1 cup", "about half a chicken breast". */
    val quantity: String = "",
    /** Estimated mass, when there is one worth stating. */
    val grams: Double? = null,
    val nutrition: Nutrition,
)

/**
 * The numbers themselves.
 *
 * Doubles rather than Ints because these get added and scaled, and rounding at
 * every step would drift. Rounding happens once, at the point of display.
 */
@Serializable
data class Nutrition(
    /** Kilocalories — what everybody means by "calories". */
    val calories: Double = 0.0,
    val proteinG: Double = 0.0,
    val carbsG: Double = 0.0,
    val fatG: Double = 0.0,
    val fibreG: Double = 0.0,
    val sugarG: Double = 0.0,
    val sodiumMg: Double = 0.0,
) {
    operator fun plus(other: Nutrition) = Nutrition(
        calories = calories + other.calories,
        proteinG = proteinG + other.proteinG,
        carbsG = carbsG + other.carbsG,
        fatG = fatG + other.fatG,
        fibreG = fibreG + other.fibreG,
        sugarG = sugarG + other.sugarG,
        sodiumMg = sodiumMg + other.sodiumMg,
    )

    /**
     * Calories implied by the macros, at 4/4/9 kcal per gram.
     *
     * A sanity check on an estimate rather than a replacement for [calories]:
     * the two coming out far apart means the macros and the calorie figure were
     * not estimated from the same meal.
     */
    val caloriesFromMacros: Double
        get() = proteinG * 4 + carbsG * 4 + fatG * 9

    /** Rounded for display, where fractions of a calorie are noise. */
    val displayCalories: Int get() = calories.roundToInt()

    companion object {
        val ZERO = Nutrition()
    }
}

enum class Confidence(val label: String, val blurb: String) {
    LOW("Low estimate", "Hard to read from the photo — treat as a rough guide"),
    MEDIUM("Estimate", "Reasonable read, but portions are inferred"),
    HIGH("Confident", "Clear photo, or the portions were stated"),
}

/** A day's worth of meals, which is the unit the tracking screen works in. */
data class DayTotals(val date: String, val meals: List<MealEntry>) {
    val totals: Nutrition = meals.fold(Nutrition.ZERO) { sum, meal -> sum + meal.totals }
}
