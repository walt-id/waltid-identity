@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.verifier2

import id.walt.commons.config.ConfigManager
import id.walt.commons.config.list.TransactionDataProfile
import id.walt.commons.config.list.TransactionDataProfilesConfig
import id.walt.commons.featureflag.FeatureConfig
import id.walt.commons.featureflag.FeatureManager
import id.walt.commons.testing.E2ETest
import id.walt.dcql.models.CredentialFormat
import id.walt.dcql.models.CredentialQuery
import id.walt.dcql.models.DcqlQuery
import id.walt.dcql.models.meta.SdJwtVcMeta
import id.walt.verifier.openid.models.authorization.ClientMetadata
import id.walt.verifier2.data.CrossDeviceFlowSetup
import id.walt.verifier2.data.GeneralFlowConfig
import id.walt.verifier2.data.OpenId4VPConfig
import id.walt.verifier2.data.VerificationSessionSetup
import id.walt.verifier2.handlers.sessioncreation.VerificationSessionCreationResponse
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.Application
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TransactionDataProfilesVerifier2IntegrationTest {

    @Test
    fun `discovery CRUD and session create honor the profile registry`() {
        val host = "127.0.0.1"
        val port = 17110
        val paymentCard = TransactionDataProfile(
            type = "payment_card",
            displayName = "Payment Card",
            fields = listOf("merchant_name", "amount"),
        )

        try {
            E2ETest(host, port, true).testBlock(
                features = listOf(OSSVerifier2FeatureCatalog),
                preload = {
                    ConfigManager.preloadConfig(
                        "verifier-service",
                        OSSVerifier2ServiceConfig(
                            clientId = "verifier2",
                            clientMetadata = ClientMetadata(clientName = "Verifier2"),
                            urlPrefix = "http://$host:$port/verification-session",
                            urlHost = "openid4vp://authorize",
                        ),
                    )
                    ConfigManager.preloadConfig(
                        "transaction-data-profiles",
                        TransactionDataProfilesConfig(transactionDataProfiles = listOf(paymentCard)),
                    )
                },
                init = {},
                module = Application::verifierModule,
            ) {
                val http = testHttpClient()

                testAndReturn("Discovery includes payment_card") {
                    val discovered = http.get("/transaction-data-profiles")
                        .also { assertEquals(HttpStatusCode.OK, it.status, it.bodyAsText()) }
                        .body<List<TransactionDataProfile>>()
                    assertTrue(discovered.any { it.type == paymentCard.type })
                    discovered
                }

                testAndReturn("Session create accepts seeded payment_card") {
                    val response = http.post("/verification-session/create") {
                        contentType(ContentType.Application.Json)
                        setBody(sessionSetup(paymentCard.type))
                    }
                    assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
                    response.body<VerificationSessionCreationResponse>()
                }

                testAndReturn("Session create rejects an unknown type") {
                    val response = http.post("/verification-session/create") {
                        contentType(ContentType.Application.Json)
                        setBody(sessionSetup("org.example.unknown-type"))
                    }
                    assertEquals(HttpStatusCode.BadRequest, response.status, response.bodyAsText())
                    assertTrue(
                        response.bodyAsText().contains("Unsupported transaction_data type", ignoreCase = true),
                        response.bodyAsText(),
                    )
                    response
                }

                testAndReturn("POST adds a runtime profile used by session create") {
                    val runtime = TransactionDataProfile(
                        type = "org.example.runtime-payment",
                        displayName = "Runtime Payment",
                        fields = listOf("merchant_name"),
                    )
                    http.post("/transaction-data-profiles") {
                        contentType(ContentType.Application.Json)
                        setBody(runtime)
                    }.also { assertEquals(HttpStatusCode.Created, it.status, it.bodyAsText()) }

                    val created = http.post("/verification-session/create") {
                        contentType(ContentType.Application.Json)
                        setBody(sessionSetup(runtime.type))
                    }
                    assertEquals(HttpStatusCode.OK, created.status, created.bodyAsText())

                    http.delete("/transaction-data-profiles/${runtime.type}")
                        .also { assertEquals(HttpStatusCode.NoContent, it.status, it.bodyAsText()) }
                }
            }
        } finally {
            ConfigManager.preclear()
            FeatureManager.preclear()
        }
    }

    @Test
    fun `disabled feature uses structure-only transaction data validation`() {
        val host = "127.0.0.1"
        val port = 17111

        try {
            E2ETest(host, port, true).testBlock(
                features = listOf(OSSVerifier2FeatureCatalog),
                preload = {
                    ConfigManager.preloadConfig(
                        "_features",
                        FeatureConfig(disabledFeatures = listOf("transaction-data-profiles")),
                    )
                    ConfigManager.preloadConfig(
                        "verifier-service",
                        OSSVerifier2ServiceConfig(
                            clientId = "verifier2",
                            clientMetadata = ClientMetadata(clientName = "Verifier2"),
                            urlPrefix = "http://$host:$port/verification-session",
                            urlHost = "openid4vp://authorize",
                        ),
                    )
                    ConfigManager.preloadConfig(
                        "transaction-data-profiles",
                        TransactionDataProfilesConfig(
                            transactionDataProfiles = listOf(
                                TransactionDataProfile(
                                    type = "payment_card",
                                    displayName = "Payment Card",
                                    fields = listOf("merchant_name", "amount"),
                                ),
                            ),
                        ),
                    )
                },
                init = {},
                module = Application::verifierModule,
            ) {
                val http = testHttpClient()

                testAndReturn("Discovery route is absent when the feature is disabled") {
                    val response = http.get("/transaction-data-profiles")
                    assertEquals(HttpStatusCode.NotFound, response.status, response.bodyAsText())
                    response
                }

                testAndReturn("Session create accepts unknown types with structure-only validation") {
                    assertNull(OSSVerifier2Manager.configuredTransactionDataTypeRegistry())
                    val response = http.post("/verification-session/create") {
                        contentType(ContentType.Application.Json)
                        setBody(sessionSetup("org.example.unknown-type"))
                    }
                    assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
                    response.body<VerificationSessionCreationResponse>()
                }
            }
        } finally {
            ConfigManager.preclear()
            FeatureManager.preclear()
        }
    }

    private fun sessionSetup(transactionDataType: String): VerificationSessionSetup = CrossDeviceFlowSetup(
        core = GeneralFlowConfig(
            dcqlQuery = DcqlQuery(
                credentials = listOf(
                    CredentialQuery(
                        id = "pid",
                        format = CredentialFormat.DC_SD_JWT,
                        meta = SdJwtVcMeta(vctValues = listOf("https://example.com/identity")),
                    ),
                ),
            ),
        ),
        openid = OpenId4VPConfig(
            transactionData = listOf(
                buildJsonObject {
                    put("type", JsonPrimitive(transactionDataType))
                    put("credential_ids", JsonArray(listOf(JsonPrimitive("pid"))))
                    put("require_cryptographic_holder_binding", JsonPrimitive(true))
                    put("transaction_data_hashes_alg", JsonArray(listOf(JsonPrimitive("sha-256"))))
                    put("merchant_name", JsonPrimitive("WeBuild Store"))
                    put("amount", JsonPrimitive("EUR 200.00"))
                },
            ),
        ),
    )
}
