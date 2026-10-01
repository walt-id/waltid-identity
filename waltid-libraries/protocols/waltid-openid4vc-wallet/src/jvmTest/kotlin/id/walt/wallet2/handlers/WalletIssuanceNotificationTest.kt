package id.walt.wallet2.handlers

import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.wallet2.data.StoredCredential
import id.walt.wallet2.data.Wallet
import id.walt.wallet2.data.WalletCredentialStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

class WalletIssuanceNotificationTest {

    @Test
    fun preAuthorizedReceivePostsCredentialAcceptedAfterStorage() = runTest {
        val notifications = mutableListOf<String>()
        val client = issuanceClient(notifications)
        val wallet = wallet()

        val result = WalletIssuanceHandler.receiveCredential(
            wallet = wallet,
            request = ReceiveCredentialRequest(offerJson = Json.parseToJsonElement(CREDENTIAL_OFFER).jsonObject),
            httpClient = client,
        )

        assertEquals(1, result.credentialIds.size)
        assertEquals("credential_accepted", notifications.single())
    }

    @Test
    fun partialBatchPostsOneCredentialFailure() = runTest {
        val notifications = mutableListOf<String>()
        val client = issuanceClient(notifications, credentialCount = 2)
        val wallet = wallet(store = FailingSecondCredentialStore())

        assertFails {
            WalletIssuanceHandler.receiveCredential(
                wallet = wallet,
                request = ReceiveCredentialRequest(offerJson = Json.parseToJsonElement(CREDENTIAL_OFFER).jsonObject),
                httpClient = client,
            )
        }

        assertEquals(listOf("credential_failure"), notifications)
    }

    @Test
    fun authorizationCodeReceivePostsCredentialAccepted() = runTest {
        val notifications = mutableListOf<String>()
        val client = issuanceClient(notifications)
        val wallet = wallet()

        val result = WalletIssuanceHandler.receiveCredentialAuthCode(
            wallet = wallet,
            request = ReceiveAuthorizedCredentialRequest(
                code = "authorization-code",
                credentialIssuer = ISSUER,
                credentialEndpoint = Url("$ISSUER/credential"),
                credentialConfigurationId = "pid",
            ),
            httpClient = client,
        )

        assertEquals(1, result.credentialIds.size)
        assertEquals("credential_accepted", notifications.single())
    }

    @Test
    fun deferredPollPostsCredentialAcceptedWhenNotificationIdIsPresent() = runTest {
        val notifications = mutableListOf<String>()
        val client = issuanceClient(notifications)
        val wallet = wallet()

        val stored = WalletIssuanceHandler.pollDeferredFlow(
            wallet = wallet,
            request = PollDeferredRequest(
                deferredCredentialEndpoint = Url("$ISSUER/deferred"),
                transactionId = "transaction-1",
                accessToken = "access-token",
                credentialIssuerBaseUrl = ISSUER,
                credentialConfigurationId = "pid",
            ),
            httpClient = client,
        ).toList()

        assertEquals(1, stored.size)
        assertEquals("credential_accepted", notifications.single())
    }

    @Test
    fun deferredPollPresentsDpopAccessTokenAndProof() = runTest {
        val authorizations = mutableListOf<String?>()
        val proofs = mutableListOf<String?>()
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    when (request.url.toString()) {
                        "$ISSUER/.well-known/openid-credential-issuer" -> respondJson(ISSUER_METADATA)
                        "$ISSUER/.well-known/oauth-authorization-server" -> respondJson(AUTHORIZATION_SERVER_METADATA)
                        "$ISSUER/deferred", "$ISSUER/notification" -> {
                            authorizations += request.headers[HttpHeaders.Authorization]
                            proofs += request.headers["DPoP"]
                            if (request.url.toString().endsWith("/notification")) {
                                respond(content = "", status = HttpStatusCode.NoContent)
                            } else {
                                respondJson(credentialResponse(1))
                            }
                        }
                        else -> error("Unexpected request: ${request.method.value} ${request.url}")
                    }
                }
            }
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        val wallet = wallet()
        val stored = WalletIssuanceHandler.pollDeferredFlow(
            wallet = wallet,
            request = PollDeferredRequest(
                deferredCredentialEndpoint = Url("$ISSUER/deferred"),
                transactionId = "transaction-1",
                accessToken = "access-token",
                credentialIssuerBaseUrl = ISSUER,
                credentialConfigurationId = "pid",
                tokenType = "DPoP",
                dpopKeyId = wallet.staticKey!!.getKeyId(),
            ),
            httpClient = client,
        ).toList()

        assertEquals(1, stored.size)
        assertEquals(2, authorizations.size)
        assertTrue(authorizations.all { it?.startsWith("DPoP ") == true })
        assertEquals(2, proofs.size)
        assertTrue(proofs.all { !it.isNullOrBlank() })
    }

    @Test
    fun reportCredentialDeletedPostsDeletedEvent() = runTest {
        val notifications = mutableListOf<String>()
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    notifications += Json.parseToJsonElement(request.bodyText()).jsonObject
                        .getValue("event").jsonPrimitive.content
                    respond(content = "", status = HttpStatusCode.NoContent)
                }
            }
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        WalletIssuanceHandler.reportCredentialDeleted(
            notificationEndpoint = "$ISSUER/notification",
            notificationId = "notification-id",
            accessToken = "access-token",
            tokenType = "Bearer",
            httpClient = client,
        )

        assertEquals(listOf("credential_deleted"), notifications)
    }

    @Test
    fun isolatedFetchWithoutStoreDoesNotNotify() = runTest {
        val notifications = mutableListOf<String>()
        val result = WalletIssuanceHandler.fetchCredential(
            wallet = wallet(),
            request = FetchCredentialRequest(
                credentialEndpoint = Url("$ISSUER/credential"),
                accessToken = "access-token",
                credentialConfigurationId = "pid",
            ),
            httpClient = issuanceClient(notifications),
        )

        assertEquals("notification-id", result.notificationId)
        assertEquals(emptyList(), notifications)
    }

    @Test
    fun isolatedFetchStoreInWalletPostsCredentialAccepted() = runTest {
        val notifications = mutableListOf<String>()
        val result = WalletIssuanceHandler.fetchCredential(
            wallet = wallet(),
            request = FetchCredentialRequest(
                credentialEndpoint = Url("$ISSUER/credential"),
                accessToken = "access-token",
                credentialConfigurationId = "pid",
                storeInWallet = true,
                credentialIssuerBaseUrl = ISSUER,
            ),
            httpClient = issuanceClient(notifications),
        )

        assertEquals(1, result.rawCredentials.size)
        assertEquals("notification-id", result.notificationId)
        assertEquals("credential_accepted", notifications.single())
    }

    @Test
    fun rejectIssuedCredentialPostsDeletedEvent() = runTest {
        val notifications = mutableListOf<String>()
        val client = issuanceClient(notifications)

        WalletIssuanceHandler.rejectIssuedCredential(
            request = RejectIssuedCredentialRequest(
                notificationId = "notification-id",
                accessToken = "access-token",
                notificationEndpoint = "$ISSUER/notification",
            ),
            httpClient = client,
        )

        assertEquals(listOf("credential_deleted"), notifications)
    }

    @Test
    fun rejectIssuedCredentialResolvesNotificationEndpointFromIssuerMetadata() = runTest {
        val notifications = mutableListOf<String>()
        val client = issuanceClient(notifications)

        WalletIssuanceHandler.rejectIssuedCredential(
            request = RejectIssuedCredentialRequest(
                notificationId = "notification-id",
                accessToken = "access-token",
                credentialIssuerBaseUrl = ISSUER,
            ),
            httpClient = client,
        )

        assertEquals(listOf("credential_deleted"), notifications)
    }

    @Test
    fun rejectIssuedCredentialIsNoOpWhenIssuerHasNoNotificationEndpoint() = runTest {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    when (request.url.toString()) {
                        "$ISSUER/.well-known/openid-credential-issuer" -> respondJson(
                            """
                            {
                              "credential_issuer": "$ISSUER",
                              "credential_endpoint": "$ISSUER/credential",
                              "credential_configurations_supported": {
                                "pid": { "format": "jwt_vc_json" }
                              }
                            }
                            """
                        )
                        else -> error("Unexpected request: ${request.method.value} ${request.url}")
                    }
                }
            }
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        WalletIssuanceHandler.rejectIssuedCredential(
            request = RejectIssuedCredentialRequest(
                notificationId = "notification-id",
                accessToken = "access-token",
                credentialIssuerBaseUrl = ISSUER,
            ),
            httpClient = client,
        )
    }

    @Test
    fun rejectIssuedCredentialRequiresEndpointOrIssuerBaseUrl() {
        val error = assertFails {
            RejectIssuedCredentialRequest(
                notificationId = "notification-id",
                accessToken = "access-token",
            )
        }
        assertTrue(error is IllegalArgumentException)
    }

    private suspend fun wallet(store: WalletCredentialStore = RecordingCredentialStore()): Wallet =
        Wallet(
            id = "notification-wallet",
            staticKey = JWKKey.generate(KeyType.Ed25519),
            credentialStores = listOf(store),
        )

    private fun issuanceClient(
        notifications: MutableList<String>,
        credentialCount: Int = 1,
    ): HttpClient = HttpClient(MockEngine) {
        engine {
            addHandler { request ->
                when (request.url.toString()) {
                    "$ISSUER/.well-known/openid-credential-issuer" -> respondJson(ISSUER_METADATA)
                    "$ISSUER/.well-known/oauth-authorization-server" -> respondJson(AUTHORIZATION_SERVER_METADATA)
                    "$ISSUER/token" -> respondJson("""{"access_token":"access-token","token_type":"Bearer"}""")
                    "$ISSUER/credential", "$ISSUER/deferred" -> respondJson(credentialResponse(credentialCount))
                    "$ISSUER/notification" -> {
                        notifications += Json.parseToJsonElement(request.bodyText()).jsonObject
                            .getValue("event").jsonPrimitive.content
                        assertTrue(request.headers[HttpHeaders.Authorization]?.startsWith("Bearer ") == true)
                        respond(content = "", status = HttpStatusCode.NoContent)
                    }
                    else -> error("Unexpected request: ${request.method.value} ${request.url}")
                }
            }
        }
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    private fun credentialResponse(credentialCount: Int): String {
        val credentials = List(credentialCount) { """{"credential":$CREDENTIAL}""" }.joinToString(",")
        return """{"credentials":[$credentials],"notification_id":"notification-id"}"""
    }

    private class RecordingCredentialStore : WalletCredentialStore {
        private val credentials = mutableListOf<StoredCredential>()
        override suspend fun getCredential(id: String): StoredCredential? = credentials.find { it.id == id }
        override suspend fun listCredentials(): Flow<StoredCredential> = credentials.asFlow()
        override suspend fun addCredential(entry: StoredCredential) { credentials += entry }
        override suspend fun removeCredential(id: String): Boolean = credentials.removeAll { it.id == id }
    }

    private class FailingSecondCredentialStore : WalletCredentialStore {
        private val credentials = mutableListOf<StoredCredential>()
        override suspend fun getCredential(id: String): StoredCredential? = credentials.find { it.id == id }
        override suspend fun listCredentials(): Flow<StoredCredential> = credentials.asFlow()
        override suspend fun addCredential(entry: StoredCredential) {
            if (credentials.isNotEmpty()) error("second credential failed")
            credentials += entry
        }
        override suspend fun removeCredential(id: String): Boolean = credentials.removeAll { it.id == id }
    }

    private companion object {
        const val ISSUER = "https://issuer.example"
        const val CREDENTIAL_OFFER = """
            {
              "credential_issuer": "$ISSUER",
              "credential_configuration_ids": ["pid"],
              "grants": {
                "urn:ietf:params:oauth:grant-type:pre-authorized_code": {
                  "pre-authorized_code": "pre-authorized-code"
                }
              }
            }
        """
        const val ISSUER_METADATA = """
            {
              "credential_issuer": "$ISSUER",
              "credential_endpoint": "$ISSUER/credential",
              "deferred_credential_endpoint": "$ISSUER/deferred",
              "notification_endpoint": "$ISSUER/notification",
              "credential_configurations_supported": {
                "pid": {
                  "format": "jwt_vc_json",
                  "credential_definition": {
                    "type": ["VerifiableCredential", "PID"]
                  }
                }
              }
            }
        """
        const val AUTHORIZATION_SERVER_METADATA = """
            {
              "issuer": "$ISSUER",
              "authorization_endpoint": "$ISSUER/authorize",
              "token_endpoint": "$ISSUER/token",
              "response_types_supported": ["code"],
              "dpop_signing_alg_values_supported": ["EdDSA"]
            }
        """
        const val CREDENTIAL = """
            {
              "@context": ["https://www.w3.org/2018/credentials/v1"],
              "type": ["VerifiableCredential"],
              "issuer": "did:example:issuer",
              "credentialSubject": {"id": "did:example:holder"}
            }
        """
    }
}

private fun io.ktor.client.engine.mock.MockRequestHandleScope.respondJson(content: String) = respond(
    content = content.trimIndent(),
    status = HttpStatusCode.OK,
    headers = headersOf(HttpHeaders.ContentType, "application/json"),
)

private fun HttpRequestData.bodyText(): String = when (val requestBody = body) {
    is OutgoingContent.ByteArrayContent -> requestBody.bytes().decodeToString()
    is TextContent -> requestBody.text
    else -> error("Unsupported request body type: ${requestBody::class}")
}
