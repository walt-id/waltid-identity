package id.walt.issuer2.openid4vci

import id.walt.issuer2.controller.openapi.Issuer2RequestExamples
import id.walt.issuer2.testsupport.credentialRequest
import id.walt.issuer2.testsupport.Issuer2CredentialScenarios
import id.walt.issuer2.testsupport.Issuer2WalletFlowDriver
import id.walt.issuer2.testsupport.apiClient
import id.walt.issuer2.testsupport.assertBearerAccessToken
import id.walt.issuer2.testsupport.assertJwtVcJsonCredentialPayload
import id.walt.issuer2.testsupport.assertSessionStatus
import id.walt.issuer2.testsupport.clearIssuer2TestEnvironment
import id.walt.issuer2.testsupport.createCredentialOffer
import id.walt.issuer2.testsupport.installIssuer2WithConfigFiles
import id.walt.issuer2.testsupport.referencedOfferUri
import id.walt.openid4vci.offers.AuthenticationMethod
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
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Issuer2PreAuthorizedWalletFlowTest {

    @AfterEach
    fun clearConfig() {
        clearIssuer2TestEnvironment()
    }

    @Test
    fun walletCanCompletePreAuthorizedByReferenceOfferWithoutTxCode() = testApplication {
        val scenario = Issuer2CredentialScenarios.universityDegree
        installIssuer2WithConfigFiles()
        val client = apiClient()
        val walletFlow = Issuer2WalletFlowDriver(client)

        val createdOffer = client.createCredentialOffer(Issuer2RequestExamples.PROFILE_PRE_AUTHORIZED_OFFER_BY_REFERENCE)
        assertEquals(AuthenticationMethod.PRE_AUTHORIZED, createdOffer.authMethod)
        assertNull(createdOffer.issuerStateMode)
        assertNull(createdOffer.txCodeValue)
        val credentialOfferUri = createdOffer.referencedOfferUri()
        assertTrue(
            credentialOfferUri.contains("/openid4vci/credential-offer?id=${createdOffer.offerId}"),
            "Expected by-reference offer URI for session ${createdOffer.offerId}, got $credentialOfferUri",
        )
        assertSessionStatus(client, createdOffer.offerId, "ACTIVE")

        val resolvedOffer = walletFlow.resolve(createdOffer)
        assertEquals(listOf(scenario.credentialConfigurationId), resolvedOffer.offer.credentialConfigurationIds)

        val preAuthorizedGrant = assertNotNull(resolvedOffer.offer.grants?.preAuthorizedCode)
        assertNull(preAuthorizedGrant.txCode)

        val tokenResponse = walletFlow.exchangePreAuthorizedCode(resolvedOffer, txCode = null)
        assertBearerAccessToken(tokenResponse)
        assertSessionStatus(client, createdOffer.offerId, "ACTIVE")

        val proofs = walletFlow.buildJwtProofs(resolvedOffer.issuerMetadata)
        val credentialResponse = client.post(resolvedOffer.issuerMetadata.credentialEndpoint) {
            bearerAuth(tokenResponse.access_token)
            contentType(ContentType.Application.Json)
            setBody(
                credentialRequest(
                    credentialConfigurationId = resolvedOffer.offer.credentialConfigurationIds.single(),
                    proofs = proofs,
                )
            )
        }

        assertEquals(HttpStatusCode.OK, credentialResponse.status, credentialResponse.bodyAsText())
        assertJwtVcJsonCredentialPayload(credentialResponse.body<JsonObject>())
        assertSessionStatus(client, createdOffer.offerId, "SUCCESSFUL")
    }
}
