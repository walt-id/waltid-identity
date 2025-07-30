import id.walt.commons.testing.E2ETest
import id.walt.commons.testing.utils.ServiceTestUtils.loadResource
import id.walt.issuer.issuance.IssuanceRequest
import id.walt.oid4vc.OpenID4VCI
import id.walt.oid4vc.OpenID4VCI.resolveCIProviderMetadata
import id.walt.oid4vc.data.CredentialOffer
import id.walt.webwallet.db.models.WalletCredential
import io.ktor.client.*
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class BatchIssuance(private val e2e: E2ETest, val client: HttpClient, val wallet: Uuid) {
    private val issuerApi = IssuerApi(e2e, client)
    private val exchangeApi = ExchangeApi(e2e, client)

    private val expectedMortgageAndVerifiableIdCredentialConfigurationIds = buildJsonArray {
        add("MortgageEligibility_jwt_vc_json")
        add("VerifiableId_jwt_vc_json")
    }

    private val expectedMortgageAndVerifiableIdCredentialDefinitionTypes = listOf(
        setOf("VerifiableCredential", "VerifiableAttestation", "VerifiableId", "MortgageEligibility"),
        setOf("VerifiableCredential", "VerifiableAttestation", "VerifiableId")
    )

    private val mortgageEligibilityAndVerifiableIdIssuanceRequest = Json.decodeFromString<List<IssuanceRequest>>(
        loadResource("issuance/batch-mortgageeligibility-verifiableId-credential-issuance-request.json")
    )

    private val mortgageEligibilityAndVerifiableIdAndPassportIssuanceRequest = Json.decodeFromString<List<IssuanceRequest>>(
        loadResource("issuance/batch-mortgageeligibility-verifiableId-passport-credential-issuance-request.json")
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

    private fun extractTypes(parsedDocument: JsonObject): Set<String> {
        return (parsedDocument["type"] as? JsonArray)
            ?.map { it.toString().trim('"') }
            ?.toSet() ?: emptySet()
    }

    suspend fun runTests() {
        runWithMortgageAndIdCredential()
        runWithMortgageAndIdAndPassportCredential()
    }

    private suspend fun runWithMortgageAndIdCredential() {
        lateinit var credentialOfferUrl: String
        lateinit var receivedCredentials: List<WalletCredential>

        issuerApi.issueJwtBatch(
            mortgageEligibilityAndVerifiableIdIssuanceRequest
        ) {
            credentialOfferUrl = it
        }

        // Assert Offer
        val credentialOffer =
            OpenID4VCI.parseAndResolveCredentialOfferRequestUrl(credentialOfferUrl) as CredentialOffer.Draft13

        assertEquals(expectedMortgageAndVerifiableIdCredentialConfigurationIds, credentialOffer.credentialConfigurationIds)

        // Assert Credential
        val providerMetadata = resolveCIProviderMetadata(credentialOffer)

        val offeredCredentials = OpenID4VCI.resolveOfferedCredentials(credentialOffer, providerMetadata)
        assertEquals(2, offeredCredentials.size, "There should be exactly 2 offered credentials")

        val actualCredentialDefinitionTypes = expectedMortgageAndVerifiableIdCredentialDefinitionTypes.toMutableList()

        offeredCredentials.forEach { offered ->
            val actualTypes = offered.credentialDefinition!!.type!!.toSet()
            val matched = actualCredentialDefinitionTypes.removeIf { it == actualTypes }
            assertTrue(matched, "Unexpected or duplicate CredentialDefinition.type: $actualTypes")
        }

        assertTrue(actualCredentialDefinitionTypes.isEmpty(), "Some expected types were not matched: $actualCredentialDefinitionTypes")


        exchangeApi.resolveCredentialOffer(wallet, credentialOfferUrl)

        exchangeApi.useOfferRequest(wallet, credentialOfferUrl, 2) {
            receivedCredentials = it
        }

        assertEquals(2, receivedCredentials.size, "Expected exactly 2 credentials")

        val actualCredentialDefinitionTypeSets = receivedCredentials.map { extractTypes(it.parsedDocument!!) }.toMutableList()
        val expectedCredentialDefinitionTypeSets = expectedMortgageAndVerifiableIdCredentialDefinitionTypes.toMutableList()

        for (actual in actualCredentialDefinitionTypeSets) {
            val matched = expectedCredentialDefinitionTypeSets.removeIf { it == actual }
            assertTrue(matched, "Unexpected types found in credential: $actual")
        }

        assertTrue(
            expectedCredentialDefinitionTypeSets.isEmpty(),
            "Not all expected types were matched: expectedCredentialDefinitionTypeSets"
        )
    }

    private suspend fun runWithMortgageAndIdAndPassportCredential() {
        lateinit var credentialOfferUrl: String
        lateinit var receivedCredentials: List<WalletCredential>

        issuerApi.issueJwtBatch(
            mortgageEligibilityAndVerifiableIdAndPassportIssuanceRequest
        ) {
            credentialOfferUrl = it
        }

        // Assert Offer
        val credentialOffer =
            OpenID4VCI.parseAndResolveCredentialOfferRequestUrl(credentialOfferUrl) as CredentialOffer.Draft13

        assertEquals(expectedMortgageAndVerifiableIdAndPassportCredentialConfigurationIds, credentialOffer.credentialConfigurationIds)

        // Assert Credential
        val providerMetadata = resolveCIProviderMetadata(credentialOffer)

        val offeredCredentials = OpenID4VCI.resolveOfferedCredentials(credentialOffer, providerMetadata)
        assertEquals(3, offeredCredentials.size, "There should be exactly 3 offered credentials")

        val actualCredentialDefinitionTypes = expectedMortgageAndVerifiableIdAndPassportCredentialDefinitionTypes.toMutableList()

        offeredCredentials.forEach { offered ->
            val actualTypes = offered.credentialDefinition!!.type!!.toSet()
            val matched = actualCredentialDefinitionTypes.removeIf { it == actualTypes }
            assertTrue(matched, "Unexpected or duplicate CredentialDefinition.type: $actualTypes")
        }

        assertTrue(actualCredentialDefinitionTypes.isEmpty(), "Some expected types were not matched: $actualCredentialDefinitionTypes")

        exchangeApi.resolveCredentialOffer(wallet, credentialOfferUrl)

        exchangeApi.useOfferRequest(wallet, credentialOfferUrl, 3) {
            receivedCredentials = it
        }

        assertEquals(3, receivedCredentials.size, "Expected exactly 3 credentials")

        val actualCredentialDefinitionTypeSets = receivedCredentials.map { extractTypes(it.parsedDocument!!) }.toMutableList()
        val expectedCredentialDefinitionTypeSets = expectedMortgageAndVerifiableIdAndPassportCredentialDefinitionTypes.toMutableList()

        for (actual in actualCredentialDefinitionTypeSets) {
            val matched = expectedCredentialDefinitionTypeSets.removeIf { it == actual }
            assertTrue(matched, "Unexpected types found in credential: $actual")
        }

        assertTrue(
            expectedCredentialDefinitionTypeSets.isEmpty(),
            "Not all expected types were matched: $expectedCredentialDefinitionTypeSets"
        )
    }


}
