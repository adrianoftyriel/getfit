package org.getfit.app.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * Working out when the next reminder is due.
 *
 * A reminder that fires at the wrong time is annoying; one that silently never
 * fires again is the real failure, because nothing appears to be wrong. Both
 * are decided here.
 */
class RemindersTest {

    private val london = TimeZone.getTimeZone("Europe/London")

    /** A moment, in a named zone, without depending on the machine's own. */
    private fun at(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        zone: TimeZone = london,
    ): Long = Calendar.getInstance(zone).apply {
        clear()
        set(year, month - 1, day, hour, minute, 0)
    }.timeInMillis

    private fun fieldsOf(millis: Long, zone: TimeZone = london): String =
        Calendar.getInstance(zone).apply { timeInMillis = millis }.let {
            "%04d-%02d-%02d %02d:%02d".format(
                it.get(Calendar.YEAR),
                it.get(Calendar.MONTH) + 1,
                it.get(Calendar.DAY_OF_MONTH),
                it.get(Calendar.HOUR_OF_DAY),
                it.get(Calendar.MINUTE),
            )
        }

    private val mwf = ReminderSchedule(
        enabled = true,
        days = setOf(Calendar.MONDAY, Calendar.WEDNESDAY, Calendar.FRIDAY),
        hour = 18,
        minute = 0,
    )

    // -- Nothing scheduled ---------------------------------------------------

    @Test
    fun `a disabled schedule never fires`() {
        assertNull(nextOccurrence(mwf.copy(enabled = false), at(2026, 8, 17, 9, 0), london))
    }

    @Test
    fun `enabled with no days never fires`() {
        // Would otherwise loop eight days and find nothing, which is the same
        // answer but only by accident. Stated explicitly instead.
        assertNull(nextOccurrence(mwf.copy(days = emptySet()), at(2026, 8, 17, 9, 0), london))
        assertTrue(!mwf.copy(days = emptySet()).active)
    }

    @Test
    fun `an impossible time is refused rather than fired at random`() {
        assertNull(nextOccurrence(mwf.copy(hour = 24), at(2026, 8, 17, 9, 0), london))
        assertNull(nextOccurrence(mwf.copy(minute = 60), at(2026, 8, 17, 9, 0), london))
    }

    // -- The ordinary cases --------------------------------------------------

    @Test
    fun `later today counts when today is a chosen day`() {
        // Monday 17 August 2026, 09:00 → 18:00 the same day.
        val next = nextOccurrence(mwf, at(2026, 8, 17, 9, 0), london)!!
        assertEquals("2026-08-17 18:00", fieldsOf(next))
    }

    @Test
    fun `once today's slot has passed it moves to the next chosen day`() {
        // Monday 19:00 → Wednesday.
        val next = nextOccurrence(mwf, at(2026, 8, 17, 19, 0), london)!!
        assertEquals("2026-08-19 18:00", fieldsOf(next))
    }

    @Test
    fun `a day that is not chosen is skipped`() {
        // Tuesday morning → Wednesday, not Tuesday.
        val next = nextOccurrence(mwf, at(2026, 8, 18, 7, 0), london)!!
        assertEquals("2026-08-19 18:00", fieldsOf(next))
    }

    @Test
    fun `the last chosen day of the week wraps to the first of the next`() {
        // Friday 19:00 → the following Monday.
        val next = nextOccurrence(mwf, at(2026, 8, 21, 19, 0), london)!!
        assertEquals("2026-08-24 18:00", fieldsOf(next))
    }

    /**
     * The case the eight-day loop exists for: one chosen day, and its slot has
     * already gone. Seven days of lookahead lands back on the same instant that
     * has just passed, and returns nothing.
     */
    @Test
    fun `a single weekly reminder that has just passed lands a week later`() {
        val weekly = mwf.copy(days = setOf(Calendar.MONDAY))
        val next = nextOccurrence(weekly, at(2026, 8, 17, 18, 30), london)!!
        assertEquals("2026-08-24 18:00", fieldsOf(next))
    }

    // -- Firing again --------------------------------------------------------

    /**
     * The receiver calls this at the moment the alarm fires, so "now" must not
     * come back — the alarm would be re-armed for the instant it is handling
     * and the reminder would repeat for the rest of the minute.
     */
    @Test
    fun `the moment it fires is not offered again`() {
        val fired = at(2026, 8, 17, 18, 0)
        val next = nextOccurrence(mwf, fired, london)!!
        assertTrue("re-armed for the instant it just handled", next > fired)
        assertEquals("2026-08-19 18:00", fieldsOf(next))
    }

    @Test
    fun `a second past the slot still moves on rather than firing twice`() {
        val next = nextOccurrence(mwf, at(2026, 8, 17, 18, 0) + 1_000, london)!!
        assertEquals("2026-08-19 18:00", fieldsOf(next))
    }

    // -- Clocks changing -----------------------------------------------------

    /**
     * Why this works off a calendar rather than adding a fixed interval. The UK
     * clocks go back on 25 October 2026; a reminder set for 18:00 must still be
     * at 18:00 afterwards, not 17:00.
     */
    @Test
    fun `the wall clock time survives the clocks going back`() {
        val daily = mwf.copy(days = ReminderSchedule.ALL_DAYS)
        // Saturday 24 October, after the slot. Next is Sunday, the day the
        // clocks change.
        val next = nextOccurrence(daily, at(2026, 10, 24, 19, 0), london)!!
        assertEquals("2026-10-25 18:00", fieldsOf(next))
    }

    @Test
    fun `the wall clock time survives the clocks going forward`() {
        val daily = mwf.copy(days = ReminderSchedule.ALL_DAYS)
        // Clocks forward on 29 March 2026.
        val next = nextOccurrence(daily, at(2026, 3, 28, 19, 0), london)!!
        assertEquals("2026-03-29 18:00", fieldsOf(next))
    }

    @Test
    fun `an early morning reminder is unaffected by a zone with no transition`() {
        val utc = TimeZone.getTimeZone("UTC")
        val early = ReminderSchedule(
            enabled = true,
            days = setOf(Calendar.SATURDAY),
            hour = 6,
            minute = 30,
        )
        val next = nextOccurrence(early, at(2026, 8, 17, 9, 0, utc), utc)!!
        assertEquals("2026-08-22 06:30", fieldsOf(next, utc))
    }

    // -- How it reads --------------------------------------------------------

    @Test
    fun `named day sets get their name`() {
        assertEquals("Every day", describeDays(ReminderSchedule.ALL_DAYS))
        assertEquals("Weekdays", describeDays(ReminderSchedule.WEEKDAYS))
        assertEquals("No days chosen", describeDays(emptySet()))
    }

    @Test
    fun `anything else is listed, Monday first`() {
        assertEquals("Mon, Wed, Fri", describeDays(mwf.days))
        // Declaration order, not the order they went into the set.
        assertEquals(
            "Tue, Sun",
            describeDays(setOf(Calendar.SUNDAY, Calendar.TUESDAY)),
        )
    }

    @Test
    fun `the time reads with a leading zero`() {
        assertEquals("06:05", ReminderSchedule(hour = 6, minute = 5).timeLabel)
        assertEquals("18:00", mwf.timeLabel)
    }
}
