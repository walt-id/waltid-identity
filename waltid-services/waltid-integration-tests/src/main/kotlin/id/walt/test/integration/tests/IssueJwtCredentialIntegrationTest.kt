@file:OptIn(ExperimentalUuidApi::class)

package id.walt.test.integration.tests

import id.walt.commons.testing.utils.ServiceTestUtils.loadResource
import id.walt.issuer.issuance.IssuanceRequest
import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.oid4vc.util.JwtUtils
import id.walt.test.integration.expectLooksLikeJwt
import id.walt.test.integration.loadJsonResource
import id.walt.w3c.schemes.JwsSignatureScheme
import id.walt.webwallet.service.credentials.CredentialFilterObject
import id.walt.webwallet.web.controllers.exchange.UsePresentationRequest
import io.klogging.Klogging
import io.ktor.http.*
import io.ktor.server.util.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import kotlin.test.*
import kotlin.uuid.ExperimentalUuidApi

private const val testOrderOfferUrlErrorMessage = "The offer URL should be set - test order?"
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

// TODO: space in category name wrong handled -> needs a fix
private val categoryNames = listOf("The-Category-A", "The-fancy-Category-B")

@TestMethodOrder(OrderAnnotation::class)
class IssueJwtCredentialIntegrationTest : AbstractIntegrationTest(), Klogging {
    companion object {

        var offerUrl: String? = null
        var credentialId: String? = null
    }

    @Order(0)
    @Test
    fun shouldIssueCredential() = runTest {
        offerUrl = issuerApi.issueJwtCredential(jwtCredential).also { offerUrl ->
            assertTrue(offerUrl.contains("draft13"))
            assertFalse(offerUrl.contains("draft11"))
        }
    }

    @Order(1)
    @Test
    fun shouldResolveCredentialOffer() = runTest {
        assertNotNull(offerUrl, testOrderOfferUrlErrorMessage)
        val result = defaultWalletApi.resolveCredentialOffer(offerUrl!!)
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
    }

    @Order(2)
    @Test
    fun shouldClaimCredential() = runTest {
        assertNotNull(offerUrl, testOrderOfferUrlErrorMessage)
        val claimedCredentials = defaultWalletApi.claimCredential(offerUrl!!)
        assertNotNull(claimedCredentials).also {
            assertEquals(1, it.size)
            assertNotNull(it[0]).also { credential ->
                credentialId = credential.id
                val document = JwtUtils.parseJWTPayload(credential.document)
                assertContains(document.keys, JwsSignatureScheme.JwsOption.VC)
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

    @Order(4)
    @Test
    fun shouldGetCredential() = runTest {
        assertNotNull(credentialId, testOrderCredentialIdErrorMessage)
        val credential = defaultWalletApi.getCredential(credentialId!!)
        assertNotNull(credential).also {
            assertEquals(credentialId, credential.id)
        }
    }

    @Order(5)
    @Test
    fun shouldAcceptCredential() = runTest {
        assertNotNull(credentialId, testOrderCredentialIdErrorMessage)
        defaultWalletApi.acceptCredential(credentialId!!)
        logger.info("Credential '$credentialId' accepted")
        val credential = defaultWalletApi.getCredential(credentialId!!)
        assertNotNull(credential)
        // TODO: this is only used, when the credential is claimed with requireUserInput = true
        // TODO: write test for that usecase
        // TODO: test reject credential
    }

    @Order(6)
    @Test
    fun shouldDeleteCredential() = runTest {
        assertNotNull(credentialId, testOrderCredentialIdErrorMessage)
        defaultWalletApi.deleteCredential(credentialId!!)
        logger.info("Credential '$credentialId' deleted")
        val credential = defaultWalletApi.getCredential(credentialId!!)
        assertNotNull(credential.deletedOn)
    }

    @Order(7)
    @Test
    fun shouldRestoreCredential() = runTest {
        assertNotNull(credentialId, testOrderCredentialIdErrorMessage)
        defaultWalletApi.restoreCredential(credentialId!!)
        logger.info("Credential '$credentialId' restored")
        val credential = defaultWalletApi.getCredential(credentialId!!)
        assertNull(credential.deletedOn)
    }

    @Order(8)
    @Test
    fun shouldAttachCategoryToCredential() = runTest {
        assertNotNull(credentialId, testOrderCredentialIdErrorMessage)
        // TODO: space not supported in category, needs to be fixed
        categoryNames.forEach { category ->
            defaultWalletApi.createCategory(category)
        }
        val categoriesRetrieved = defaultWalletApi.listCategories()
        assertNotNull(categoriesRetrieved)
        defaultWalletApi.attachCategoriesToCredential(credentialId!!, *categoryNames.toTypedArray())

        val credentialsList = defaultWalletApi.listCredentials(
            CredentialFilterObject.default.copy(categories = categoryNames)
        )
        assertEquals(1, credentialsList.size)
        //TODO: no categories on the credential when I do a get
    }

    @Disabled("Can still find credential with category specified")
    @Order(9)
    @Test
    fun shouldDetachCategoryFromCredential() = runTest {
        assertNotNull(credentialId, testOrderCredentialIdErrorMessage)
        defaultWalletApi.detachCategoriesFromCredential(credentialId!!, *categoryNames.toTypedArray())
        assertEquals(
            0,
            defaultWalletApi.listCredentials(
                CredentialFilterObject.default.copy(categories = categoryNames)
            ).size
        )
    }

    @Order(10)
    @Test
    fun shouldVerify() = runTest {
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

        val matchedCredentials =
            defaultWalletApi.matchCredentialsForPresentationDefinition(presentationDefinition)
        assertNotNull(matchedCredentials).also {
            assertEquals(1, it.size)
            assertEquals(credentialId, matchedCredentials.first().id)
        }


        val defaultDidString = defaultWalletApi.getDefaultDid().did
        val notMatchedCredentials =
            defaultWalletApi.unmatchedCredentialsForPresentationDefinition(presentationDefinition)
        assertTrue(notMatchedCredentials.isEmpty())
        defaultWalletApi.usePresentationRequest(
            UsePresentationRequest(defaultDidString, resolvedPresentationOfferString, listOf(credentialId!!))
        )

        verifierApi.getSession(verificationId).also {
            assertNotNull(
                it.tokenResponse?.vpToken?.jsonPrimitive?.contentOrNull?.expectLooksLikeJwt(),
                "Received no valid token response!"
            )
            assertNotNull(
                it.tokenResponse?.presentationSubmission,
                "should have a presentation submission after submission"
            )

            assertEquals(true, it.verificationResult, "overall verification should be valid")
            it.policyResults.let { policyResults ->
                assertNotNull(policyResults, "policyResults should be available after running policies")
                assertTrue(policyResults.size > 1, "no policies have run")
                assertNotNull(policyResults["results"]?.jsonArray) { r ->
                    r.map { it.jsonObject }.forEach { credentialPolicyResult ->
                        assertNotNull(credentialPolicyResult["policyResults"]?.jsonArray).forEach { pr ->
                            assertEquals(
                                "true",
                                pr.jsonObject["is_success"]?.jsonPrimitive?.content,
                                "policyResult should be success"
                            )
                        }
                    }
                }
            }
        }
    }
}