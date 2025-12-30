package id.walt.test.integration.tests

import id.walt.commons.testing.utils.ServiceTestUtils.loadResource
import id.walt.issuer.issuance.IssuanceRequest
import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.oid4vc.util.JwtUtils
import id.walt.test.integration.loadJsonResource
import id.walt.w3c.schemes.JwsSignatureScheme
import id.walt.webwallet.performance.Stopwatch
import io.klogging.Klogging
import io.ktor.http.*
import io.ktor.server.util.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import kotlin.test.*
import kotlin.time.DurationUnit
import kotlin.time.toDuration

private const val testOrderCredentialIdErrorMessage = "Credential ID should be set - test order?"

private val jwtCredential = IssuanceRequest(
    issuerKey = loadJsonResource("issuance/key.json"),
    issuerDid = loadResource("issuance/did.txt"),
    credentialConfigurationId = "OpenBadgeCredential_jwt_vc_json",
    credentialData = loadJsonResource("issuance/openbadgecredential.json"),
    mapping = loadJsonResource("issuance/mapping.json")
)

private val simplePresentationRequestPayload =
    loadResource("presentation/openbadgecredential-presentation-request.json")

@Disabled("Only for manual performance testing of the community stack wallet")
@TestMethodOrder(OrderAnnotation::class)
class WalletJwtCredentialPerformanceIntegrationTest : AbstractIntegrationTest(), Klogging {
    companion object {
        var credentialId: String? = null
    }

    @Order(0)
    @Test
    fun shouldIssueCredential() = runTest(timeout = 100.toDuration(DurationUnit.SECONDS)) {
        for (i in 1..300) {
            val offerUrl = issuerApi.issueJwtCredential(jwtCredential).also { offerUrl ->
                assertTrue(offerUrl.contains("draft13"))
                assertFalse(offerUrl.contains("draft11"))
            }
            val result = defaultWalletApi.resolveCredentialOffer(offerUrl)
            assertNotNull(result).also {
                assertNotNull(
                    it["grants"]?.jsonObject["urn:ietf:params:oauth:grant-type:pre-authorized_code"]
                        ?.jsonObject["pre-authorized_code"]?.jsonPrimitive?.content, "no pre-authorized_code"
                )
                assertEquals(
                    "OpenBadgeCredential_jwt_vc_json",
                    it["credential_configuration_ids"]?.jsonArray?.first()?.jsonPrimitive?.content
                )
            }
            val claimedCredentials = defaultWalletApi.claimCredential(offerUrl)
            assertNotNull(claimedCredentials).also {
                assertEquals(1, it.size)
                assertNotNull(it[0]).also { credential ->
                    credentialId = credential.id
                    val document = JwtUtils.parseJWTPayload(credential.document)
                    assertContains(document.keys, JwsSignatureScheme.JwsOption.VC)
                }
            }
        }
    }

    @Order(3)
    @Test
    fun shouldListCredential() = runTest {
        assertNotNull(credentialId, testOrderCredentialIdErrorMessage)
        val credentials = defaultWalletApi.listCredentials()
        assertTrue(credentials.any { it.id == credentialId }, "Could not list credential with id $credentialId")
    }

    @Order(10)
    @Test
    fun shouldMatchCredential() = runTest(timeout = 100.toDuration(DurationUnit.SECONDS)) {
        assertNotNull(credentialId, testOrderCredentialIdErrorMessage)
        val verificationUrl = verifierApi.verify(simplePresentationRequestPayload)
        val verificationId = Url(verificationUrl).parameters.getOrFail("state")

        defaultWalletApi.resolvePresentationRequest(verificationUrl)
        val resolvedPresentationOfferString =
            defaultWalletApi.resolvePresentationRequest(verificationUrl)
        val presentationDefinition =
            Url(resolvedPresentationOfferString).parameters.getOrFail("presentation_definition")

        val verifierSessionInfo = verifierApi.getSession(verificationId)
        assertEquals(
            PresentationDefinition.fromJSONString(presentationDefinition),
            verifierSessionInfo.presentationDefinition
        )

        for (i in 1..1000) {
            val matchedCredentials =
                defaultWalletApi.matchCredentialsForPresentationDefinition(presentationDefinition)
            assertNotNull(matchedCredentials).also {
                assertTrue(it.size > 1)
            }
        }
        val report = Stopwatch.report()
        logger.error("==============================================================================================")
        report.forEach { rl ->
            logger.error(
                "{mark}: count: {count} total: {total} min: {min} max: {max} avg: {avg}",
                rl.key,
                rl.value.count,
                rl.value.total,
                rl.value.min,
                rl.value.max,
                rl.value.average
            )
        }
        logger.error("==============================================================================================")
    }
}
