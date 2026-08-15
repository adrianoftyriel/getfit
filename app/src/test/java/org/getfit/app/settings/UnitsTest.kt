package org.getfit.app.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The seam every stored number crosses on its way to a screen.
 *
 * A conversion factor applied the wrong way round does not throw or look
 * obviously wrong — it produces a plausible number, in a field where 60 and 132
 * are both believable, and it would be found weeks later by somebody wondering
 * why their deadlift halved. So both directions are pinned here, and so is the
 * round trip, which is what actually has to hold: a weight typed, stored,
 * shown, and typed again must still be the same weight.
 */
class UnitsTest {

    // -- Weight ---------------------------------------------------------------

    @Test
    fun `metric stores exactly what it shows`() {
        assertEquals(100.0, UnitSystem.METRIC.fromKg(100.0), 0.0001)
        assertEquals(100.0, UnitSystem.METRIC.toKg(100.0), 0.0001)
    }

    @Test
    fun `a hundred kilos is two hundred and twenty pounds`() {
        assertEquals(220.462, UnitSystem.IMPERIAL.fromKg(100.0), 0.001)
        assertEquals(45.359, UnitSystem.IMPERIAL.toKg(100.0), 0.001)
    }

    @Test
    fun `a weight survives the round trip in either system`() {
        UnitSystem.entries.forEach { units ->
            val shown = units.fromKg(142.5)
            assertEquals("$units", 142.5, units.toKg(shown), 0.0001)
        }
    }

    // -- Distance -------------------------------------------------------------

    @Test
    fun `metric distance is metres over a thousand`() {
        assertEquals(5.0, UnitSystem.METRIC.fromMetres(5000), 0.0001)
        assertEquals(5000, UnitSystem.METRIC.toMetres(5.0))
        assertEquals("a tenth of a kilometre is a hundred metres", 100, UnitSystem.METRIC.toMetres(0.1))
    }

    @Test
    fun `a mile is sixteen hundred and nine metres`() {
        assertEquals(1609, UnitSystem.IMPERIAL.toMetres(1.0))
        assertEquals(1.0, UnitSystem.IMPERIAL.fromMetres(1609), 0.001)
        assertEquals("a 5k is a bit over three miles", 3.107, UnitSystem.IMPERIAL.fromMetres(5000), 0.001)
    }

    @Test
    fun `a distance survives the round trip in either system`() {
        UnitSystem.entries.forEach { units ->
            val shown = units.fromMetres(8000)
            assertEquals("$units", 8000, units.toMetres(shown))
        }
    }

    @Test
    fun `a detent of distance is rounded rather than truncated`() {
        // A tenth of a mile is 160.9344 metres. Dropping the fraction each time
        // would walk a logged distance quietly downwards on every edit.
        assertEquals(161, UnitSystem.IMPERIAL.toMetres(0.1))
        assertEquals(1770, UnitSystem.IMPERIAL.toMetres(1.1))
    }

    // -- What the screens print -----------------------------------------------

    @Test
    fun `each system names its own units`() {
        assertEquals("kg", UnitSystem.METRIC.weightSuffix)
        assertEquals("km", UnitSystem.METRIC.distanceSuffix)
        assertEquals("lb", UnitSystem.IMPERIAL.weightSuffix)
        assertEquals("mi", UnitSystem.IMPERIAL.distanceSuffix)
    }
}
