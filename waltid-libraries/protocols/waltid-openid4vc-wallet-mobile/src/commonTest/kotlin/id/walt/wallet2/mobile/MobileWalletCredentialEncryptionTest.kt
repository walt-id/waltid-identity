package id.walt.wallet2.mobile

import id.walt.credentials.CredentialParser
import id.walt.credentials.examples.SdJwtExamples
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.Jwk
import id.walt.crypto2.jose.exportPublicJwk
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.Key
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.openid4vci.offers.CROSS_DEVICE_CREDENTIAL_OFFER_URL
import id.walt.openid4vci.requests.credential.encryption.CredentialEncryptionProfile
import id.walt.openid4vci.requests.credential.encryption.CredentialResponseEncryptionParameters
import id.walt.openid4vci.requests.credential.encryption.Crypto2JweCredentialRequestDecryptor
import id.walt.openid4vci.responses.credential.encryption.Crypto2JweCredentialResponseEncryptor
import id.walt.wallet2.handlers.WalletIssuanceOutcome
import id.walt.wallet2.stores.inmemory.InMemoryCredentialStore
import id.walt.wallet2.stores.inmemory.InMemoryDidStore
import id.walt.wallet2.stores.inmemory.InMemoryIssuanceSessionStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * The Compose demo and iOS demo call [MobileWallet]. This checks that path, not only Wallet API.
 */
class MobileWalletCredentialEncryptionTest {
    private val runtime = CryptoRuntime(defaultSoftwareKeyProviders())

    @Test
    fun `mobile sdk decrypts an encrypted credential response before storing it`() = runTest {
        val issuerKey = runtime.generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId("issuer-encryption"),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.KEY_AGREEMENT),
            ),
        )
        val holderKey = runtime.generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId("holder"),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            ),
        )
        val issuerJwk = issuerKey.metadataJwk()
        val requestTypes = mutableListOf<String>()
        val metadata = buildJsonObject {
            put("credential_issuer", ISSUER)
            put("credential_endpoint", "$ISSUER/credential")
            put("credential_request_encryption", buildJsonObject {
                put("jwks", buildJsonObject { put("keys", buildJsonArray { add(issuerJwk) }) })
                put("enc_values_supported", buildJsonArray { add(JsonPrimitive(CredentialEncryptionProfile.ENC_A128GCM)) })
                put("encryption_required", JsonPrimitive(false))
            })
            put("credential_response_encryption", buildJsonObject {
                put("alg_values_supported", buildJsonArray { add(JsonPrimitive(CredentialEncryptionProfile.ALG_ECDH_ES)) })
                put("enc_values_supported", buildJsonArray { add(JsonPrimitive(CredentialEncryptionProfile.ENC_A128GCM)) })
                put("encryption_required", JsonPrimitive(false))
            })
            put("credential_configurations_supported", buildJsonObject {
                put(CONFIGURATION, buildJsonObject {
                    put("format", "dc+sd-jwt")
                    put("vct", "https://credentials.example.com/identity_credential")
                })
            })
        }.toString()
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    val path = request.url.encodedPath
                    when {
                        path.endsWith("/openid-credential-issuer") -> jsonResponse(metadata)
                        path.endsWith("/oauth-authorization-server") || path.endsWith("/openid-configuration") ->
                            jsonResponse(
                                """
                                {
                                  "issuer":"$ISSUER",
                                  "token_endpoint":"$ISSUER/token",
                                  "response_types_supported":["code"],
                                  "authorization_details_types_supported":["openid_credential"],
                                  "grant_types_supported":["urn:ietf:params:oauth:grant-type:pre-authorized_code"]
                                }
                                """.trimIndent(),
                            )
                        path.endsWith("/token") -> jsonResponse("""{"access_token":"access","token_type":"Bearer"}""")
                        path.endsWith("/credential") -> {
                            val body = request.body as OutgoingContent.ByteArrayContent
                            val type = body.contentType?.withoutParameters()?.toString()?.lowercase() ?: "missing"
                            requestTypes += type
                            val compactJwe = body.bytes().decodeToString()
                            check(type == "application/jwt") { "Credential request was $type: ${compactJwe.take(180)}" }
                            val credentialRequest = Crypto2JweCredentialRequestDecryptor(issuerKey).decrypt(compactJwe)
                            val response = Crypto2JweCredentialResponseEncryptor.encrypt(
                                payload = buildJsonObject {
                                    put("credentials", buildJsonArray {
                                        add(buildJsonObject { put("credential", SdJwtExamples.sdJwtVcSignedExample2) })
                                    })
                                },
                                encryption = CredentialResponseEncryptionParameters.fromJsonObject(
                                    credentialRequest.getValue("credential_response_encryption").jsonObject,
                                ),
                            )
                            respond(response, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/jwt"))
                        }
                        else -> error("Unexpected issuer request: ${request.url}")
                    }
                }
            }
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val credentials = InMemoryCredentialStore()
        val wallet = MobileWallet(
            walletId = "encrypted-issuance",
            keyStore = InMemoryMobileWalletKeyStore().also { it.addCrypto2Key(holderKey) },
            didStore = InMemoryDidStore().also {
                it.addDid(id.walt.wallet2.data.WalletDidEntry(did = "did:key:holder", document = buildJsonObject {}))
            },
            credentialStore = credentials,
            issuanceHttpClient = client,
        )
        val offerUrl = URLBuilder(CROSS_DEVICE_CREDENTIAL_OFFER_URL).apply {
            parameters.append("credential_offer", buildJsonObject {
                put("credential_issuer", ISSUER)
                put("credential_configuration_ids", buildJsonArray { add(JsonPrimitive(CONFIGURATION)) })
                put("grants", buildJsonObject {
                    put("urn:ietf:params:oauth:grant-type:pre-authorized_code", buildJsonObject {
                        put("pre-authorized_code", "pre-code")
                    })
                })
            }.toString())
        }.buildString()

        val session = wallet.startIssuance(
            MobileWalletIssuanceRequest(offer = MobileWalletCredentialOffer.Uri(offerUrl)),
        )
        val outcome = assertIs<WalletIssuanceOutcome.Stored>(wallet.continuePreAuthorizedIssuance(session.id))
        val stored = requireNotNull(credentials.getCredential(outcome.credentialIds.single()))

        assertEquals(listOf("application/jwt"), requestTypes)
        assertEquals(
            CredentialParser.detectAndParse(SdJwtExamples.sdJwtVcSignedExample2).second,
            stored.credential,
        )
    }

    @Test
    fun `mobile sdk decrypts an encrypted deferred response after restart`() = runTest {
        val issuerKey = runtime.generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId("issuer-encryption-deferred"),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.KEY_AGREEMENT),
            ),
        )
        val holderKey = runtime.generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId("holder-deferred"),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            ),
        )
        var responseEncryption: CredentialResponseEncryptionParameters? = null
        val pollTypes = mutableListOf<String>()
        val metadata = encryptedIssuerMetadata(issuerKey.metadataJwk(), deferred = true)
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    val path = request.url.encodedPath
                    when {
                        path.endsWith("/openid-credential-issuer") -> jsonResponse(metadata)
                        path.endsWith("/oauth-authorization-server") || path.endsWith("/openid-configuration") ->
                            jsonResponse(authorizationServerMetadata())
                        path.endsWith("/token") -> jsonResponse("""{"access_token":"access","token_type":"Bearer"}""")
                        path.endsWith("/credential") -> {
                            val body = request.body as OutgoingContent.ByteArrayContent
                            val compactJwe = body.bytes().decodeToString()
                            responseEncryption = CredentialResponseEncryptionParameters.fromJsonObject(
                                Crypto2JweCredentialRequestDecryptor(issuerKey).decrypt(compactJwe)
                                    .getValue("credential_response_encryption").jsonObject,
                            )
                            encryptedResponse(
                                buildJsonObject {
                                    put("transaction_id", "deferred-tx")
                                    put("interval", 1)
                                },
                                requireNotNull(responseEncryption),
                                HttpStatusCode.Accepted,
                            )
                        }
                        path.endsWith("/deferred") -> {
                            val body = request.body as OutgoingContent.ByteArrayContent
                            pollTypes += body.contentType?.withoutParameters()?.toString()?.lowercase() ?: "missing"
                            val poll = body.bytes().decodeToString()
                            check(poll.contains("deferred-tx")) { "Deferred request was not the transaction id: $poll" }
                            encryptedResponse(
                                buildJsonObject {
                                    put("credentials", buildJsonArray {
                                        add(buildJsonObject { put("credential", SdJwtExamples.sdJwtVcSignedExample2) })
                                    })
                                },
                                requireNotNull(responseEncryption),
                                HttpStatusCode.OK,
                            )
                        }
                        else -> error("Unexpected issuer request: ${request.url}")
                    }
                }
            }
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val keys = InMemoryMobileWalletKeyStore().also { it.addCrypto2Key(holderKey) }
        val dids = InMemoryDidStore().also {
            it.addDid(id.walt.wallet2.data.WalletDidEntry(did = "did:key:holder", document = buildJsonObject {}))
        }
        val credentials = InMemoryCredentialStore()
        val sessions = InMemoryIssuanceSessionStore()
        fun wallet() = MobileWallet(
            walletId = "encrypted-deferred",
            keyStore = keys,
            didStore = dids,
            credentialStore = credentials,
            issuanceSessionStore = sessions,
            issuanceHttpClient = client,
        )
        val issuing = wallet()
        val session = issuing.startIssuance(
            MobileWalletIssuanceRequest(offer = MobileWalletCredentialOffer.Uri(preAuthorizedOffer())),
        )
        val deferred = assertIs<WalletIssuanceOutcome.Deferred>(issuing.continuePreAuthorizedIssuance(session.id))
        kotlinx.coroutines.runBlocking { delay(1_100) }
        val outcome = assertIs<WalletIssuanceOutcome.Stored>(
            wallet().resumeDeferredIssuance(deferred.credentials.single().id),
        )
        val stored = requireNotNull(credentials.getCredential(outcome.credentialIds.single()))

        assertEquals(listOf("application/json"), pollTypes)
        assertEquals(
            CredentialParser.detectAndParse(SdJwtExamples.sdJwtVcSignedExample2).second,
            stored.credential,
        )
    }

    private suspend fun Key.metadataJwk() = buildJsonObject {
        Jwk.parse(exportPublicJwk()).forEach { (name, value) -> put(name, value) }
        put("kid", JsonPrimitive(id.value))
        put("alg", JsonPrimitive(CredentialEncryptionProfile.ALG_ECDH_ES))
        put("use", JsonPrimitive(CredentialEncryptionProfile.KEY_USE_ENC))
    }

    private fun MockRequestHandleScope.jsonResponse(content: String) = respond(
        content = content,
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/json"),
    )

    private suspend fun MockRequestHandleScope.encryptedResponse(
        payload: kotlinx.serialization.json.JsonObject,
        encryption: CredentialResponseEncryptionParameters,
        status: HttpStatusCode,
    ) = respond(
        content = Crypto2JweCredentialResponseEncryptor.encrypt(payload, encryption),
        status = status,
        headers = headersOf(HttpHeaders.ContentType, "application/jwt"),
    )

    private suspend fun encryptedIssuerMetadata(issuerJwk: kotlinx.serialization.json.JsonObject, deferred: Boolean) = buildJsonObject {
        put("credential_issuer", ISSUER)
        put("credential_endpoint", "$ISSUER/credential")
        if (deferred) put("deferred_credential_endpoint", "$ISSUER/deferred")
        put("credential_request_encryption", buildJsonObject {
            put("jwks", buildJsonObject { put("keys", buildJsonArray { add(issuerJwk) }) })
            put("enc_values_supported", buildJsonArray { add(JsonPrimitive(CredentialEncryptionProfile.ENC_A128GCM)) })
            put("encryption_required", JsonPrimitive(false))
        })
        put("credential_response_encryption", buildJsonObject {
            put("alg_values_supported", buildJsonArray { add(JsonPrimitive(CredentialEncryptionProfile.ALG_ECDH_ES)) })
            put("enc_values_supported", buildJsonArray { add(JsonPrimitive(CredentialEncryptionProfile.ENC_A128GCM)) })
            put("encryption_required", JsonPrimitive(false))
        })
        put("credential_configurations_supported", buildJsonObject {
            put(CONFIGURATION, buildJsonObject {
                put("format", "dc+sd-jwt")
                put("vct", "https://credentials.example.com/identity_credential")
            })
        })
    }.toString()

    private fun authorizationServerMetadata() = """
        {
          "issuer":"$ISSUER",
          "token_endpoint":"$ISSUER/token",
          "response_types_supported":["code"],
          "authorization_details_types_supported":["openid_credential"],
          "grant_types_supported":["urn:ietf:params:oauth:grant-type:pre-authorized_code"]
        }
    """.trimIndent()

    private fun preAuthorizedOffer() = URLBuilder(CROSS_DEVICE_CREDENTIAL_OFFER_URL).apply {
        parameters.append("credential_offer", buildJsonObject {
            put("credential_issuer", ISSUER)
            put("credential_configuration_ids", buildJsonArray { add(JsonPrimitive(CONFIGURATION)) })
            put("grants", buildJsonObject {
                put("urn:ietf:params:oauth:grant-type:pre-authorized_code", buildJsonObject {
                    put("pre-authorized_code", "pre-code")
                })
            })
        }.toString())
    }.buildString()

    private companion object {
        const val ISSUER = "https://issuer.example"
        const val CONFIGURATION = "test-credential"
    }
}
