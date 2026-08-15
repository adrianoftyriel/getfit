package org.getfit.app.settings

import kotlin.math.roundToInt

/**
 * What weights and distances are shown in, and the arithmetic to get there.
 *
 * Apart from [SettingsRepository] because none of it is Android: this is the
 * seam every stored number crosses on its way to a screen and back, and it is
 * the sort of thing that fails quietly — a factor the wrong way round reads as
 * a plausible number, and a training history that changed value when a setting
 * was flipped would not announce itself either. Here it can be tested; in a
 * file that imports `Context` it could not be.
 */

/**
 * Which units weights and distances are shown in.
 *
 * **Storage is always metric.** Every weight in the model is kilograms and
 * every distance is metres; this converts at the edge, on the way to a field
 * and back off it. Storing whatever the user last had selected would mean a
 * training history that changed value when the setting was flipped.
 */
enum class UnitSystem(
    val label: String,
    val weightSuffix: String,
    val distanceSuffix: String,
) {
    METRIC("Metric", "kg", "km"),
    IMPERIAL("Imperial", "lb", "mi"),
}

/** Exactly, by definition of the international pound. */
const val KG_PER_LB = 0.45359237

/** Exactly, by definition of the international mile. */
const val METRES_PER_MILE = 1609.344

/** Kilograms out of storage, into whatever is being shown. */
fun UnitSystem.fromKg(kg: Double): Double = when (this) {
    UnitSystem.METRIC -> kg
    UnitSystem.IMPERIAL -> kg / KG_PER_LB
}

/** Whatever was typed, back into the kilograms everything is stored in. */
fun UnitSystem.toKg(value: Double): Double = when (this) {
    UnitSystem.METRIC -> value
    UnitSystem.IMPERIAL -> value * KG_PER_LB
}

/**
 * Metres out of storage, into kilometres or miles.
 *
 * Same arrangement as weights, for the same reason: one base unit everywhere,
 * converted at the edge, so switching the setting changes what a run is
 * displayed as and never what it was.
 */
fun UnitSystem.fromMetres(metres: Int): Double = when (this) {
    UnitSystem.METRIC -> metres / 1000.0
    UnitSystem.IMPERIAL -> metres / METRES_PER_MILE
}

/**
 * Whatever was dialled or typed, back into stored metres.
 *
 * Rounded rather than truncated: a dial detent of a tenth of a mile is 160.9344
 * metres, and dropping the fraction every time would walk a distance downwards
 * a little on every edit.
 */
fun UnitSystem.toMetres(value: Double): Int = when (this) {
    UnitSystem.METRIC -> (value * 1000.0).roundToInt()
    UnitSystem.IMPERIAL -> (value * METRES_PER_MILE).roundToInt()
}
