@file:OptIn(ExperimentalTime::class)

package id.walt.mdoc.conversion

import id.walt.mdoc.dataelement.ByteStringElement
import id.walt.mdoc.dataelement.FullDateElement
import id.walt.mdoc.dataelement.ListElement
import id.walt.mdoc.dataelement.TDateElement
import id.walt.mdoc.dataelement.json.JsonArrayToCborMappingConfig
import id.walt.mdoc.dataelement.json.JsonStringToCborMappingConfig
import id.walt.mdoc.dataelement.json.StringToCborTypeConversion
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime

class JsonArrayConversionTest {

    @Test
    fun testStringConversions() {
        val fullDateStr = "1980-05-20"
        val tDateStr = "1986-03-22T22:00:00Z"
        val byteArray = byteArrayOf(0x00, 0x7F, 0x10, 0x20, 0xFF.toByte())
        val base64Str = "AH8QIP8="
        val base64UrlStr = "AH8QIP8"
        val mappingConfig = JsonArrayToCborMappingConfig(
            arrayConfig = listOf(
                JsonStringToCborMappingConfig(
                    conversionType = StringToCborTypeConversion.STRING_TO_FULL_DATE
                ),
                JsonStringToCborMappingConfig(
                    conversionType = StringToCborTypeConversion.STRING_TO_T_DATE
                ),
                JsonStringToCborMappingConfig(
                    conversionType = StringToCborTypeConversion.BASE64_STRING_TO_BYTE_STRING
                ),
                JsonStringToCborMappingConfig(
                    conversionType = StringToCborTypeConversion.BASE64URL_STRING_TO_BYTE_STRING
                )
            )
        )
        val dataElement = mappingConfig.executeMapping(
            Json.encodeToJsonElement(
                listOf(
                    fullDateStr, tDateStr, base64Str, base64UrlStr
                )
            )
        )
        val listElement = assertIs<ListElement>(dataElement)
        val fullDateElement = assertIs<FullDateElement>(listElement.value[0])
        assertEquals(
            expected = fullDateStr,
            actual = fullDateElement.value.toString(),
        )
        val tDateElement = assertIs<TDateElement>(listElement.value[1])
        assertEquals(
            expected = tDateStr,
            actual = tDateElement.value.toString(),
        )
        var byteStringElement = assertIs<ByteStringElement>(listElement.value[2])
        assertTrue {
            byteArray.contentEquals(byteStringElement.value)
        }
        byteStringElement = assertIs<ByteStringElement>(listElement.value[3])
        assertTrue {
            byteArray.contentEquals(byteStringElement.value)
        }
    }
}
