package id.walt.wallet2.handlers

import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.openid4vci.CredentialFormat
import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import id.walt.openid4vci.metadata.issuer.CredentialIssuerMetadata
import id.walt.openid4vci.metadata.issuer.toSignedJwt
import id.walt.wallet2.data.StoredCredential
import id.walt.wallet2.data.Wallet
import id.walt.wallet2.data.WalletCredentialStore
import id.waltid.openid4vci.wallet.metadata.MetadataSigner
import id.waltid.openid4vci.wallet.metadata.MetadataSignerTrustType
import id.waltid.openid4vci.wallet.metadata.ResolvedCredentialIssuerMetadata
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class WalletIssuanceHandlerPreviewTest {

    @Test
    fun credentialCountCallbackRunsBeforeBatchPersistence() = runTest {
        val events = mutableListOf<String>()
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    when (request.url.toString()) {
                        "$ISSUER/.well-known/openid-credential-issuer" -> respondJson(ISSUER_METADATA)
                        "$ISSUER/.well-known/oauth-authorization-server" -> respondJson(AUTHORIZATION_SERVER_METADATA)
                        "$ISSUER/token" -> respondJson("""{"access_token":"token","token_type":"bearer"}""")
                        "$ISSUER/credential" -> respondJson(
                            """{"credentials":[{"credential":$CREDENTIAL},{"credential":$CREDENTIAL}]}"""
                        )
                        else -> error("Unexpected request: ${request.method.value} ${request.url}")
                    }
                }
            }
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
        val store = RecordingCredentialStore(events)
        val wallet = Wallet(
            id = "pre-persistence-callback-test",
            staticKey = JWKKey.generate(KeyType.Ed25519),
            credentialStores = listOf(store),
        )

        val result = WalletIssuanceHandler.receiveCredential(
            wallet = wallet,
            request = ReceiveCredentialRequest(
                offerJson = Json.parseToJsonElement(CREDENTIAL_OFFER).jsonObject,
                txCode = "1234",
            ),
            httpClient = client,
            beforeCredentialsStored = { events += "reserve:$it" },
            onCredentialStored = { events += "stored:${it.id}" },
        )

        assertEquals(2, result.credentialIds.size)
        assertEquals("reserve:2", events.first())
        assertEquals(
            listOf("reserve", "persist", "stored", "persist", "stored"),
            events.map { it.substringBefore(':') },
        )
    }

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

        assertEquals(ISSUER, preview.resolvedIssuerMetadata.metadata.credentialIssuer)
        assertIs<ResolvedCredentialIssuerMetadata.Unsigned>(preview.resolvedIssuerMetadata)
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
    fun signedPreviewRetainsExactMetadataResolutionAndSigner() = runTest {
        val key = JWKKey.generate(KeyType.Ed25519)
        val compactJwt = signedIssuerMetadata().toSignedJwt(key)
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    when (request.url.toString()) {
                        OFFER_URL -> respondJson(CREDENTIAL_OFFER)
                        "$ISSUER/.well-known/openid-credential-issuer" -> respond(
                            content = compactJwt,
                            headers = headersOf(HttpHeaders.ContentType, "application/jwt"),
                        )
                        "$ISSUER/.well-known/oauth-authorization-server" -> respondJson(AUTHORIZATION_SERVER_METADATA)
                        else -> error("Unexpected request: ${request.method.value} ${request.url}")
                    }
                }
            }
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        val preview = WalletIssuanceHandler.previewOffer(
            wallet = Wallet(id = "signed-preview", staticKey = JWKKey.generate(KeyType.Ed25519)),
            request = ResolveOfferRequest(offerUrl = Url(OFFER_DEEP_LINK)),
            httpClient = client,
            metadataTrustResolver = { candidate, expectedIssuer ->
                assertEquals(compactJwt, candidate)
                assertEquals(ISSUER, expectedIssuer)
                key.getPublicKey().verifyJws(candidate).getOrThrow()
                MetadataSigner(key.getKeyId(), "EdDSA", MetadataSignerTrustType.TRUSTED_ISSUER)
            },
        )

        val signed = assertIs<ResolvedCredentialIssuerMetadata.Signed>(preview.resolvedIssuerMetadata)
        assertEquals(compactJwt, signed.compactJwt)
        assertEquals(
            MetadataSigner(key.getKeyId(), "EdDSA", MetadataSignerTrustType.TRUSTED_ISSUER),
            signed.signer,
        )
        assertEquals(ISSUER, signed.metadata.credentialIssuer)
    }

    @Test
    fun detailedOfferResolutionRetainsUnsignedMetadataWithoutTrustResolver() = runTest {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    when (request.url.toString()) {
                        OFFER_URL -> respondJson(CREDENTIAL_OFFER)
                        "$ISSUER/.well-known/openid-credential-issuer" -> respondJson(ISSUER_METADATA)
                        "$ISSUER/.well-known/oauth-authorization-server" -> respondJson(AUTHORIZATION_SERVER_METADATA)
                        else -> error("Unexpected request: ${request.method.value} ${request.url}")
                    }
                }
            }
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        val resolution = WalletIssuanceHandler.resolveOfferDetailed(
            request = ResolveOfferRequest(offerUrl = Url(OFFER_DEEP_LINK)),
            httpClient = client,
        )

        assertIs<ResolvedCredentialIssuerMetadata.Unsigned>(resolution.resolvedIssuerMetadata)
        assertEquals(ISSUER, resolution.resolvedIssuerMetadata.metadata.credentialIssuer)
        assertEquals("pid", resolution.offeredCredentials.single().credentialConfigurationId)
    }

    @Test
    fun detailedOfferResolutionRetainsExactSignedMetadataAndSigner() = runTest {
        val key = JWKKey.generate(KeyType.Ed25519)
        val compactJwt = signedIssuerMetadata().toSignedJwt(key)
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    when (request.url.toString()) {
                        OFFER_URL -> respondJson(CREDENTIAL_OFFER)
                        "$ISSUER/.well-known/openid-credential-issuer" -> respond(
                            content = compactJwt,
                            headers = headersOf(HttpHeaders.ContentType, "application/jwt"),
                        )
                        "$ISSUER/.well-known/oauth-authorization-server" -> respondJson(AUTHORIZATION_SERVER_METADATA)
                        else -> error("Unexpected request: ${request.method.value} ${request.url}")
                    }
                }
            }
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        val signer = MetadataSigner(key.getKeyId(), "EdDSA", MetadataSignerTrustType.TRUSTED_ISSUER)
        val resolution = WalletIssuanceHandler.resolveOfferDetailed(
            request = ResolveOfferRequest(offerUrl = Url(OFFER_DEEP_LINK)),
            httpClient = client,
            metadataTrustResolver = { candidate, expectedIssuer ->
                assertEquals(compactJwt, candidate)
                assertEquals(ISSUER, expectedIssuer)
                key.getPublicKey().verifyJws(candidate).getOrThrow()
                signer
            },
        )

        val signed = assertIs<ResolvedCredentialIssuerMetadata.Signed>(resolution.resolvedIssuerMetadata)
        assertEquals(compactJwt, signed.compactJwt)
        assertEquals(signer, signed.signer)
        assertEquals(ISSUER, signed.metadata.credentialIssuer)
    }

    @Test
    fun detailedOfferResolutionRejectsUntrustedSignedMetadata() = runTest {
        val key = JWKKey.generate(KeyType.Ed25519)
        val compactJwt = signedIssuerMetadata().toSignedJwt(key)
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    when (request.url.toString()) {
                        OFFER_URL -> respondJson(CREDENTIAL_OFFER)
                        "$ISSUER/.well-known/openid-credential-issuer" -> respond(
                            content = compactJwt,
                            headers = headersOf(HttpHeaders.ContentType, "application/jwt"),
                        )
                        "$ISSUER/.well-known/oauth-authorization-server" -> respondJson(AUTHORIZATION_SERVER_METADATA)
                        else -> error("Unexpected request: ${request.method.value} ${request.url}")
                    }
                }
            }
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        assertFails {
            WalletIssuanceHandler.resolveOfferDetailed(
                request = ResolveOfferRequest(offerUrl = Url(OFFER_DEEP_LINK)),
                httpClient = client,
                metadataTrustResolver = { _, _ -> error("untrusted signer") },
            )
        }
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

    private fun signedIssuerMetadata() = CredentialIssuerMetadata(
        credentialIssuer = ISSUER,
        credentialEndpoint = "$ISSUER/credential",
        credentialConfigurationsSupported = mapOf(
            "pid" to CredentialConfiguration(CredentialFormat.SD_JWT_VC),
        ),
    )

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
        const val CREDENTIAL = """
            {
              "@context": ["https://www.w3.org/2018/credentials/v1"],
              "type": ["VerifiableCredential"],
              "issuer": "did:example:issuer",
              "credentialSubject": {"id": "did:example:holder"}
            }
        """
    }

    private class RecordingCredentialStore(private val events: MutableList<String>) : WalletCredentialStore {
        private val credentials = mutableListOf<StoredCredential>()

        override suspend fun getCredential(id: String): StoredCredential? = credentials.find { it.id == id }

        override suspend fun listCredentials(): Flow<StoredCredential> = credentials.asFlow()

        override suspend fun addCredential(entry: StoredCredential) {
            events += "persist:${entry.id}"
            credentials += entry
        }

        override suspend fun removeCredential(id: String): Boolean = credentials.removeAll { it.id == id }
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
