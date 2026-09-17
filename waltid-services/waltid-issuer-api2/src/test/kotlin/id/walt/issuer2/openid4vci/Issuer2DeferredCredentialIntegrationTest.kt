package id.walt.issuer2.openid4vci

import id.walt.issuer2.controller.openapi.Issuer2RequestExamples
import id.walt.issuer2.testsupport.Issuer2WalletFlowDriver
import id.walt.issuer2.testsupport.apiClient
import id.walt.issuer2.testsupport.assertJwtVcJsonCredentialPayload
import id.walt.issuer2.testsupport.clearIssuer2TestEnvironment
import id.walt.issuer2.testsupport.createCredentialOffer
import id.walt.issuer2.testsupport.installIssuer2WithConfigFiles
import id.walt.issuer2.config.Issuer2ServiceConfig
import id.walt.issuer2.config.IssuanceMode
import id.walt.openid4vci.offers.AuthenticationMethod
import id.walt.openid4vci.offers.CredentialOfferValueMode
import id.waltid.openid4vci.wallet.oauth.ClientConfiguration
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class Issuer2DeferredCredentialIntegrationTest {

    @AfterEach
    fun clearConfig() {
        clearIssuer2TestEnvironment()
    }

    @Test
    fun shouldIssueDeferredCredentialEndToEnd() = testApplication {
        installIssuer2WithConfigFiles { config ->
            config.copy(credentialIssuanceMode = IssuanceMode.DEFERRED)
        }
        val client = apiClient()
        val wallet1 = Issuer2WalletFlowDriver(client)
        val wallet2 = Issuer2WalletFlowDriver(
            client = client,
            walletClientConfig = ClientConfiguration(
                clientId = "issuer2-wallet-test-2",
                redirectUris = listOf("https://wallet2.example/callback"),
            ),
        )

        val createdOffer = client.createCredentialOffer(Issuer2RequestExamples.PROFILE_PRE_AUTHORIZED_OFFER_BY_REFERENCE.copy(
            authMethod = AuthenticationMethod.PRE_AUTHORIZED,
            valueMode = CredentialOfferValueMode.BY_REFERENCE,
        ))
        val resolvedOffer = wallet1.resolve(createdOffer)

        val tokenResponse = wallet1.exchangePreAuthorizedCode(resolvedOffer, txCode = null)
        assertTrue(tokenResponse.access_token.isNotBlank())

        val wallet1Proofs = wallet1.buildJwtProofs(
            issuerMetadata = resolvedOffer.issuerMetadata,
            credentialConfigurationId = resolvedOffer.offer.credentialConfigurationIds.single(),
        )
        val credentialResponse = client.post(resolvedOffer.issuerMetadata.credentialEndpoint) {
            bearerAuth(tokenResponse.access_token)
            contentType(ContentType.Application.Json)
            setBody(
                id.walt.issuer2.testsupport.credentialRequest(
                    credentialConfigurationId = resolvedOffer.offer.credentialConfigurationIds.single(),
                    proofs = wallet1Proofs,
                )
            )
        }

        assertEquals(HttpStatusCode.Accepted, credentialResponse.status, credentialResponse.bodyAsText())
        val transactionId = assertNotNull(credentialResponse.body<JsonObject>()["transaction_id"]?.jsonPrimitive?.contentOrNull)

        repeat(6) {
            val response = client.post("/openid4vci/deferred_credential") {
                bearerAuth(tokenResponse.access_token)
                contentType(ContentType.Application.Json)
                setBody("""{"transaction_id":"$transactionId"}""")
            }
            if (response.status == HttpStatusCode.OK) {
                val body = response.body<JsonObject>()
                if (body["credentials"] != null || body["credential"] != null) {
                    assertJwtVcJsonCredentialPayload(body)
                    return@testApplication
                }
            }
            assertTrue(response.status in setOf(HttpStatusCode.Accepted, HttpStatusCode.OK), response.bodyAsText())
            kotlinx.coroutines.delay(2.seconds)
        }

        val wallet2Token = wallet2.exchangePreAuthorizedCode(resolvedOffer, txCode = null)
        assertTrue(wallet2Token.access_token.isNotBlank())

        val wallet2Response = client.post("/openid4vci/deferred_credential") {
            bearerAuth(wallet2Token.access_token)
            contentType(ContentType.Application.Json)
            setBody("""{"transaction_id":"$transactionId"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, wallet2Response.status, wallet2Response.bodyAsText())

        val replayResponse = client.post("/openid4vci/deferred_credential") {
            bearerAuth(tokenResponse.access_token)
            contentType(ContentType.Application.Json)
            setBody("""{"transaction_id":"$transactionId"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, replayResponse.status, replayResponse.bodyAsText())
    }
}
