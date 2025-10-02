@file:OptIn(ExperimentalTime::class)

package id.walt.mdoc.dataelement.json

import id.walt.mdoc.dataelement.ByteStringElement
import id.walt.mdoc.dataelement.FullDateElement
import id.walt.mdoc.dataelement.TDateElement
import kotlinx.datetime.LocalDate
import kotlin.io.encoding.Base64
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

internal object StringToCborElementConverter {

    private val base64Url = Base64.UrlSafe.withPadding(Base64.PaddingOption.PRESENT_OPTIONAL)

    fun convert(
        s: String,
        conversionHint: StringToCborTypeConversion,
    ) = when (conversionHint) {
        StringToCborTypeConversion.STRING_TO_T_DATE -> {
            stringToTDate(s)
        }

        StringToCborTypeConversion.STRING_TO_FULL_DATE -> {
            stringToFullDate(s)
        }

        StringToCborTypeConversion.BASE64_STRING_TO_BYTE_STRING -> {
            base64StringToByteString(s)
        }

        StringToCborTypeConversion.BASE64URL_STRING_TO_BYTE_STRING -> {
            base64UrlStringToByteString(s)
        }
    }

    private fun stringToTDate(s: String) = TDateElement(Instant.parse(s))

    private fun stringToFullDate(s: String) = FullDateElement(LocalDate.parse(s))

    private fun base64StringToByteString(s: String) = ByteStringElement(Base64.decode(s))

    private fun base64UrlStringToByteString(s: String) = ByteStringElement(base64Url.decode(s))

}
