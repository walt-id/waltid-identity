package id.walt.mdoc.conversion

import id.walt.mdoc.dataelement.ByteStringElement
import id.walt.mdoc.dataelement.FullDateElement
import id.walt.mdoc.dataelement.TDateElement
import id.walt.mdoc.dataelement.json.JsonStringToCborMappingConfig
import id.walt.mdoc.dataelement.json.StringToCborTypeConversion
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.*

class JsonStringConversionTest {

    @Test
    fun testStringToFullDateConversion() {
        val mappingConfig = JsonStringToCborMappingConfig(
            conversionType = StringToCborTypeConversion.STRING_TO_FULL_DATE
        )
        val fullDateStr = "1980-05-20"
        val dataElement = mappingConfig.executeMapping(JsonPrimitive(fullDateStr))
        val fullDateElement = assertIs<FullDateElement>(dataElement)
        assertEquals(
            expected = fullDateStr,
            actual = fullDateElement.value.toString(),
        )
    }

    @Test
    fun testStringToTDateConversion() {
        val mappingConfig = JsonStringToCborMappingConfig(
            conversionType = StringToCborTypeConversion.STRING_TO_T_DATE
        )
        val tDateStr = "1986-03-22T22:00:00Z"
        val dataElement = mappingConfig.executeMapping(JsonPrimitive(tDateStr))
        val tDateElement = assertIs<TDateElement>(dataElement)
        assertEquals(
            expected = tDateStr,
            actual = tDateElement.value.toString(),
        )
    }

    @Test
    fun testBase64StringToByteStringConversion() {
        val byteArray = byteArrayOf(0x00, 0x7F, 0x10, 0x20, 0xFF.toByte())
        val base64Str = "AH8QIP8="
        val mappingConfig = JsonStringToCborMappingConfig(
            conversionType = StringToCborTypeConversion.BASE64_STRING_TO_BYTE_STRING
        )
        val dataElement = mappingConfig.executeMapping(JsonPrimitive(base64Str))
        val byteStringElement = assertIs<ByteStringElement>(dataElement)
        assertTrue {
            byteArray.contentEquals(byteStringElement.value)
        }
    }

    @Test
    fun testBase64UrlStringToByteStringConversion() {
        val byteArray = byteArrayOf(0x00, 0x7F, 0x10, 0x20, 0xFF.toByte())
        val base64UrlStr = "AH8QIP8"
        val mappingConfig = JsonStringToCborMappingConfig(
            conversionType = StringToCborTypeConversion.BASE64URL_STRING_TO_BYTE_STRING
        )
        val dataElement = mappingConfig.executeMapping(JsonPrimitive(base64UrlStr))
        val byteStringElement = assertIs<ByteStringElement>(dataElement)
        assertTrue {
            byteArray.contentEquals(byteStringElement.value)
        }
    }

    @Test
    fun testInvalidBase64StringConversions() {
        val base64MappingConfig = JsonStringToCborMappingConfig(
            conversionType = StringToCborTypeConversion.BASE64_STRING_TO_BYTE_STRING
        )
        val base64UrlMappingConfig = JsonStringToCborMappingConfig(
            conversionType = StringToCborTypeConversion.BASE64URL_STRING_TO_BYTE_STRING
        )
        val invalidSamples = listOf(
            "abcd/",            // contains '/' (not allowed in URL-safe)
            "aGVsbG8=gd29ybGQ", // bad '=' padding in the middle
            "###"               // illegal characters
        )
        invalidSamples.forEach {
            assertFails {
                base64MappingConfig.executeMapping(JsonPrimitive(it))
            }

            assertFails {
                base64UrlMappingConfig.executeMapping(JsonPrimitive(it))
            }
        }
    }
}