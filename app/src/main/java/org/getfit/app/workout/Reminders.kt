package org.getfit.app.workout

import kotlinx.serialization.Serializable
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * When to be reminded to train.
 *
 * The rule is "18:00 on Monday, Wednesday and Friday", not "every 172800
 * seconds": a repeating interval drifts across daylight saving and lands at
 * 17:00 for half the year, which for a reminder to go to the gym is the
 * difference between useful and ignored. So the next occurrence is worked out
 * from a calendar each time, and the alarm is re-armed after every firing.
 *
 * [nextOccurrence] is pure and takes its zone, because the awkward cases — the
 * reminder due later today, the one due next week, the clocks going forward
 * over the chosen time — are exactly the ones worth pinning in a test.
 */
@Serializable
data class ReminderSchedule(
    val enabled: Boolean = false,
    /**
     * Days as [Calendar.MONDAY]…[Calendar.SUNDAY] (1 = Sunday … 7 = Saturday).
     * Calendar's own constants rather than an enum of our own, because this
     * number is handed straight to a [Calendar] and a second numbering to
     * translate between would be a second thing to get wrong.
     */
    val days: Set<Int> = emptySet(),
    val hour: Int = 18,
    val minute: Int = 0,
) {
    /** Enabled but with no days chosen would never fire; treat it as off. */
    val active: Boolean get() = enabled && days.isNotEmpty()

    val timeLabel: String
        get() = String.format(Locale.US, "%02d:%02d", hour, minute)

    companion object {
        /** Monday to Friday, which is the common case worth one tap. */
        val WEEKDAYS = setOf(
            Calendar.MONDAY,
            Calendar.TUESDAY,
            Calendar.WEDNESDAY,
            Calendar.THURSDAY,
            Calendar.FRIDAY,
        )

        val ALL_DAYS = setOf(
            Calendar.MONDAY,
            Calendar.TUESDAY,
            Calendar.WEDNESDAY,
            Calendar.THURSDAY,
            Calendar.FRIDAY,
            Calendar.SATURDAY,
            Calendar.SUNDAY,
        )

        /** Monday first, which is how a training week is read. */
        val ORDERED_DAYS = listOf(
            Calendar.MONDAY to "Mon",
            Calendar.TUESDAY to "Tue",
            Calendar.WEDNESDAY to "Wed",
            Calendar.THURSDAY to "Thu",
            Calendar.FRIDAY to "Fri",
            Calendar.SATURDAY to "Sat",
            Calendar.SUNDAY to "Sun",
        )
    }
}

/**
 * The next moment [schedule] is due after [fromMillis], or null if it never is.
 *
 * Strictly after: called from the receiver that has just fired, so returning
 * "now" would re-arm the alarm for the instant it is already handling and
 * reminders would repeat until the minute was out.
 *
 * Looks eight days ahead rather than seven. Seven is enough to find the same
 * weekday again, but only if today's slot has not already passed — and when it
 * has, the answer is a week tomorrow, which is the eighth day.
 */
fun nextOccurrence(
    schedule: ReminderSchedule,
    fromMillis: Long,
    zone: TimeZone = TimeZone.getDefault(),
): Long? {
    if (!schedule.active) return null
    if (schedule.hour !in 0..23 || schedule.minute !in 0..59) return null

    for (offset in 0..8) {
        val candidate = Calendar.getInstance(zone).apply {
            timeInMillis = fromMillis
            add(Calendar.DAY_OF_YEAR, offset)
            set(Calendar.HOUR_OF_DAY, schedule.hour)
            set(Calendar.MINUTE, schedule.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        // Read the weekday off the candidate rather than off the starting
        // instant, so it stays correct once the offset has crossed midnight.
        if (candidate.get(Calendar.DAY_OF_WEEK) !in schedule.days) continue
        if (candidate.timeInMillis > fromMillis) return candidate.timeInMillis
    }
    return null
}

/**
 * How the next reminder should be described, given the days chosen.
 *
 * Named sets get their name; anything else is listed. "Mon, Wed, Fri" is
 * clearer than "3 days a week", which leaves the reader wondering which.
 */
fun describeDays(days: Set<Int>): String = when {
    days.isEmpty() -> "No days chosen"
    days == ReminderSchedule.ALL_DAYS -> "Every day"
    days == ReminderSchedule.WEEKDAYS -> "Weekdays"
    else -> ReminderSchedule.ORDERED_DAYS
        .filter { (day, _) -> day in days }
        .joinToString(", ") { (_, label) -> label }
}
