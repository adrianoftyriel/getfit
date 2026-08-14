package org.getfit.app.nutrition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The link a meal travels in, and what happens when it arrives damaged.
 *
 * Every case here is one the app will actually meet: a link retyped by hand, a
 * link cut short by a messaging app, a link written by a newer skill than the
 * installed build. None of them may end with a meal of the wrong size being
 * filed silently.
 */
class MealLinkTest {

    private fun meal(
        id: String = "m-1",
        calories: Double = 640.0,
        capturedAt: Long = 1_723_600_000L,
        label: String = "Chicken burrito bowl",
        items: List<MealItem> = emptyList(),
        schema: Int = MealEntry.SCHEMA_VERSION,
    ) = MealEntry(
        id = id,
        capturedAt = capturedAt,
        label = label,
        items = items,
        totals = Nutrition(calories = calories, proteinG = 42.0, carbsG = 68.0, fatG = 19.0),
        confidence = Confidence.MEDIUM,
        notes = "Assumed no added oil.",
        schemaVersion = schema,
    )

    // -- Round trip ---------------------------------------------------------

    @Test
    fun `a meal survives the round trip intact`() {
        val original = meal(
            items = listOf(
                MealItem("Chicken thigh", "150 g", 150.0, Nutrition(295.0, 25.0, 0.0, 21.0)),
                MealItem("Rice", "1 cup", 190.0, Nutrition(240.0, 4.4, 53.0, 0.4)),
            ),
        )
        val link = MealLink.encode(original, isDevBuild = false)
        val payload = link.substringAfter("&${MealLink.PARAM_DATA}=")

        val result = MealLink.decodePayload(payload)
        assertTrue("decode said: $result", result is MealLinkResult.Ok)
        assertEquals(original, (result as MealLinkResult.Ok).entry)
    }

    @Test
    fun `the link names the scheme and the channel host`() {
        assertTrue(MealLink.encode(meal(), isDevBuild = false).startsWith("getfit://meal?"))
        assertTrue(MealLink.encode(meal(), isDevBuild = true).startsWith("getfit://meal-dev?"))
    }

    /**
     * With both builds installed the two hosts are the only thing stopping a
     * link raising a chooser, so they must not be the same string.
     */
    @Test
    fun `the two channels answer different hosts`() {
        assertNotEquals(MealLink.hostFor(isDevBuild = true), MealLink.hostFor(isDevBuild = false))
    }

    @Test
    fun `the payload needs no url escaping`() {
        val payload = MealLink.encode(meal(label = "Café crème & pain au chocolat"), false)
            .substringAfter("&${MealLink.PARAM_DATA}=")
        assertTrue(
            "base64url must not contain characters a query string would escape: $payload",
            payload.all { it.isLetterOrDigit() || it == '-' || it == '_' },
        )
        // And the accented label still comes back.
        val result = MealLink.decodePayload(payload)
        assertEquals("Café crème & pain au chocolat", (result as MealLinkResult.Ok).entry.label)
    }

    // -- Damaged links ------------------------------------------------------

    @Test
    fun `an empty payload is refused rather than treated as an empty meal`() {
        assertTrue(MealLink.decodePayload(null) is MealLinkResult.Malformed)
        assertTrue(MealLink.decodePayload("") is MealLinkResult.Malformed)
    }

    @Test
    fun `a payload that is not base64 is refused`() {
        assertTrue(MealLink.decodePayload("not a payload!!") is MealLinkResult.Malformed)
    }

    @Test
    fun `a truncated link is refused rather than half read`() {
        val payload = MealLink.encode(meal(), false).substringAfter("&${MealLink.PARAM_DATA}=")
        val cut = payload.take(payload.length / 2)
        assertTrue(
            "a half link must not produce a meal",
            MealLink.decodePayload(cut) is MealLinkResult.Malformed,
        )
    }

    @Test
    fun `a meal from a newer skill says so instead of being misread`() {
        val payload = MealLink.encode(meal(schema = MealEntry.SCHEMA_VERSION + 1), false)
            .substringAfter("&${MealLink.PARAM_DATA}=")
        val result = MealLink.decodePayload(payload)
        assertTrue("expected TooNew, got $result", result is MealLinkResult.TooNew)
        assertTrue((result as MealLinkResult.TooNew).reason.contains("newer"))
    }

    // -- Nonsense that decodes cleanly --------------------------------------

    @Test
    fun `a meal with no identity cannot be filed`() {
        assertTrue(MealLink.validate(meal(id = "")) is MealLinkResult.Malformed)
    }

    @Test
    fun `a meal with no name is refused`() {
        assertTrue(MealLink.validate(meal(label = "  ")) is MealLinkResult.Malformed)
    }

    @Test
    fun `an impossible calorie count is refused`() {
        assertTrue(MealLink.validate(meal(calories = -1.0)) is MealLinkResult.Malformed)
        assertTrue(
            MealLink.validate(meal(calories = MealLink.MAX_PLAUSIBLE_CALORIES + 1))
                is MealLinkResult.Malformed,
        )
        // A large but real meal is not the thing being caught here.
        assertTrue(MealLink.validate(meal(calories = 2_400.0)) is MealLinkResult.Ok)
    }

    @Test
    fun `an undated meal is refused`() {
        assertTrue(MealLink.validate(meal(capturedAt = 0)) is MealLinkResult.Malformed)
    }

    // -- The codec itself ---------------------------------------------------

    @Test
    fun `base64url round trips every tail length`() {
        // One, two and three trailing bytes take different paths through the
        // encoder, and the two-byte tail is the one that historically gets
        // written wrong.
        listOf("a", "ab", "abc", "abcd", "abcde", "").forEach { text ->
            val encoded = base64UrlEncode(text.toByteArray())
            assertEquals(text, base64UrlDecode(encoded)!!.toString(Charsets.UTF_8))
        }
    }

    @Test
    fun `base64url round trips bytes that are not text`() {
        val bytes = ByteArray(256) { (it - 128).toByte() }
        val decoded = base64UrlDecode(base64UrlEncode(bytes))!!
        assertTrue(bytes.contentEquals(decoded))
    }

    @Test
    fun `padding and the standard alphabet are both accepted`() {
        // A link that has been through a mail client may come back either way.
        assertEquals("ab", base64UrlDecode("YWI=")!!.toString(Charsets.UTF_8))
        assertEquals("ab", base64UrlDecode("YWI")!!.toString(Charsets.UTF_8))
        // 0xFF 0xFF encodes as //8 in the standard alphabet and __8 in url-safe.
        assertTrue(base64UrlDecode("//8")!!.contentEquals(base64UrlDecode("__8")!!))
    }

    @Test
    fun `a character outside the alphabet is not silently skipped`() {
        assertNull(base64UrlDecode("YW*I"))
    }
}
