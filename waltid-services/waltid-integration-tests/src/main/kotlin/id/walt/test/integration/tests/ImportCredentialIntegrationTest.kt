@file:OptIn(ExperimentalUuidApi::class)

package id.walt.test.integration.tests

import id.walt.commons.testing.utils.ServiceTestUtils.loadResource
import id.walt.issuer.issuance.IssuanceRequest
import id.walt.test.integration.loadJsonResource
import id.walt.webwallet.web.model.EmailAccountRequest
import io.klogging.Klogging
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import kotlin.test.*
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private val jwtCredentialRequest = IssuanceRequest(
    issuerKey = loadJsonResource("issuance/key.json"),
    issuerDid = loadResource("issuance/did.txt"),
    credentialConfigurationId = "OpenBadgeCredential_jwt_vc_json",
    credentialData = loadJsonResource("issuance/openbadgecredential.json"),
    mapping = loadJsonResource("issuance/mapping.json")
)

private val sdJwtCredentialRequest = IssuanceRequest(
    issuerKey = loadJsonResource("issuance/key.json"),
    issuerDid = loadResource("issuance/did.txt"),
    credentialConfigurationId = "IdentityCredential_vc+sd-jwt",
    credentialData = loadJsonResource("issuance/identity-credential-no-disclosures.json"),
    mapping = loadJsonResource("issuance/mapping.json")
)

@TestMethodOrder(OrderAnnotation::class)
class ImportCredentialIntegrationTest : AbstractIntegrationTest(), Klogging {

    @Order(1)
    @Test
    fun shouldImportJwtCredential() = runTest {
        // 1. Issue a JWT credential
        val offerUrl = issuerApi.issueJwtCredential(jwtCredentialRequest)
        val claimedCredentials = defaultWalletApi.claimCredential(offerUrl)
        assertEquals(1, claimedCredentials.size)
        val jwt = claimedCredentials[0].document
        val associatedDid = claimedCredentials[0].parsedDocument?.get("sub")?.jsonPrimitive?.content ?: ""

        // 2. Create a new wallet/account for import
        val email = "import-test-jwt-${Uuid.random()}@walt.id"
        val password = "password"
        val newWalletContainer = walletContainerApi.register(EmailAccountRequest(null, email, password))
            .login(EmailAccountRequest(null, email, password))
        val newWalletApi = newWalletContainer.selectDefaultWallet()

        // 3. Import the JWT into the new wallet
        val importedCredential = newWalletApi.importCredential(jwt, associatedDid)
        assertNotNull(importedCredential)
        assertEquals(jwt, importedCredential.document)
        //assertEquals(associatedDid, importedCredential.did) // did is not a field in WalletCredential

        // 4. Verify it's in the list
        val credentials = newWalletApi.listCredentials()
        assertTrue(credentials.any { it.id == importedCredential.id })
    }

    @Order(2)
    @Test
    fun shouldImportSdJwtCredential() = runTest {
        // 1. Issue an SD-JWT credential
        val offerUrl = issuerApi.issueSdJwtCredential(sdJwtCredentialRequest)
        val claimedCredentials = defaultWalletApi.claimCredential(offerUrl)
        assertEquals(1, claimedCredentials.size)
        val sdJwt = claimedCredentials[0].document
        val associatedDid = claimedCredentials[0].parsedDocument?.get("sub")?.jsonPrimitive?.content ?: ""
        assertTrue(sdJwt.contains("~"), "Should be an SD-JWT")

        // 2. Create a new wallet/account for import
        val email = "import-test-sdjwt-${Uuid.random()}@walt.id"
        val password = "password"
        val newWalletContainer = walletContainerApi.register(EmailAccountRequest(null, email, password))
            .login(EmailAccountRequest(null, email, password))
        val newWalletApi = newWalletContainer.selectDefaultWallet()

        // 3. Import the SD-JWT into the new wallet
        val importedCredential = newWalletApi.importCredential(sdJwt, associatedDid)
        assertNotNull(importedCredential)
        assertEquals(sdJwt, importedCredential.document)
        //assertEquals(associatedDid, importedCredential.did) // did is not a field in WalletCredential

        // 4. Verify it's in the list
        val credentials = newWalletApi.listCredentials()
        assertTrue(credentials.any { it.id == importedCredential.id })
    }
}
