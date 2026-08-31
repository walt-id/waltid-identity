package id.walt.issuer2.openid4vci

import id.walt.issuer2.models.CredentialOfferRuntimeOverrides
import id.walt.issuer2.testsupport.Issuer2CredentialScenarios
import id.walt.issuer2.testsupport.Issuer2TxCodeMode
import id.walt.issuer2.testsupport.Issuer2WalletFlowDriver
import id.walt.issuer2.testsupport.apiClient
import id.walt.issuer2.testsupport.assertBearerAccessToken
import id.walt.issuer2.testsupport.assertIsoMdlCredentialPayload
import id.walt.issuer2.testsupport.assertRefreshToken
import id.walt.issuer2.testsupport.assertSessionStatus
import id.walt.issuer2.testsupport.clearIssuer2TestEnvironment
import id.walt.issuer2.testsupport.createWalletFlowCredentialOffer
import id.walt.issuer2.testsupport.installIssuer2WithConfigFiles
import id.walt.issuer2.testsupport.mdocValidityInfo
import id.walt.mdoc.objects.mso.ValidityInfo
import id.walt.openid4vci.mdoc.MsoData
import id.walt.openid4vci.offers.AuthenticationMethod
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

class Issuer2MsoDataWalletFlowTest {

    @AfterEach
    fun tearDown() {
        clearIssuer2TestEnvironment()
    }

    @Test
    fun issuedMdocUsesDefaultValidityWhenMsoDataIsOmitted() = testApplication {
        val validity = issueIsoMdl()
        assertEquals(validity.signed.epochSeconds, validity.validFrom.epochSeconds)
        assertTrue(validity.validUntil - validity.signed in (364.days..366.days))
        assertNull(validity.expectedUpdate)
    }

    @Test
    fun issuedMdocUsesRuntimeMsoDataFunctions() = testApplication {
        val now = Clock.System.now()
        val validity = issueIsoMdl(
            CredentialOfferRuntimeOverrides(
                msoData = MsoData(
                    validFrom = "<timestamp>",
                    validUntil = "<timestamp-in:365d>",
                    expectedUpdate = "<timestamp-in:180d>",
                )
            )
        )

        assertTrue(validity.validFrom - now in (-2.days..2.days))
        assertTrue(validity.validUntil - validity.validFrom in (364.days..366.days))
        assertTrue(assertNotNull(validity.expectedUpdate) - validity.validFrom in (179.days..181.days))
    }

    @Test
    fun issuedMdocUsesStaticRuntimeMsoData() = testApplication {
        val validFrom = Clock.System.now().plus(1.days)
        val validUntil = validFrom.plus(365.days * 5)
        val expectedUpdate = validFrom.plus(180.days)
        val validity = issueIsoMdl(
            CredentialOfferRuntimeOverrides(
                msoData = MsoData(
                    validFrom = validFrom.toString(),
                    validUntil = validUntil.toString(),
                    expectedUpdate = expectedUpdate.toString(),
                )
            )
        )

        assertEquals(validFrom.epochSeconds, validity.validFrom.epochSeconds)
        assertEquals(validUntil.epochSeconds, validity.validUntil.epochSeconds)
        assertEquals(expectedUpdate.epochSeconds, validity.expectedUpdate?.epochSeconds)
    }

    @Test
    fun mappingValidFromDoesNotChangeMsoValidity() = testApplication {
        val validity = issueIsoMdl(
            CredentialOfferRuntimeOverrides(
                mapping = buildJsonObject {
                    put("validFrom", JsonPrimitive("<timestamp-before:30d>"))
                    put("validUntil", JsonPrimitive("<timestamp-in:10d>"))
                }
            )
        )
        assertEquals(validity.signed.epochSeconds, validity.validFrom.epochSeconds)
        assertTrue(validity.validUntil - validity.signed in (364.days..366.days))
        assertNull(validity.expectedUpdate)
    }

    private suspend fun ApplicationTestBuilder.issueIsoMdl(
        runtimeOverrides: CredentialOfferRuntimeOverrides? = null,
    ): ValidityInfo {
        val scenario = Issuer2CredentialScenarios.isoMdl
        installIssuer2WithConfigFiles()
        val client = apiClient()
        val walletFlow = Issuer2WalletFlowDriver(client)

        val createdOffer = client.createWalletFlowCredentialOffer(
            scenario = scenario,
            authenticationMethod = AuthenticationMethod.PRE_AUTHORIZED,
            txCodeMode = Issuer2TxCodeMode.NONE,
            runtimeOverrides = runtimeOverrides,
        )
        assertSessionStatus(client, createdOffer.offerId, "ACTIVE")

        val resolvedOffer = walletFlow.resolve(createdOffer)
        val tokenResponse = walletFlow.exchangePreAuthorizedCode(resolvedOffer, txCode = null)
        assertBearerAccessToken(tokenResponse)
        assertRefreshToken(tokenResponse)

        val credentialPayload = walletFlow.requestCredential(
            resolvedOffer = resolvedOffer,
            accessToken = tokenResponse.access_token,
            includeDidInProof = false,
        )
        assertIsoMdlCredentialPayload(credentialPayload)
        assertSessionStatus(client, createdOffer.offerId, "SUCCESSFUL")
        return mdocValidityInfo(credentialPayload)
    }
}
