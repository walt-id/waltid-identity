@file:OptIn(ExperimentalUuidApi::class)

package id.walt.test.integration.tests

import id.walt.commons.testing.utils.ServiceTestUtils.loadResource
import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.oid4vc.util.JwtUtils
import id.walt.test.integration.environment.api.issuer.IssuerApi
import id.walt.test.integration.environment.api.verifier.VerifierApi
import id.walt.test.integration.environment.api.wallet.WalletApi
import id.walt.test.integration.expectLooksLikeJwt
import id.walt.w3c.schemes.JwsSignatureScheme
import id.walt.webwallet.db.models.AccountWalletListing.WalletListing
import id.walt.webwallet.service.credentials.CredentialFilterObject
import id.walt.webwallet.web.controllers.exchange.UsePresentationRequest
import io.klogging.Klogging
import io.ktor.http.*
import io.ktor.server.util.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import kotlin.collections.forEach
import kotlin.test.*
import kotlin.text.contains
import kotlin.uuid.ExperimentalUuidApi

private val issuerKey = loadResource("issuance/key.json")
private val issuerDid = loadResource("issuance/did.txt")
private val openBadgeCredentialData = loadResource("issuance/openbadgecredential.json")
private val credentialMapping = loadResource("issuance/mapping.json")
private val jwtCredential = buildJsonObject {
    put("issuerKey", Json.decodeFromString<JsonElement>(issuerKey))
    put("issuerDid", issuerDid)
    put("credentialConfigurationId", "OpenBadgeCredential_jwt_vc_json")
    put("credentialData", Json.decodeFromString<JsonElement>(openBadgeCredentialData))
    put("mapping", Json.decodeFromString<JsonElement>(credentialMapping))
}

private val simplePresentationRequestPayload =
    loadResource("presentation/openbadgecredential-presentation-request.json")


// TODO: space in category name wrong handled -> needs a fix
private val categoryNames = listOf("The-Category-A", "The-fancy-Category-B")

@TestMethodOrder(OrderAnnotation::class)
class IssueJwtCredentialIntegrationTest : AbstractIntegrationTest(), Klogging {
    companion object {

        lateinit var walletApi: WalletApi
        lateinit var issuerApi: IssuerApi
        lateinit var verifierApi: VerifierApi
        lateinit var wallet: WalletListing

        var offerUrl: String? = null
        var credentialId: String? = null

        @JvmStatic
        @BeforeAll
        fun loadWalletAndDefaultDid(): Unit = runBlocking {
            walletApi = getDefaultAccountWalletApi()
            issuerApi = getIssuerApi()
            verifierApi = getVerifierApi()
            wallet = walletApi.listAccountWallets().wallets.first()
        }

        @JvmStatic
        @AfterAll
        fun deleteAllCategoriesAndCredential(): Unit = runBlocking {
            categoryNames.forEach {
                walletApi.deleteCategory(wallet.id, it)
            }
            credentialId?.let {
                walletApi.deleteCredential(wallet.id, it)
            }
        }
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
        assertNotNull(offerUrl, "The offer URL should be set - test order?")
        val result = walletApi.resolveCredentialOffer(wallet.id, offerUrl!!)
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
        assertNotNull(offerUrl, "The offer URL should be set - test order?")
        val claimedCredentials = walletApi.claimCredential(wallet.id, offerUrl!!)
        assertNotNull(claimedCredentials).also {
            assertEquals(1, it.size)
            assertNotNull(it.get(0)).also { credential ->
                credentialId = credential.id
                val document = JwtUtils.parseJWTPayload(credential.document)
                assertContains(document.keys, JwsSignatureScheme.JwsOption.VC)
            }
        }
    }

    @Order(3)
    @Test
    fun shouldListCredential() = runTest {
        assertNotNull(credentialId, "Credential ID should be set - test order?")
        val credentials = walletApi.listCredentials(wallet.id)
        assertTrue(credentials.any { it.id == credentialId }, "Could not list credential with id $credentialId")
    }

    @Order(4)
    @Test
    fun shouldGetCredential() = runTest {
        assertNotNull(credentialId, "Credential ID should be set - test order?")
        val credential = walletApi.getCredential(wallet.id, credentialId!!)
        assertNotNull(credential).also {
            assertEquals(credentialId, credential.id)
        }
    }

    @Order(5)
    @Test
    fun shouldAcceptCredential() = runTest {
        assertNotNull(credentialId, "Credential ID should be set - test order?")
        walletApi.acceptCredential(wallet.id, credentialId!!)
        logger.info("Credential '$credentialId' accepted")
        val credential = walletApi.getCredential(wallet.id, credentialId!!)
        assertNotNull(credential)
        // TODO: accept doesn't seem to have an effect on the credential - what should be asserted
    }

    @Order(6)
    @Test
    fun shouldDeleteCredential() = runTest {
        assertNotNull(credentialId, "Credential ID should be set - test order?")
        walletApi.deleteCredential(wallet.id, credentialId!!)
        logger.info("Credential '$credentialId' deleted")
        val credential = walletApi.getCredential(wallet.id, credentialId!!)
        assertNotNull(credential.deletedOn)
    }

    @Order(7)
    @Test
    fun shouldRestoreCredential() = runTest {
        assertNotNull(credentialId, "Credential ID should be set - test order?")
        walletApi.restoreCredential(wallet.id, credentialId!!)
        logger.info("Credential '$credentialId' restored")
        val credential = walletApi.getCredential(wallet.id, credentialId!!)
        assertNull(credential.deletedOn)
    }

    @Order(8)
    @Test
    fun shouldAttachCategoryToCredential() = runTest {
        assertNotNull(credentialId, "Credential ID should be set - test order?")
        // TODO: space not supported in category, needs to be fixed
        categoryNames.forEach { category ->
            walletApi.createCategory(wallet.id, category)
        }
        val categoriesRetrieved = walletApi.listCategories(wallet.id)
        assertNotNull(categoriesRetrieved)
        walletApi.attachCategoriesToCredential(wallet.id, credentialId!!, *categoryNames.toTypedArray())
        assertEquals(
            1,
            walletApi.listCredentials(
                wallet.id,
                CredentialFilterObject.default.copy(categories = categoryNames)
            ).size
        )
        //TODO: no categories on the credential when I do a get
    }

    @Disabled("Can still find credential with category specified")
    @Order(9)
    @Test
    fun shouldDetachCategoryFromCredential() = runTest {
        assertNotNull(credentialId, "Credential ID should be set - test order?")
        walletApi.detachCategoriesFromCredential(wallet.id, credentialId!!, *categoryNames.toTypedArray())
        assertEquals(
            0,
            walletApi.listCredentials(
                wallet.id,
                CredentialFilterObject.default.copy(categories = categoryNames)
            ).size
        )
    }

    @Order(10)
    @Test
    fun shouldVerify() = runTest {
        assertNotNull(credentialId, "Credential ID should be set - test order?")
        val verificationUrl = verifierApi.verify(simplePresentationRequestPayload)
        val verificationId = Url(verificationUrl).parameters.getOrFail("state")

        walletApi.resolvePresentationRequest(wallet.id, verificationUrl)
        val resolvedPresentationOfferString = walletApi.resolvePresentationRequest(wallet.id, verificationUrl)
        val presentationDefinition =
            Url(resolvedPresentationOfferString).parameters.getOrFail("presentation_definition")

        val verifierSessionInfo = verifierApi.getSession(verificationId)
        assertEquals(
            PresentationDefinition.fromJSONString(presentationDefinition),
            verifierSessionInfo.presentationDefinition
        )

        val matchedCredentials = walletApi.matchCredentialsForPresentationDefinition(wallet.id, presentationDefinition)
        assertNotNull(matchedCredentials).also {
            assertEquals(1, it.size)
            assertEquals(credentialId, matchedCredentials.first().id)
        }


        val defaultDidString = walletApi.getDefaultDid(wallet.id).did
        val notMatchedCredentials =
            walletApi.unmatchedCredentialsForPresentationDefinition(wallet.id, presentationDefinition)
        assertTrue(notMatchedCredentials.isEmpty())
        walletApi.usePresentationRequest(
            wallet.id,
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