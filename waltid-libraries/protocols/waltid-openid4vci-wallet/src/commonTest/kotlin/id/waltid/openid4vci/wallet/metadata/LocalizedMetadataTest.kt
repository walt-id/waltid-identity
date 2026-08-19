package id.waltid.openid4vci.wallet.metadata

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LocalizedMetadataTest {
    private data class Display(val locale: String?, val name: String)

    @Test
    fun `selects exact then progressively less specific language tags`() {
        val displays = listOf(
            Display("de", "German"),
            Display("de-AT", "Austrian German"),
            Display(null, "Unlocalized"),
        )

        assertEquals("Austrian German", select(displays, listOf("de-AT"))?.name)
        assertEquals("German", select(displays, listOf("de-CH"))?.name)
    }

    @Test
    fun `lookup removes extension and private-use singleton subtags together`() {
        assertEquals(
            "Swiss German",
            select(
                listOf(
                    Display("de-CH-u", "Incomplete extension"),
                    Display("de-CH", "Swiss German"),
                ),
                listOf("de-CH-u-co-phonebk"),
            )?.name,
        )
        assertEquals(
            "English",
            select(
                listOf(Display("en-x", "Incomplete private use"), Display("en", "English")),
                listOf("en-x-wallet"),
            )?.name,
        )
    }

    @Test
    fun `falls back to unlocalized then first advertised display deterministically`() {
        assertEquals(
            "Unlocalized",
            select(
                listOf(Display("en", "English"), Display(null, "Unlocalized")),
                listOf("de"),
            )?.name,
        )
        assertEquals(
            "English",
            select(listOf(Display("en", "English"), Display("fr", "French")), listOf("de"))?.name,
        )
    }

    @Test
    fun `normalizes preferences before constructing Accept-Language`() {
        assertEquals(
            "de-AT, en;q=0.9",
            LocalizedMetadata.acceptLanguageValue(listOf(" de-AT ", "", "DE-at", "invalid tag", "en")),
        )
    }

    @Test
    fun `does not construct Accept-Language from empty or invalid preferences`() {
        assertNull(LocalizedMetadata.acceptLanguageValue(listOf(" ", "invalid tag", "en_US")))
    }

    private fun select(displays: List<Display>, preferredLocales: List<String>): Display? =
        LocalizedMetadata.select(displays, preferredLocales) { it.locale }
}
