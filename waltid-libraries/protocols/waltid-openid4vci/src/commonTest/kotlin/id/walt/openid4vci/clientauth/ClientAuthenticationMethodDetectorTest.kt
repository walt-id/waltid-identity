package id.walt.openid4vci.clientauth

import id.walt.openid4vci.clientauth.attestation.ClientAttestationHeaders
import kotlin.test.Test
import kotlin.test.assertEquals

class ClientAuthenticationMethodDetectorTest {

    @Test
    fun `detects attestation based client authentication from headers`() {
        val methods = ClientAuthenticationMethodDetector.detectRequestedMethods(
            parameters = emptyMap(),
            headers = mapOf(
                ClientAttestationHeaders.CLIENT_ATTESTATION to listOf("attestation-jwt"),
            ),
        )

        assertEquals(setOf(ClientAuthenticationMethods.ATTEST_JWT_CLIENT_AUTH), methods)
    }

    @Test
    fun `detects client authentication headers case-insensitively`() {
        val methods = ClientAuthenticationMethodDetector.detectRequestedMethods(
            parameters = emptyMap(),
            headers = mapOf("oauth-client-attestation-pop" to listOf("pop-jwt")),
        )

        assertEquals(setOf(ClientAuthenticationMethods.ATTEST_JWT_CLIENT_AUTH), methods)
    }

    @Test
    fun `detects private key jwt from assertion parameters`() {
        val methods = ClientAuthenticationMethodDetector.detectRequestedMethods(
            parameters = mapOf("client_assertion" to listOf("assertion-jwt")),
            headers = emptyMap(),
        )

        assertEquals(setOf(ClientAuthenticationMethods.PRIVATE_KEY_JWT), methods)
    }

    @Test
    fun `detects client secret basic from authorization header`() {
        val methods = ClientAuthenticationMethodDetector.detectRequestedMethods(
            parameters = emptyMap(),
            headers = mapOf("Authorization" to listOf("Basic abc")),
        )

        assertEquals(setOf(ClientAuthenticationMethods.CLIENT_SECRET_BASIC), methods)
    }

    @Test
    fun `detects client secret post from client id and client secret form parameters`() {
        val methods = ClientAuthenticationMethodDetector.detectRequestedMethods(
            parameters = mapOf(
                "client_id" to listOf("client-id"),
                "client_secret" to listOf("secret"),
            ),
            headers = emptyMap(),
        )

        assertEquals(setOf(ClientAuthenticationMethods.CLIENT_SECRET_POST), methods)
    }

    @Test
    fun `does not detect client secret post without client id`() {
        val methods = ClientAuthenticationMethodDetector.detectRequestedMethods(
            parameters = mapOf("client_secret" to listOf("secret")),
            headers = emptyMap(),
        )

        assertEquals(emptySet(), methods)
    }

    @Test
    fun `detects multiple requested client authentication methods`() {
        val methods = ClientAuthenticationMethodDetector.detectRequestedMethods(
            parameters = mapOf(
                "client_id" to listOf("client-id"),
                "client_secret" to listOf("secret"),
            ),
            headers = mapOf(
                ClientAttestationHeaders.CLIENT_ATTESTATION_POP to listOf("pop-jwt"),
            ),
        )

        assertEquals(
            setOf(
                ClientAuthenticationMethods.ATTEST_JWT_CLIENT_AUTH,
                ClientAuthenticationMethods.CLIENT_SECRET_POST,
            ),
            methods,
        )
    }
}
