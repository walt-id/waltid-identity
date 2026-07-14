package id.walt.openid4vci.clientauth

import id.walt.openid4vci.clientauth.attestation.ClientAttestationHeaders
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class ClientAuthenticationServiceTest {

    @Test
    fun `returns unauthenticated when request has no client authentication input`() = runTest {
        val service = ClientAuthenticationService()

        val result = service.authenticate(
            endpoint = ClientAuthenticationEndpoint.TOKEN,
            parameters = emptyMap(),
            headers = emptyMap(),
        )

        assertEquals(ClientAuthenticationResult.Unauthenticated, result)
    }

    @Test
    fun `ignores client authentication input when endpoint has no configured methods`() = runTest {
        val method = RecordingClientAuthenticationMethod(ClientAuthenticationMethods.ATTEST_JWT_CLIENT_AUTH)
        val service = ClientAuthenticationService(
            ClientAuthenticationServiceConfig(methods = listOf(method)),
        )

        val result = service.authenticate(
            endpoint = ClientAuthenticationEndpoint.TOKEN,
            parameters = mapOf("client_assertion" to listOf("jwt")),
            headers = mapOf(
                ClientAttestationHeaders.CLIENT_ATTESTATION to listOf("jwt"),
            ),
        )

        assertEquals(ClientAuthenticationResult.Unauthenticated, result)
        assertEquals(0, method.calls)
    }

    @Test
    fun `rejects missing client authentication when endpoint has configured methods`() = runTest {
        val method = RecordingClientAuthenticationMethod(ClientAuthenticationMethods.ATTEST_JWT_CLIENT_AUTH)
        val service = ClientAuthenticationService(
            ClientAuthenticationServiceConfig(
                methods = listOf(method),
                methodsByEndpoint = mapOf(
                    ClientAuthenticationEndpoint.TOKEN to setOf(ClientAuthenticationMethods.ATTEST_JWT_CLIENT_AUTH),
                ),
            ),
        )

        val result = service.authenticate(
            endpoint = ClientAuthenticationEndpoint.TOKEN,
            parameters = emptyMap(),
            headers = emptyMap(),
        )

        val failure = assertIs<ClientAuthenticationResult.Failure>(result)
        assertEquals("invalid_client", failure.error.error)
        assertEquals("Client authentication is required for this endpoint", failure.error.description)
    }

    @Test
    fun `rejects multiple client authentication methods`() = runTest {
        val service = ClientAuthenticationService(
            ClientAuthenticationServiceConfig(
                methods = listOf(
                    RecordingClientAuthenticationMethod(ClientAuthenticationMethods.ATTEST_JWT_CLIENT_AUTH),
                    RecordingClientAuthenticationMethod(ClientAuthenticationMethods.CLIENT_SECRET_POST),
                ),
                methodsByEndpoint = mapOf(
                    ClientAuthenticationEndpoint.TOKEN to setOf(
                        ClientAuthenticationMethods.ATTEST_JWT_CLIENT_AUTH,
                        ClientAuthenticationMethods.CLIENT_SECRET_POST,
                    ),
                ),
            ),
        )

        val result = service.authenticate(
            endpoint = ClientAuthenticationEndpoint.TOKEN,
            parameters = mapOf(
                "client_id" to listOf("client-id"),
                "client_secret" to listOf("secret"),
            ),
            headers = mapOf(
                ClientAttestationHeaders.CLIENT_ATTESTATION to listOf("jwt"),
            ),
        )

        val failure = assertIs<ClientAuthenticationResult.Failure>(result)
        assertEquals("invalid_client", failure.error.error)
        assertEquals("Multiple client authentication methods are not allowed", failure.error.description)
    }

    @Test
    fun `rejects detected but unconfigured client authentication method`() = runTest {
        val service = ClientAuthenticationService(
            ClientAuthenticationServiceConfig(
                methodsByEndpoint = mapOf(
                    ClientAuthenticationEndpoint.TOKEN to setOf(ClientAuthenticationMethods.PRIVATE_KEY_JWT),
                ),
            ),
        )

        val result = service.authenticate(
            endpoint = ClientAuthenticationEndpoint.TOKEN,
            parameters = mapOf("client_assertion" to listOf("jwt")),
            headers = emptyMap(),
        )

        val failure = assertIs<ClientAuthenticationResult.Failure>(result)
        assertEquals("invalid_client", failure.error.error)
        assertEquals("Unsupported client authentication method", failure.error.description)
    }

    @Test
    fun `dispatches to configured selected method`() = runTest {
        val method = RecordingClientAuthenticationMethod(ClientAuthenticationMethods.ATTEST_JWT_CLIENT_AUTH)
        val service = ClientAuthenticationService(
            ClientAuthenticationServiceConfig(
                methods = listOf(method),
                methodsByEndpoint = mapOf(
                    ClientAuthenticationEndpoint.PUSHED_AUTHORIZATION to
                        setOf(ClientAuthenticationMethods.ATTEST_JWT_CLIENT_AUTH),
                ),
            ),
        )

        val result = service.authenticate(
            endpoint = ClientAuthenticationEndpoint.PUSHED_AUTHORIZATION,
            parameters = emptyMap(),
            headers = mapOf(
                ClientAttestationHeaders.CLIENT_ATTESTATION to listOf("jwt"),
            ),
        )

        val authenticated = assertIs<ClientAuthenticationResult.Authenticated>(result)
        assertEquals("client-id", authenticated.client.id)
        assertEquals(ClientAuthenticationMethods.ATTEST_JWT_CLIENT_AUTH, authenticated.client.authenticationMethod)
        assertEquals(1, method.calls)
    }

    @Test
    fun `dispatches to selected method from configured methods`() = runTest {
        val attestationMethod = RecordingClientAuthenticationMethod(ClientAuthenticationMethods.ATTEST_JWT_CLIENT_AUTH)
        val clientSecretPostMethod = RecordingClientAuthenticationMethod(ClientAuthenticationMethods.CLIENT_SECRET_POST)
        val service = ClientAuthenticationService(
            ClientAuthenticationServiceConfig(
                methods = listOf(attestationMethod, clientSecretPostMethod),
                methodsByEndpoint = mapOf(
                    ClientAuthenticationEndpoint.TOKEN to setOf(
                        ClientAuthenticationMethods.ATTEST_JWT_CLIENT_AUTH,
                        ClientAuthenticationMethods.CLIENT_SECRET_POST,
                    ),
                ),
            ),
        )

        val result = service.authenticate(
            endpoint = ClientAuthenticationEndpoint.TOKEN,
            parameters = mapOf(
                "client_id" to listOf("client-id"),
                "client_secret" to listOf("secret"),
            ),
            headers = emptyMap(),
        )

        val authenticated = assertIs<ClientAuthenticationResult.Authenticated>(result)
        assertEquals("client-id", authenticated.client.id)
        assertEquals(ClientAuthenticationMethods.CLIENT_SECRET_POST, authenticated.client.authenticationMethod)
        assertEquals(0, attestationMethod.calls)
        assertEquals(1, clientSecretPostMethod.calls)
    }

    @Test
    fun `rejects blank client authentication method names`() {
        assertFailsWith<IllegalArgumentException> {
            ClientAuthenticationServiceConfig(
                methods = listOf(RecordingClientAuthenticationMethod("")),
            )
        }
    }

    @Test
    fun `rejects blank endpoint client authentication method names`() {
        assertFailsWith<IllegalArgumentException> {
            ClientAuthenticationServiceConfig(
                methodsByEndpoint = mapOf(
                    ClientAuthenticationEndpoint.TOKEN to setOf(""),
                ),
            )
        }
    }

    @Test
    fun `allows endpoint methods to be configured before methods are registered`() {
        val config = ClientAuthenticationServiceConfig(
            methodsByEndpoint = mapOf(
                ClientAuthenticationEndpoint.TOKEN to setOf(ClientAuthenticationMethods.PRIVATE_KEY_JWT),
            ),
        )

        assertEquals(
            setOf(ClientAuthenticationMethods.PRIVATE_KEY_JWT),
            config.methodsForEndpoint(ClientAuthenticationEndpoint.TOKEN),
        )
    }

    private class RecordingClientAuthenticationMethod(
        override val name: String,
    ) : ClientAuthenticationMethod {
        var calls: Int = 0
            private set

        override suspend fun authenticate(
            endpoint: ClientAuthenticationEndpoint,
            parameters: Map<String, List<String>>,
            headers: Map<String, List<String>>,
            context: ClientAuthenticationContext,
        ): ClientAuthenticationResult {
            calls += 1
            return ClientAuthenticationResult.Authenticated(
                AuthenticatedClient(
                    id = "client-id",
                    authenticationMethod = name,
                ),
            )
        }
    }
}
