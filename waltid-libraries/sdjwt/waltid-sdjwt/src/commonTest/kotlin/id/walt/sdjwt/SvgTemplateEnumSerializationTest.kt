package id.walt.sdjwt

import id.walt.sdjwt.metadata.type.SvgTemplateColorScheme
import id.walt.sdjwt.metadata.type.SvgTemplateContrast
import id.walt.sdjwt.metadata.type.SvgTemplateOrientation
import id.walt.sdjwt.metadata.type.SvgTemplateProperties
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SvgTemplateEnumSerializationTest {

    // ----- Orientation -----
    @Test
    fun encode_orientation() {
        assertEquals("\"portrait\"", Json.encodeToString(SvgTemplateOrientation.Portrait))
        assertEquals("\"landscape\"", Json.encodeToString(SvgTemplateOrientation.Landscape))
    }

    @Test
    fun decode_orientation() {
        assertEquals(SvgTemplateOrientation.Portrait, Json.decodeFromString("\"portrait\""))
        assertEquals(SvgTemplateOrientation.Landscape, Json.decodeFromString("\"landscape\""))
    }

    // ----- Color scheme -----
    @Test
    fun encode_color() {
        assertEquals("\"light\"", Json.encodeToString(SvgTemplateColorScheme.Light))
        assertEquals("\"dark\"", Json.encodeToString(SvgTemplateColorScheme.Dark))
    }

    @Test
    fun decode_color() {
        assertEquals(SvgTemplateColorScheme.Light, Json.decodeFromString("\"light\""))
        assertEquals(SvgTemplateColorScheme.Dark, Json.decodeFromString("\"dark\""))
    }

    // ----- Contrast -----
    @Test
    fun encode_contrast() {
        assertEquals("\"normal\"", Json.encodeToString(SvgTemplateContrast.Normal))
        assertEquals("\"high\"", Json.encodeToString(SvgTemplateContrast.High))
    }

    @Test
    fun decode_contrast() {
        assertEquals(SvgTemplateContrast.Normal, Json.decodeFromString("\"normal\""))
        assertEquals(SvgTemplateContrast.High, Json.decodeFromString("\"high\""))
    }

    // ----- Round-trip as fields in an object -----
    @Test
    fun roundtrip_wrapper_object() {
        val cfg = SvgTemplateProperties(
            orientation = SvgTemplateOrientation.Portrait,
            colorScheme = SvgTemplateColorScheme.Dark,
            contrast = SvgTemplateContrast.High
        )
        val s = Json.encodeToString(cfg)
        assertEquals("""{"orientation":"portrait","color_scheme":"dark","contrast":"high"}""", s)
        assertEquals(cfg, Json.decodeFromString<SvgTemplateProperties>(s))
    }

    // ----- Round-trip in lists -----
    @Test
    fun roundtrip_lists() {
        val orientations = listOf(SvgTemplateOrientation.Portrait, SvgTemplateOrientation.Landscape)
        val colors = listOf(SvgTemplateColorScheme.Light, SvgTemplateColorScheme.Dark)
        val contrasts = listOf(SvgTemplateContrast.Normal, SvgTemplateContrast.High)

        val so = Json.encodeToString(orientations)
        val sc = Json.encodeToString(colors)
        val st = Json.encodeToString(contrasts)

        assertEquals("""["portrait","landscape"]""", so)
        assertEquals("""["light","dark"]""", sc)
        assertEquals("""["normal","high"]""", st)

        assertEquals(orientations, Json.decodeFromString(so))
        assertEquals(colors, Json.decodeFromString(sc))
        assertEquals(contrasts, Json.decodeFromString(st))
    }

    // ----- Map keys serialize to serial names -----
    @Test
    fun map_keys() {
        val m1 = mapOf(SvgTemplateOrientation.Portrait to 1, SvgTemplateOrientation.Landscape to 2)
        val s1 = Json.encodeToString(m1)
        val d1: Map<SvgTemplateOrientation, Int> = Json.decodeFromString(s1)
        assertEquals(m1, d1)
        assertTrue(s1.contains("\"portrait\"") && s1.contains("\"landscape\""))

        val m2 = mapOf(SvgTemplateColorScheme.Light to true, SvgTemplateColorScheme.Dark to false)
        val s2 = Json.encodeToString(m2)
        val d2: Map<SvgTemplateColorScheme, Boolean> = Json.decodeFromString(s2)
        assertEquals(m2, d2)
        assertTrue(s2.contains("\"light\"") && s2.contains("\"dark\""))

        val m3 = mapOf(SvgTemplateContrast.Normal to "n", SvgTemplateContrast.High to "h")
        val s3 = Json.encodeToString(m3)
        val d3: Map<SvgTemplateContrast, String> = Json.decodeFromString(s3)
        assertEquals(m3, d3)
        assertTrue(s3.contains("\"normal\"") && s3.contains("\"high\""))
    }

    // ----- Failure cases (unknown value, wrong casing) -----
    @Test
    fun decoding_unknown_value_fails() {
        assertFailsWith<SerializationException> { Json.decodeFromString<SvgTemplateOrientation>("\"square\"") }
        assertFailsWith<SerializationException> { Json.decodeFromString<SvgTemplateColorScheme>("\"LIGHT\"") }
        assertFailsWith<SerializationException> { Json.decodeFromString<SvgTemplateContrast>("\"High-Contrast\"") }
    }

}