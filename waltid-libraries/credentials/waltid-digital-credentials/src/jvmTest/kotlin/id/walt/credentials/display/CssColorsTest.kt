package id.walt.credentials.display

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CssColorsTest {
    @Test
    fun parsesCssColorLevel3Forms() {
        assertEquals(CssColor(18, 16, 124), CssColors.parse("#12107c"))
        assertEquals(CssColor(17, 34, 51), CssColors.parse("#123"))
        assertEquals(CssColor(255, 0, 128), CssColors.parse("rgb(255, 0, 128)"))
        assertEquals(CssColor(255, 0, 0), CssColors.parse("rgb(100%, 0%, 0%)"))
        assertEquals(CssColor(255, 0, 0, 128), CssColors.parse("rgba(255, 0, 0, 0.5)"))
        assertEquals(CssColor(0, 255, 0), CssColors.parse("hsl(120, 100%, 50%)"))
        assertEquals(CssColor(0, 255, 0, 64), CssColors.parse("hsla(120, 100%, 50%, 25%)"))
        assertEquals(CssColor(0, 0, 0, 0), CssColors.parse("transparent"))
    }

    @Test
    fun rejectsNonCss3Tokens() {
        assertNull(CssColors.parse("#11223344"))
        assertNull(CssColors.parse("12107c"))
        assertNull(CssColors.parse("rgbfoo(255, 0, 0)"))
        assertNull(CssColors.parse("rgb(255, 0, 0) extra"))
        assertNull(CssColors.parse("rgb(NaN, 0, 0)"))
        assertNull(CssColors.parse("rgb(0%, Infinity, 0%)"))
        assertNull(CssColors.parse("rgba(0, 0, 0, -Infinity)"))
        assertNull(CssColors.parse("hsl(Infinity, 100%, 50%)"))
        assertNull(CssColors.parse("hsl(0, NaN%, 50%)"))
        assertNull(CssColors.parse("blue"))
        assertNull(CssColors.parse("not-a-color"))
        assertNull(CssColors.parse(""))
        assertNull(CssColors.parse(null))
    }
}
