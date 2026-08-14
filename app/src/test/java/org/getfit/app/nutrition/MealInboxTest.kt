package org.getfit.app.nutrition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

/**
 * Not importing the same meal twice.
 *
 * The app cannot write to the inbox — it holds no token and the repository is
 * read-only to it — so an imported meal stays published and every poll offers
 * it again. Deduplication is entirely local, which makes this the only thing
 * standing between one lunch and a week of duplicates.
 */
class MealInboxTest {

    private fun meal(id: String, at: Long, calories: Double = 500.0) = MealEntry(
        id = id,
        capturedAt = at,
        label = "Meal $id",
        totals = Nutrition(calories = calories, proteinG = 30.0, carbsG = 50.0, fatG = 15.0),
    )

    private val breakfast = meal("a", 1_723_600_000L)
    private val lunch = meal("b", 1_723_620_000L)
    private val dinner = meal("c", 1_723_640_000L)

    @Test
    fun `a meal already imported is not offered again`() {
        val fresh = selectNew(listOf(breakfast, lunch), seenIds = setOf("a"))
        assertEquals(listOf(lunch), fresh)
    }

    @Test
    fun `an inbox where everything has been seen yields nothing`() {
        assertTrue(selectNew(listOf(breakfast, lunch), setOf("a", "b")).isEmpty())
    }

    @Test
    fun `a duplicate id inside one batch is still one meal`() {
        // The same file listed twice, or two files carrying the same entry —
        // either way it is one meal and must be filed once.
        val fresh = selectNew(listOf(breakfast, breakfast.copy(label = "Again")), emptySet())
        assertEquals(1, fresh.size)
        assertEquals("a", fresh.single().id)
    }

    @Test
    fun `new meals come back newest first`() {
        val fresh = selectNew(listOf(breakfast, dinner, lunch), emptySet())
        assertEquals(listOf("c", "b", "a"), fresh.map { it.id })
    }

    @Test
    fun `an empty inbox is not an error`() {
        assertTrue(selectNew(emptyList(), setOf("a")).isEmpty())
    }

    // -- Grouping into days -------------------------------------------------

    @Test
    fun `meals fall into the day they were eaten, in a fixed zone`() {
        val utc = TimeZone.getTimeZone("UTC")
        // 2024-08-14 22:00 UTC and 2024-08-15 01:00 UTC: different days.
        val late = meal("late", 1_723_672_800L)
        val early = meal("early", 1_723_683_600L)

        val days = groupByDay(listOf(late, early), utc)
        assertEquals(2, days.size)
        // Most recent day first.
        assertEquals(listOf("early", "late"), days.map { it.meals.single().id })
    }

    @Test
    fun `a day totals its meals`() {
        val utc = TimeZone.getTimeZone("UTC")
        val a = meal("a", 1_723_600_000L, calories = 400.0)
        val b = meal("b", 1_723_601_000L, calories = 650.0)

        val day = groupByDay(listOf(a, b), utc).single()
        assertEquals(1050, day.totals.displayCalories)
        assertEquals(60.0, day.totals.proteinG, 0.001)
    }

    /**
     * The reason [dayKey] takes a zone at all: the same instant is two different
     * days depending on where the phone is, and a test that could not pin it
     * would pass in one place and fail in another.
     */
    @Test
    fun `the day depends on the zone it is asked about`() {
        val instant = 1_723_672_800L // 2024-08-14 22:00 UTC
        assertEquals("2024-08-14", dayKey(instant, TimeZone.getTimeZone("UTC")))
        assertEquals("2024-08-15", dayKey(instant, TimeZone.getTimeZone("Australia/Sydney")))
    }

    // -- Totals that disagree with themselves -------------------------------

    @Test
    fun `an itemised meal whose parts do not add up is flagged`() {
        val entry = MealEntry(
            id = "x",
            capturedAt = 1_723_600_000L,
            label = "Mystery plate",
            items = listOf(
                MealItem("Rice", nutrition = Nutrition(calories = 200.0)),
                MealItem("Beans", nutrition = Nutrition(calories = 150.0)),
            ),
            // Nowhere near the 350 the items come to.
            totals = Nutrition(calories = 900.0),
        )
        assertTrue(!entry.itemsAgreeWithTotals)
        assertEquals(350, entry.derivedTotals.displayCalories)
    }

    @Test
    fun `a small disagreement is within what an estimate from a photo means`() {
        val entry = MealEntry(
            id = "x",
            capturedAt = 1_723_600_000L,
            label = "Plate",
            items = listOf(MealItem("Rice", nutrition = Nutrition(calories = 480.0))),
            totals = Nutrition(calories = 500.0),
        )
        assertTrue("4% apart should not be flagged", entry.itemsAgreeWithTotals)
    }

    @Test
    fun `a meal with no items has nothing to disagree with`() {
        assertTrue(meal("a", 1L).itemsAgreeWithTotals)
    }
}
