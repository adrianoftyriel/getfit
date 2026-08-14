package org.getfit.app.nutrition

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Grouping meals into days.
 *
 * `java.time` would be the obvious tool and is deliberately not used: it needs
 * API 26 and minSdk is 24, and turning on desugaring to format a date is a
 * build-wide change to avoid one class.
 *
 * The time zone is a parameter with a sensible default rather than read from
 * the system inside the function, because "which day was that meal on" is
 * exactly the question that goes wrong at a zone boundary — a test that cannot
 * pin the zone is a test that passes in London and fails in Sydney.
 */

/** The day a moment falls in, as `yyyy-MM-dd`, which sorts correctly as text. */
fun dayKey(epochSeconds: Long, zone: TimeZone = TimeZone.getDefault()): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US)
        .apply { timeZone = zone }
        .format(Date(epochSeconds * 1000))

/** `Thu 14 Aug`, for a heading. */
fun dayLabel(epochSeconds: Long, zone: TimeZone = TimeZone.getDefault()): String =
    SimpleDateFormat("EEE d MMM", Locale.getDefault())
        .apply { timeZone = zone }
        .format(Date(epochSeconds * 1000))

/** `19:40`, for a row. */
fun timeLabel(epochSeconds: Long, zone: TimeZone = TimeZone.getDefault()): String =
    SimpleDateFormat("HH:mm", Locale.getDefault())
        .apply { timeZone = zone }
        .format(Date(epochSeconds * 1000))

/**
 * Meals gathered into days, most recent day first and each day's meals newest
 * first within it.
 */
fun groupByDay(
    meals: List<MealEntry>,
    zone: TimeZone = TimeZone.getDefault(),
): List<DayTotals> =
    meals.groupBy { dayKey(it.capturedAt, zone) }
        .map { (day, entries) -> DayTotals(day, entries.sortedByDescending { it.capturedAt }) }
        .sortedByDescending { it.date }
