package id.walt.credentials.display

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DisplayLocalesTest {

    @Test
    fun prefersLanguageRegionThenLanguage() {
        val selected = DisplayLocales.select(
            items = listOf("en" to "English", "de" to "German", "de-AT" to "Austrian"),
            preferredLocales = listOf("de-AT"),
            localeOf = { it.first },
        )
        assertEquals("Austrian", selected?.second)
    }

    @Test
    fun fallsBackToUntaggedThenFirst() {
        assertEquals(
            "untagged",
            DisplayLocales.select(
                items = listOf("en" to "English", null to "untagged"),
                preferredLocales = listOf("fr"),
                localeOf = { it.first },
            )?.second,
        )
        assertEquals(
            "English",
            DisplayLocales.select(
                items = listOf("en" to "English", "de" to "German"),
                preferredLocales = emptyList(),
                localeOf = { it.first },
            )?.second,
        )
        assertNull(DisplayLocales.select(emptyList<String>(), listOf("en")) { it })
    }

    @Test
    fun normalizesUnderscoreAndCase() {
        assertEquals("de-at", DisplayLocales.normalize("de_AT"))
        assertEquals(listOf("de-at", "de"), DisplayLocales.lookupTags("de-at"))
    }
}
