@file:OptIn(ExperimentalUuidApi::class)

package id.walt.test.integration.tests

import id.walt.commons.testing.utils.ServiceTestUtils.loadResource
import id.walt.issuer.issuance.IssuanceRequest
import id.walt.oid4vc.OpenID4VCI
import id.walt.oid4vc.data.CredentialOffer
import io.klogging.Klogging
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi

private val mortgageEligibilityAndVerifiableIdIssuanceRequest = Json.decodeFromString<List<IssuanceRequest>>(
    loadResource("issuance/batch-mortgageeligibility-verifiableId-credential-issuance-request.json")
)

private val mortgageEligibilityAndVerifiableIdAndPassportIssuanceRequest = Json.decodeFromString<List<IssuanceRequest>>(
    loadResource("issuance/batch-mortgageeligibility-verifiableId-passport-credential-issuance-request.json")
)

private val expectedMortgageAndVerifiableIdCredentialConfigurationIds = buildJsonArray {
    add("MortgageEligibility_jwt_vc_json")
    add("VerifiableId_jwt_vc_json")
}

private val expectedMortgageAndVerifiableIdCredentialDefinitionTypes = listOf(
    setOf("VerifiableCredential", "VerifiableAttestation", "VerifiableId", "MortgageEligibility"),
    setOf("VerifiableCredential", "VerifiableAttestation", "VerifiableId")
)

private val expectedMortgageAndVerifiableIdAndPassportCredentialConfigurationIds = buildJsonArray {
    add("MortgageEligibility_jwt_vc_json")
    add("VerifiableId_jwt_vc_json")
    add("PassportCh_jwt_vc_json")
}

private val expectedMortgageAndVerifiableIdAndPassportCredentialDefinitionTypes = listOf(
    setOf("VerifiableCredential", "VerifiableAttestation", "VerifiableId", "MortgageEligibility"),
    setOf("VerifiableCredential", "VerifiableAttestation", "VerifiableId", "PassportCh"),
    setOf("VerifiableCredential", "VerifiableAttestation", "VerifiableId")
)

@TestMethodOrder(OrderAnnotation::class)
class BatchIssuanceIntegrationTest : AbstractIntegrationTest(), Klogging {
    companion object {
        var twoCredentialOfferUrl: String? = null
        var threeCredentialOfferUrl: String? = null
    }

    private fun extractTypes(parsedDocument: JsonObject): Set<String> {
        return (parsedDocument["type"] as? JsonArray)
            ?.map { it.toString().trim('"') }
            ?.toSet() ?: emptySet()
    }

    @Order(0)
    @Test
    fun shouldIssueBatchOfTwoCredentials() = runTest {
        twoCredentialOfferUrl = issuerApi.issueJwtBatchCredential(mortgageEligibilityAndVerifiableIdIssuanceRequest)
        assertNotNull(twoCredentialOfferUrl)
        logger.info("Batch credential offer URL (2 credentials) created")
    }

    @Order(1)
    @Test
    fun shouldVerifyTwoCredentialOffer() = runTest {
        assertNotNull(twoCredentialOfferUrl, "Offer URL should be set - test order?")
        
        val credentialOffer = OpenID4VCI.parseAndResolveCredentialOfferRequestUrl(twoCredentialOfferUrl!!) as CredentialOffer.Draft13
        assertEquals(expectedMortgageAndVerifiableIdCredentialConfigurationIds, credentialOffer.credentialConfigurationIds)
        
        val providerMetadata = OpenID4VCI.resolveCIProviderMetadata(credentialOffer)
        val offeredCredentials = OpenID4VCI.resolveOfferedCredentials(credentialOffer, providerMetadata)
        assertEquals(2, offeredCredentials.size, "There should be exactly 2 offered credentials")
        
        val actualCredentialDefinitionTypes = expectedMortgageAndVerifiableIdCredentialDefinitionTypes.toMutableList()
        offeredCredentials.forEach { offered ->
            val actualTypes = offered.credentialDefinition!!.type!!.toSet()
            val matched = actualCredentialDefinitionTypes.removeIf { it == actualTypes }
            assertTrue(matched, "Unexpected or duplicate CredentialDefinition.type: $actualTypes")
        }
        assertTrue(actualCredentialDefinitionTypes.isEmpty(), "Some expected types were not matched: $actualCredentialDefinitionTypes")
    }

    @Order(2)
    @Test
    fun shouldClaimBatchOfTwoCredentials() = runTest {
        assertNotNull(twoCredentialOfferUrl, "Offer URL should be set - test order?")
        
        defaultWalletApi.resolveCredentialOffer(twoCredentialOfferUrl!!)
        val receivedCredentials = defaultWalletApi.claimCredential(twoCredentialOfferUrl!!)
        
        assertEquals(2, receivedCredentials.size, "Expected exactly 2 credentials")
        
        val actualCredentialDefinitionTypeSets = receivedCredentials.map { extractTypes(it.parsedDocument!!) }.toMutableList()
        val expectedCredentialDefinitionTypeSets = expectedMortgageAndVerifiableIdCredentialDefinitionTypes.toMutableList()
        
        for (actual in actualCredentialDefinitionTypeSets) {
            val matched = expectedCredentialDefinitionTypeSets.removeIf { it == actual }
            assertTrue(matched, "Unexpected types found in credential: $actual")
        }
        assertTrue(expectedCredentialDefinitionTypeSets.isEmpty(), "Not all expected types were matched")
    }

    @Order(3)
    @Test
    fun shouldIssueBatchOfThreeCredentials() = runTest {
        threeCredentialOfferUrl = issuerApi.issueJwtBatchCredential(mortgageEligibilityAndVerifiableIdAndPassportIssuanceRequest)
        assertNotNull(threeCredentialOfferUrl)
        logger.info("Batch credential offer URL (3 credentials) created")
    }

    @Order(4)
    @Test
    fun shouldVerifyThreeCredentialOffer() = runTest {
        assertNotNull(threeCredentialOfferUrl, "Offer URL should be set - test order?")
        
        val credentialOffer = OpenID4VCI.parseAndResolveCredentialOfferRequestUrl(threeCredentialOfferUrl!!) as CredentialOffer.Draft13
        assertEquals(expectedMortgageAndVerifiableIdAndPassportCredentialConfigurationIds, credentialOffer.credentialConfigurationIds)
        
        val providerMetadata = OpenID4VCI.resolveCIProviderMetadata(credentialOffer)
        val offeredCredentials = OpenID4VCI.resolveOfferedCredentials(credentialOffer, providerMetadata)
        assertEquals(3, offeredCredentials.size, "There should be exactly 3 offered credentials")
        
        val actualCredentialDefinitionTypes = expectedMortgageAndVerifiableIdAndPassportCredentialDefinitionTypes.toMutableList()
        offeredCredentials.forEach { offered ->
            val actualTypes = offered.credentialDefinition!!.type!!.toSet()
            val matched = actualCredentialDefinitionTypes.removeIf { it == actualTypes }
            assertTrue(matched, "Unexpected or duplicate CredentialDefinition.type: $actualTypes")
        }
        assertTrue(actualCredentialDefinitionTypes.isEmpty(), "Some expected types were not matched: $actualCredentialDefinitionTypes")
    }

    @Order(5)
    @Test
    fun shouldClaimBatchOfThreeCredentials() = runTest {
        assertNotNull(threeCredentialOfferUrl, "Offer URL should be set - test order?")
        
        defaultWalletApi.resolveCredentialOffer(threeCredentialOfferUrl!!)
        val receivedCredentials = defaultWalletApi.claimCredential(threeCredentialOfferUrl!!)
        
        assertEquals(3, receivedCredentials.size, "Expected exactly 3 credentials")
        
        val actualCredentialDefinitionTypeSets = receivedCredentials.map { extractTypes(it.parsedDocument!!) }.toMutableList()
        val expectedCredentialDefinitionTypeSets = expectedMortgageAndVerifiableIdAndPassportCredentialDefinitionTypes.toMutableList()
        
        for (actual in actualCredentialDefinitionTypeSets) {
            val matched = expectedCredentialDefinitionTypeSets.removeIf { it == actual }
            assertTrue(matched, "Unexpected types found in credential: $actual")
        }
        assertTrue(expectedCredentialDefinitionTypeSets.isEmpty(), "Not all expected types were matched: $expectedCredentialDefinitionTypeSets")
    }
}
