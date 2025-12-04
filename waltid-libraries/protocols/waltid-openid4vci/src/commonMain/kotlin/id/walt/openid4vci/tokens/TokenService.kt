package id.walt.openid4vci.tokens

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.random.Random

/**
 * Will be updated
 * Simple token generator used until strategy abstractions are introduced.
 * Default handlers defer to injected strategies; we keep the same things so the
 * implementation can be switched later without touching handler logic.
 */
class TokenService {
    fun createAccessToken(clientId: String, code: String): String =
        "access-$clientId-$code-${anotherBase64()}"


    @OptIn(ExperimentalEncodingApi::class)
    private fun anotherBase64(): String =
        Base64.UrlSafe.encode(Random.nextBytes(16))
}
