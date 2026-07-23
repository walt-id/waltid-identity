package id.walt.wallet2.handlers

import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.wallet2.data.Wallet
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith

class WalletIssuanceHandlerPreviewTest {

    @Test
    fun previewsOfSameCredentialOfferUriRemainIndependentlyBound() = runTest {
        var offerFetches = 0
        val tokenEndpoints = mutableListOf<String>()
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    val issuer = if (offerFetches == 0) "$ISSUER/first" else "$ISSUER/second"
                    when {
                        request.url.toString() == OFFER_URL -> {
                            offerFetches++
                            respondJson(credentialOffer(issuer))
                        }
                        request.url.toString().contains("/.well-known/openid-credential-issuer") ->
                            respondJson(issuerMetadata(if (request.url.encodedPath.endsWith("/first")) "$ISSUER/first" else "$ISSUER/second"))
                        request.url.toString().contains("/.well-known/oauth-authorization-server") ->
                            respondJson(authorizationServerMetadata(if (request.url.encodedPath.endsWith("/first")) "$ISSUER/first" else "$ISSUER/second"))
                        request.url.toString().endsWith("/token") -> {
                            tokenEndpoints += request.url.toString()
                            respondJson("""{"error":"invalid_grant"}""", HttpStatusCode.BadRequest)
                        }
                        else -> error("Unexpected request: ${request.method.value} ${request.url}")
                    }
                }
            }
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val wallet = Wallet(id = "same-offer-uri", staticKey = JWKKey.generate(KeyType.Ed25519))

        val first = WalletIssuanceHandler.previewOffer(
            wallet,
            ResolveOfferRequest(offerUrl = Url(OFFER_DEEP_LINK)),
            client,
        )
        val second = WalletIssuanceHandler.previewOffer(
            wallet,
            ResolveOfferRequest(offerUrl = Url(OFFER_DEEP_LINK)),
            client,
        )

        assertFails {
            WalletIssuanceHandler.receiveCredential(
                wallet,
                ReceiveCredentialFromPreviewRequest(first.previewHandle),
                httpClient = client,
            )
        }
        assertFails {
            WalletIssuanceHandler.receiveCredential(
                wallet,
                ReceiveCredentialFromPreviewRequest(second.previewHandle),
                httpClient = client,
            )
        }

        assertEquals(
            listOf("$ISSUER/first/token", "$ISSUER/second/token"),
            tokenEndpoints,
        )
    }

    @Test
    fun previewedOfferIsReusedForRetriesWhileDirectReceiveStillResolves() = runTest {
        var offerFetches = 0
        var metadataFetches = 0
        var tokenRequests = 0
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    when (request.url.toString()) {
                        OFFER_URL -> {
                            offerFetches++
                            respondJson(CREDENTIAL_OFFER)
                        }

                        "$ISSUER/.well-known/openid-credential-issuer" -> {
                            metadataFetches++
                            respondJson(ISSUER_METADATA)
                        }

                        "$ISSUER/.well-known/oauth-authorization-server" -> {
                            metadataFetches++
                            respondJson(AUTHORIZATION_SERVER_METADATA)
                        }
                        "$ISSUER/token" -> {
                            tokenRequests++
                            respondJson("""{"error":"invalid_grant"}""", HttpStatusCode.BadRequest)
                        }

                        else -> error("Unexpected request: ${request.method.value} ${request.url}")
                    }
                }
            }
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
        val wallet = Wallet(
            id = "offer-binding-test",
            staticKey = JWKKey.generate(KeyType.Ed25519),
        )
        val preview = WalletIssuanceHandler.previewOffer(
            wallet = wallet,
            request = ResolveOfferRequest(offerUrl = Url(OFFER_DEEP_LINK)),
            httpClient = client,
        )

        assertEquals(ISSUER, preview.issuerMetadata.credentialIssuer)
        assertEquals("pid", preview.offeredCredentials.single().credentialConfigurationId)
        assertEquals("text", preview.transactionCode?.inputMode)

        repeat(2) {
            assertFails {
                WalletIssuanceHandler.receiveCredential(
                    wallet = wallet,
                    request = ReceiveCredentialFromPreviewRequest(
                        previewHandle = preview.previewHandle,
                        txCode = "wrong-code",
                    ),
                    httpClient = client,
                )
            }
        }

        assertEquals(1, offerFetches)
        assertEquals(2, metadataFetches)
        assertEquals(2, tokenRequests)

        assertFails {
            WalletIssuanceHandler.receiveCredential(
                wallet = wallet.copy(id = "direct-receive-test"),
                request = ReceiveCredentialRequest(
                    offerUrl = Url(OFFER_DEEP_LINK),
                    txCode = "wrong-code",
                ),
                httpClient = client,
            )
        }

        assertEquals(2, offerFetches)
        assertEquals(4, metadataFetches)
        assertEquals(3, tokenRequests)
    }

    @Test
    fun successfulIssuanceConsumesPreview() = runTest {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    when (request.url.toString()) {
                        OFFER_URL -> respondJson(CREDENTIAL_OFFER)
                        "$ISSUER/.well-known/openid-credential-issuer" -> respondJson(ISSUER_METADATA)
                        "$ISSUER/.well-known/oauth-authorization-server" -> respondJson(AUTHORIZATION_SERVER_METADATA)
                        "$ISSUER/token" -> respondJson("""{"access_token":"token","token_type":"Bearer"}""")
                        "$ISSUER/credential" -> respondJson("""{"transaction_id":"deferred-1"}""")
                        else -> error("Unexpected request: ${request.method.value} ${request.url}")
                    }
                }
            }
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val wallet = Wallet(id = "successful-preview", staticKey = JWKKey.generate(KeyType.Ed25519))
        val preview = WalletIssuanceHandler.previewOffer(
            wallet,
            ResolveOfferRequest(offerUrl = Url(OFFER_DEEP_LINK)),
            client,
        )

        WalletIssuanceHandler.receiveCredential(
            wallet,
            ReceiveCredentialFromPreviewRequest(preview.previewHandle),
            httpClient = client,
        )

        val error = assertFailsWith<PreviewSessionException> {
            WalletIssuanceHandler.receiveCredential(
                wallet,
                ReceiveCredentialFromPreviewRequest(preview.previewHandle),
                httpClient = client,
            )
        }
        assertEquals(PreviewSessionFailureReason.CONSUMED, error.reason)
    }

    private fun credentialOffer(issuer: String): String = """
        {
          "credential_issuer": "$issuer",
          "credential_configuration_ids": ["pid"],
          "grants": {
            "urn:ietf:params:oauth:grant-type:pre-authorized_code": {
              "pre-authorized_code": "pre-authorized-code"
            }
          }
        }
    """

    private fun issuerMetadata(issuer: String): String = """
        {
          "credential_issuer": "$issuer",
          "credential_endpoint": "$issuer/credential",
          "credential_configurations_supported": {
            "pid": {
              "format": "jwt_vc_json",
              "credential_definition": { "type": ["VerifiableCredential", "PID"] }
            }
          }
        }
    """

    private fun authorizationServerMetadata(issuer: String): String = """
        {
          "issuer": "$issuer",
          "authorization_endpoint": "$issuer/authorize",
          "token_endpoint": "$issuer/token",
          "response_types_supported": ["code"]
        }
    """

    private companion object {
        const val ISSUER = "https://issuer.example"
        const val OFFER_URL = "$ISSUER/credential-offer"
        const val OFFER_DEEP_LINK =
            "openid-credential-offer://?credential_offer_uri=https%3A%2F%2Fissuer.example%2Fcredential-offer"
        const val CREDENTIAL_OFFER = """
            {
              "credential_issuer": "$ISSUER",
              "credential_configuration_ids": ["pid"],
              "grants": {
                "urn:ietf:params:oauth:grant-type:pre-authorized_code": {
                  "pre-authorized_code": "pre-authorized-code",
                  "tx_code": { "input_mode": "text" }
                }
              }
            }
        """
        const val ISSUER_METADATA = """
            {
              "credential_issuer": "$ISSUER",
              "credential_endpoint": "$ISSUER/credential",
              "display": [{ "name": "Example Issuer", "locale": "en" }],
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
              "response_types_supported": ["code"]
            }
        """
    }
}

private fun io.ktor.client.engine.mock.MockRequestHandleScope.respondJson(
    content: String,
    status: HttpStatusCode = HttpStatusCode.OK,
) = respond(
    content = content.trimIndent(),
    status = status,
    headers = headersOf(HttpHeaders.ContentType, "application/json"),
)
