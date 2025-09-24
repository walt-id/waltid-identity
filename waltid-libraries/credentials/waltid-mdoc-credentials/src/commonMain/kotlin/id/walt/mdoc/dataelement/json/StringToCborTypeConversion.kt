package id.walt.mdoc.dataelement.json

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class StringToCborTypeConversion {
    @SerialName("stringToFullDate")
    STRING_TO_FULL_DATE,
    @SerialName("stringToTDate")
    STRING_TO_T_DATE,
    @SerialName("base64StringToByteString")
    BASE64_STRING_TO_BYTE_STRING,
    @SerialName("base64UrlStringToByteString")
    BASE64URL_STRING_TO_BYTE_STRING
}