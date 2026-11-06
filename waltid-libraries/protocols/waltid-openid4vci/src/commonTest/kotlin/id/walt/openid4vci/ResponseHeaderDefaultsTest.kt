package id.walt.openid4vci

import id.walt.openid4vci.responses.challenge.AttestationChallengeResponseHttp
import id.walt.openid4vci.responses.credential.CredentialResponseBody
import id.walt.openid4vci.responses.credential.CredentialResponseHttp
import id.walt.openid4vci.responses.nonce.NonceResponseHttp
import id.walt.openid4vci.responses.par.PushedAuthorizationResponseHttp
import id.walt.openid4vci.responses.token.AccessTokenResponseHttp
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

class ResponseHeaderDefaultsTest {
    @Test
    fun sensitiveResponsesDefaultToBothCacheHeaders() {
        val expected = mapOf("Cache-Control" to "no-store", "Pragma" to "no-cache")
        val responses = listOf(
            AccessTokenResponseHttp(200, emptyMap()).headers,
            PushedAuthorizationResponseHttp(201, emptyMap()).headers,
            CredentialResponseHttp(400, emptyMap()).headers,
            CredentialResponseHttp(200, CredentialResponseBody.EncryptedJwt("encrypted")).headers,
        )
        responses.forEach { assertEquals(expected, it) }
    }

    @Test
    fun nonceAndChallengeDefaultToNoStoreOnly() {
        val expected = mapOf("Cache-Control" to "no-store")
        assertEquals(expected, NonceResponseHttp(200, emptyMap()).headers)
        assertEquals(expected, AttestationChallengeResponseHttp(buildJsonObject {}).headers)
    }

    @Test
    fun explicitHeadersRemainSupported() {
        val headers = mapOf("Custom" to "value")
        assertEquals(headers, AccessTokenResponseHttp(200, emptyMap(), headers).headers)
        assertEquals(headers, CredentialResponseHttp(200, emptyMap(), headers).headers)
    }
}
