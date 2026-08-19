@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.verifier2

import id.walt.commons.config.ConfigManager
import id.walt.commons.config.list.TransactionDataProfile
import id.walt.commons.config.list.TransactionDataProfilesConfig
import id.walt.commons.featureflag.FeatureManager
import id.walt.commons.testing.E2ETest
import id.walt.verifier.openid.models.authorization.ClientMetadata
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import kotlinx.serialization.ExperimentalSerializationApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TransactionDataProfilesVerifier2IntegrationTest {

    @Test
    fun `discovery returns seeded payment_card profile`() {
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
            }
        } finally {
            ConfigManager.preclear()
            FeatureManager.preclear()
        }
    }
}
