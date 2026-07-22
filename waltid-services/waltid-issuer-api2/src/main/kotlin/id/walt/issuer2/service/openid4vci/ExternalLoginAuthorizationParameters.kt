package id.walt.issuer2.service.openid4vci

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64

internal const val MAX_EXTERNAL_LOGIN_AUTHORIZATION_PARAMETERS_LENGTH = 4096

internal fun Map<String, List<String>>.encodeExternalLoginAuthorizationParameters(): String {
    val encoded = Base64.UrlSafe
        .encode(Json.encodeToString(this).encodeToByteArray())
        .trimEnd('=')

    require(encoded.length <= MAX_EXTERNAL_LOGIN_AUTHORIZATION_PARAMETERS_LENGTH) {
        "External login authorization parameters exceed the maximum encoded length of " +
                "$MAX_EXTERNAL_LOGIN_AUTHORIZATION_PARAMETERS_LENGTH characters"
    }
    return encoded
}

internal fun String.decodeExternalLoginAuthorizationParameters(): Map<String, List<String>> {
    require(length <= MAX_EXTERNAL_LOGIN_AUTHORIZATION_PARAMETERS_LENGTH) {
        "External login authorization parameters exceed the maximum encoded length of " +
                "$MAX_EXTERNAL_LOGIN_AUTHORIZATION_PARAMETERS_LENGTH characters"
    }

    return runCatching {
        val paddingLength = (4 - length % 4) % 4
        val json = Base64.UrlSafe
            .decode(padEnd(length + paddingLength, '='))
            .decodeToString(throwOnInvalidSequence = true)
        Json.decodeFromString<Map<String, List<String>>>(json)
    }.getOrElse { cause ->
        throw IllegalArgumentException("Invalid external login authorization parameters", cause)
    }
}
