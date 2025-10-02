@file:OptIn(ExperimentalTime::class)

package id.walt.mdoc.conversion

import id.walt.mdoc.dataelement.ByteStringElement
import id.walt.mdoc.dataelement.FullDateElement
import id.walt.mdoc.dataelement.TDateElement
import id.walt.mdoc.dataelement.json.StringToCborElementConverter
import id.walt.mdoc.dataelement.json.StringToCborTypeConversion
import kotlin.test.*
import kotlin.time.ExperimentalTime

class StringToCborElementConverterTest {

    @Test
    fun testStringToFullDateConversion() {
        val fullDateStr = "1980-05-20"
        val dataElement = StringToCborElementConverter.convert(
            s = fullDateStr,
            conversionHint = StringToCborTypeConversion.STRING_TO_FULL_DATE,
        )
        val fullDateElement = assertIs<FullDateElement>(dataElement)
        assertEquals(
            expected = fullDateStr,
            actual = fullDateElement.value.toString(),
        )
    }

    @Test
    fun testStringToTDateConversion() {
        val tDateStr = "1986-03-22T22:00:00Z"
        val dataElement = StringToCborElementConverter.convert(
            s = tDateStr,
            conversionHint = StringToCborTypeConversion.STRING_TO_T_DATE,
        )
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
        val dataElement = StringToCborElementConverter.convert(
            s = base64Str,
            conversionHint = StringToCborTypeConversion.BASE64_STRING_TO_BYTE_STRING,
        )
        val byteStringElement = assertIs<ByteStringElement>(dataElement)
        assertTrue {
            byteArray.contentEquals(byteStringElement.value)
        }
    }

    @Test
    fun testBase64UrlStringToByteStringConversion() {
        val byteArray = byteArrayOf(0x00, 0x7F, 0x10, 0x20, 0xFF.toByte())
        val base64UrlStr = "AH8QIP8"
        val dataElement = StringToCborElementConverter.convert(
            s = base64UrlStr,
            conversionHint = StringToCborTypeConversion.BASE64URL_STRING_TO_BYTE_STRING,
        )
        val byteStringElement = assertIs<ByteStringElement>(dataElement)
        assertTrue {
            byteArray.contentEquals(byteStringElement.value)
        }
    }

    @Test
    fun testInvalidBase64StringConversions() {
        val invalidSamples = listOf(
            "abcd/",            // contains '/' (not allowed in URL-safe)
            "aGVsbG8=gd29ybGQ", // bad '=' padding in the middle
            "###"               // illegal characters
        )
        invalidSamples.forEach {
            assertFails {
                StringToCborElementConverter.convert(
                    s = it,
                    conversionHint = StringToCborTypeConversion.BASE64_STRING_TO_BYTE_STRING,
                )
            }

            assertFails {
                StringToCborElementConverter.convert(
                    s = it,
                    conversionHint = StringToCborTypeConversion.BASE64URL_STRING_TO_BYTE_STRING,
                )
            }
        }
    }
}
