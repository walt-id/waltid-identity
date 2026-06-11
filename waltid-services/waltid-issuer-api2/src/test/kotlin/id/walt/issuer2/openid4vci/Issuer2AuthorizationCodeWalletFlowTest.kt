package id.walt.issuer2.openid4vci

import id.walt.issuer2.controller.openapi.Issuer2RequestExamples
import id.walt.issuer2.testsupport.Issuer2CredentialScenarios
import id.walt.issuer2.testsupport.Issuer2WalletFlowDriver
import id.walt.issuer2.testsupport.apiClient
import id.walt.issuer2.testsupport.assertSessionStatus
import id.walt.issuer2.testsupport.clearIssuer2TestEnvironment
import id.walt.issuer2.testsupport.createCredentialOffer
import id.walt.issuer2.testsupport.installIssuer2WithConfigFiles
import id.walt.issuer2.testsupport.listSessions
import id.walt.openid4vci.offers.AuthenticationMethod
import id.walt.openid4vci.offers.IssuerStateMode
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Issuer2AuthorizationCodeWalletFlowTest {

    @AfterEach
    fun clearConfig() {
        clearIssuer2TestEnvironment()
    }

    @Test
    fun walletAuthorizationRequestRedirectsToExternalLoginWithIssuerState() = testApplication {
        val scenario = Issuer2CredentialScenarios.universityDegree
        installIssuer2WithConfigFiles()
        val client = apiClient()
        val walletFlow = Issuer2WalletFlowDriver(client)

        val createdOffer = client.createCredentialOffer(Issuer2RequestExamples.PROFILE_AUTHORIZED_OFFER_BY_REFERENCE)
        assertEquals(AuthenticationMethod.AUTHORIZED, createdOffer.authMethod)
        assertEquals(IssuerStateMode.INCLUDE, createdOffer.issuerStateMode)
        assertSessionStatus(client, createdOffer.offerId, "ACTIVE")

        val resolvedOffer = walletFlow.resolve(createdOffer)
        assertEquals(listOf(scenario.credentialConfigurationId), resolvedOffer.offer.credentialConfigurationIds)

        // Runtime-overridden offers use issuer_state as the stable session handle.
        assertEquals(createdOffer.offerId, resolvedOffer.offer.grants?.authorizationCode?.issuerState)

        val externalLoginRedirect = walletFlow.startAuthorizationCodeFlowWithIssuerState(
            createdOffer = createdOffer,
            resolvedOffer = resolvedOffer,
        )
        assertTrue(externalLoginRedirect.contains("/openid4vci/external_login/"))
        assertSessionStatus(client, createdOffer.offerId, "ACTIVE")
    }

    @Test
    fun walletAuthorizationRequestWithoutIssuerStateCreatesProfileDerivedSession() = testApplication {
        val scenario = Issuer2CredentialScenarios.universityDegree
        installIssuer2WithConfigFiles()
        val client = apiClient()
        val walletFlow = Issuer2WalletFlowDriver(client)

        val createdOffer = client.createCredentialOffer(
            Issuer2RequestExamples.PROFILE_AUTHORIZED_OFFER_BY_VALUE_WITHOUT_ISSUER_STATE
        )
        assertEquals(IssuerStateMode.OMIT, createdOffer.issuerStateMode)
        assertSessionStatus(client, createdOffer.offerId, "ACTIVE")

        val resolvedOffer = walletFlow.resolve(createdOffer)
        assertNull(resolvedOffer.offer.grants?.authorizationCode?.issuerState)

        val externalLoginRedirect = walletFlow.startAuthorizationCodeFlowWithoutIssuerState(resolvedOffer)
        assertTrue(externalLoginRedirect.contains("/openid4vci/external_login/"))

        val authorizationSession = client.listSessions().single { session ->
            session.profileId == scenario.profileId &&
                session.sessionId != createdOffer.offerId &&
                session.authorizationRequest != null
        }
        assertNotEquals(createdOffer.offerId, authorizationSession.sessionId)
        assertEquals(AuthenticationMethod.AUTHORIZED, authorizationSession.authenticationMethod)
        assertEquals(scenario.credentialConfigurationId, authorizationSession.credentialConfigurationId)
        assertNotNull(authorizationSession.authorizationRequest?.get("authorization_details"))
    }

    @Test
    fun offerlessAuthorizationRequestCreatesProfileDerivedSessionFromScope() = testApplication {
        val scenario = Issuer2CredentialScenarios.universityDegree
        installIssuer2WithConfigFiles()
        val client = apiClient()

        val authorizationResponse = client.get("/openid4vci/authorize") {
            parameter("response_type", "code")
            parameter("client_id", "issuer2-wallet-test")
            parameter("redirect_uri", "https://wallet.example/callback")
            parameter("state", "offerless-state")
            parameter("scope", scenario.credentialConfigurationId)
        }

        assertEquals(HttpStatusCode.Found, authorizationResponse.status, authorizationResponse.bodyAsText())
        val externalLoginRedirect = assertNotNull(authorizationResponse.headers[HttpHeaders.Location])
        assertTrue(externalLoginRedirect.contains("/openid4vci/external_login/"))

        val authorizationSession = client.listSessions().single { session ->
            session.profileId == scenario.profileId &&
                session.authorizationRequest != null
        }
        assertEquals(AuthenticationMethod.AUTHORIZED, authorizationSession.authenticationMethod)
        assertEquals(scenario.credentialConfigurationId, authorizationSession.credentialConfigurationId)
        assertEquals(
            listOf(scenario.credentialConfigurationId),
            authorizationSession.authorizationRequest?.get("scope"),
        )
    }

    @Test
    fun malformedAuthorizationRequestReturnsOAuthErrorResponse() = testApplication {
        installIssuer2WithConfigFiles()
        val client = apiClient()

        val authorizationResponse = client.get("/openid4vci/authorize") {
            parameter("client_id", "issuer2-wallet-test")
        }

        assertEquals(HttpStatusCode.BadRequest, authorizationResponse.status)
        assertTrue(authorizationResponse.bodyAsText().contains("Missing response_type"))
    }

    @Test
    fun unresolvedAuthorizationRequestRedirectsOAuthErrorToWallet() = testApplication {
        installIssuer2WithConfigFiles()
        val client = apiClient()

        val authorizationResponse = client.get("/openid4vci/authorize") {
            parameter("response_type", "code")
            parameter("client_id", "issuer2-wallet-test")
            parameter("redirect_uri", "https://wallet.example/callback")
            parameter("state", "unknown-scope-state")
            parameter("scope", "unknown_credential_configuration")
        }

        assertEquals(HttpStatusCode.Found, authorizationResponse.status, authorizationResponse.bodyAsText())
        val redirect = Url(assertNotNull(authorizationResponse.headers[HttpHeaders.Location]))
        assertEquals("invalid_request", redirect.parameters["error"])
        assertEquals("unknown-scope-state", redirect.parameters["state"])
        assertTrue(
            assertNotNull(redirect.parameters["error_description"]).contains("No credential configuration"),
        )
    }
}
