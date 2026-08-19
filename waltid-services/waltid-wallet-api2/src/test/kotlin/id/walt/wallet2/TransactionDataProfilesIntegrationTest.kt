@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.wallet2

import id.walt.commons.config.ConfigManager
import id.walt.commons.config.list.TransactionDataProfile
import id.walt.commons.config.list.TransactionDataProfilesConfig
import id.walt.commons.featureflag.FeatureConfig
import id.walt.commons.featureflag.FeatureManager
import id.walt.commons.testing.E2ETest
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.TypedKeyGenerationRequest
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.did.dids.DidService
import id.walt.wallet2.data.WalletKeyInfo
import id.walt.wallet2.handlers.PreviewPresentationRequest
import id.walt.wallet2.server.handlers.CreateWalletRequest
import id.walt.wallet2.server.handlers.WalletCreatedResponse
import id.walt.wallet2.server.models.PresentationPreviewResponse
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.contentType
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TransactionDataProfilesIntegrationTest {

    private val host = "127.0.0.1"
    private val port = 17080

    private val paymentType = "org.waltid.transaction-data.payment-authorization"
    private val scaType = "urn:eudi:sca:payment:1"
    private val accountType = "org.waltid.transaction-data.account-access"
    private val paymentCardType = "payment_card"

    private val profiles = listOf(
        TransactionDataProfile(
            type = paymentType,
            displayName = "Payment Authorization",
            fields = listOf("merchant_name", "amount", "currency"),
        ),
        TransactionDataProfile(
            type = accountType,
            displayName = "Account Access",
            fields = listOf("account_identifier", "access_scope"),
        ),
        TransactionDataProfile(
            type = scaType,
            displayName = "SCA Payment",
            fields = listOf("payload"),
        ),
        TransactionDataProfile(
            type = paymentCardType,
            displayName = "Payment Card",
            fields = listOf("merchant_name", "amount"),
        ),
    )

    @Test
    fun `discovery and preview accept configured transaction data types`() {
        try {
            E2ETest(host, port, failEarly = true).testBlock(
                features = listOf(OSSWallet2FeatureCatalog),
                preload = {
                    ConfigManager.preloadConfig(
                        "wallet-service",
                        OSSWallet2ServiceConfig(publicBaseUrl = Url("http://$host:$port")),
                    )
                    ConfigManager.preloadConfig(
                        "transaction-data-profiles",
                        TransactionDataProfilesConfig(transactionDataProfiles = profiles),
                    )
                },
                init = {
                    DidService.minimalInit()
                    OSSWallet2Service.configureInMemory()
                },
                module = { wallet2Module(withPlugins = false) },
            ) {
                val http = testHttpClient()

                testAndReturn("Discovery returns configured profiles") {
                    val discovered = http.get("/transaction-data-profiles")
                        .also { assertEquals(HttpStatusCode.OK, it.status, it.bodyAsText()) }
                        .body<List<TransactionDataProfile>>()
                    assertEquals(profiles.map { it.type }.toSet(), discovered.map { it.type }.toSet())
                    assertTrue(discovered.any { it.type == scaType })
                    discovered
                }

                testAndReturn("Service registry knows configured types") {
                    val registry = OSSWallet2Service.configuredTransactionDataTypeRegistry()
                    registry.requireKnown(paymentType)
                    registry.requireKnown(scaType)
                    registry.requireKnown(paymentCardType)
                    assertFailsWith<IllegalArgumentException> {
                        registry.requireKnown("org.example.unknown-type")
                    }
                    registry
                }

                val walletId = testAndReturn("Create wallet") {
                    http.post("/wallet") {
                        contentType(ContentType.Application.Json)
                        setBody(CreateWalletRequest())
                    }.also { assertEquals(HttpStatusCode.Created, it.status) }
                        .body<WalletCreatedResponse>()
                        .walletId
                }

                testAndReturn("Generate signing key") {
                    http.post("/wallet/$walletId/keys/generate") {
                        contentType(ContentType.Application.Json)
                        setBody<TypedKeyGenerationRequest>(TypedKeyGenerationRequest.Jwk(keyType = KeyType.Ed25519))
                    }.also { assertEquals(HttpStatusCode.Created, it.status, it.bodyAsText()) }
                        .body<WalletKeyInfo>()
                }

                testAndReturn("Preview accepts payment-authorization type") {
                    val preview = http.post("/wallet/$walletId/credentials/present/preview") {
                        contentType(ContentType.Application.Json)
                        setBody(PreviewPresentationRequest(requestUrl = presentationRequestUrl(paymentType)))
                    }.also { assertEquals(HttpStatusCode.OK, it.status, it.bodyAsText()) }
                        .body<PresentationPreviewResponse>()
                    assertTypeAcceptedByRegistry(preview, paymentType)
                    preview
                }

                testAndReturn("Preview accepts SCA payment type") {
                    val preview = http.post("/wallet/$walletId/credentials/present/preview") {
                        contentType(ContentType.Application.Json)
                        setBody(PreviewPresentationRequest(requestUrl = presentationRequestUrl(scaType)))
                    }.also { assertEquals(HttpStatusCode.OK, it.status, it.bodyAsText()) }
                        .body<PresentationPreviewResponse>()
                    assertTypeAcceptedByRegistry(preview, scaType)
                    preview
                }

                testAndReturn("Preview accepts payment_card type") {
                    val preview = http.post("/wallet/$walletId/credentials/present/preview") {
                        contentType(ContentType.Application.Json)
                        setBody(PreviewPresentationRequest(requestUrl = presentationRequestUrl(paymentCardType)))
                    }.also { assertEquals(HttpStatusCode.OK, it.status, it.bodyAsText()) }
                        .body<PresentationPreviewResponse>()
                    assertTypeAcceptedByRegistry(preview, paymentCardType)
                    preview
                }

                testAndReturn("Preview rejects unknown transaction data type") {
                    val preview = http.post("/wallet/$walletId/credentials/present/preview") {
                        contentType(ContentType.Application.Json)
                        setBody(
                            PreviewPresentationRequest(
                                requestUrl = presentationRequestUrl("org.example.unknown-type"),
                            ),
                        )
                    }.also { assertEquals(HttpStatusCode.OK, it.status, it.bodyAsText()) }
                        .body<PresentationPreviewResponse>()
                    assertFalse(preview.valid)
                    assertEquals("invalid_transaction_data", preview.error?.code)
                    assertTrue(
                        preview.error?.message.orEmpty().contains("Unsupported transaction_data type", ignoreCase = true),
                        "Expected unsupported-type rejection, got: ${preview.error}",
                    )
                    preview
                }
            }
        } finally {
            ConfigManager.preclear()
            FeatureManager.preclear()
        }
    }

    @Test
    fun `disabled feature yields empty registry and no discovery route`() {
        try {
            E2ETest(host, port + 1, failEarly = true).testBlock(
                features = listOf(OSSWallet2FeatureCatalog),
                preload = {
                    ConfigManager.preloadConfig(
                        "_features",
                        FeatureConfig(disabledFeatures = listOf("transaction-data-profiles")),
                    )
                    ConfigManager.preloadConfig(
                        "wallet-service",
                        OSSWallet2ServiceConfig(publicBaseUrl = Url("http://$host:${port + 1}")),
                    )
                    ConfigManager.preloadConfig(
                        "transaction-data-profiles",
                        TransactionDataProfilesConfig(transactionDataProfiles = profiles),
                    )
                },
                init = {
                    DidService.minimalInit()
                    OSSWallet2Service.configureInMemory()
                },
                module = { wallet2Module(withPlugins = false) },
            ) {
                val http = testHttpClient()

                testAndReturn("Discovery route is absent when feature disabled") {
                    val response = http.get("/transaction-data-profiles")
                    assertEquals(HttpStatusCode.NotFound, response.status, response.bodyAsText())
                    response
                }

                testAndReturn("Registry is empty when feature disabled") {
                    val registry = OSSWallet2Service.configuredTransactionDataTypeRegistry()
                    assertTrue(registry.types.isEmpty())
                    assertFailsWith<IllegalArgumentException> { registry.requireKnown(paymentType) }
                    registry
                }
            }
        } finally {
            ConfigManager.preclear()
            FeatureManager.preclear()
        }
    }

    /**
     * Type-registry acceptance is what this parity pass wires. Without a matching stored credential,
     * preview may still return invalid_transaction_data for credential availability — that proves the
     * type itself was accepted (unknown types fail earlier with "Unsupported transaction_data type").
     */
    private fun assertTypeAcceptedByRegistry(preview: PresentationPreviewResponse, expectedType: String) {
        if (preview.valid) {
            assertEquals(expectedType, preview.transactionData.single().type)
            return
        }
        assertEquals("invalid_transaction_data", preview.error?.code, "Unexpected preview error: ${preview.error}")
        assertFalse(
            preview.error?.message.orEmpty().contains("Unsupported transaction_data type", ignoreCase = true),
            "Type $expectedType should be accepted by the registry, got: ${preview.error}",
        )
    }

    private fun presentationRequestUrl(transactionDataType: String): Url {
        val encodedTransactionData = buildJsonObject {
            put("type", transactionDataType)
            put("credential_ids", buildJsonArray { add(JsonPrimitive("pid")) })
            put("require_cryptographic_holder_binding", true)
            put("transaction_data_hashes_alg", buildJsonArray { add(JsonPrimitive("sha-256")) })
            if (transactionDataType == scaType) {
                put(
                    "payload",
                    buildJsonObject {
                        put("transaction_id", "tx-1")
                        put("payee", buildJsonObject { put("name", "Super Store") })
                        put("currency", "EUR")
                        put("amount", 11.56)
                    },
                )
            } else {
                put("merchant_name", "ACME Corp")
                put("amount", "42.00")
                put("currency", "EUR")
            }
        }.toString().encodeToByteArray().encodeToBase64Url()

        val dcqlQuery = buildJsonObject {
            put(
                "credentials",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("id", "pid")
                            put("format", "dc+sd-jwt")
                            put(
                                "meta",
                                buildJsonObject {
                                    put("vct_values", buildJsonArray { add(JsonPrimitive("https://example.com/identity")) })
                                },
                            )
                        },
                    )
                },
            )
        }.toString()

        return URLBuilder("openid4vp://authorize").apply {
            parameters.append("client_id", "redirect_uri:https://verifier.example/callback")
            parameters.append("redirect_uri", "https://verifier.example/callback")
            parameters.append("response_type", "vp_token")
            parameters.append("response_mode", "fragment")
            parameters.append("nonce", "nonce-td-test")
            parameters.append("dcql_query", dcqlQuery)
            parameters.append("transaction_data", "[\"$encodedTransactionData\"]")
        }.build()
    }
}
