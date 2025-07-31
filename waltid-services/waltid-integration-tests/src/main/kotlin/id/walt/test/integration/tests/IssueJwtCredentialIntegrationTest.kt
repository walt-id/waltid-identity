@file:OptIn(ExperimentalUuidApi::class)

package id.walt.test.integration.tests

import id.walt.commons.testing.utils.ServiceTestUtils.loadResource
import id.walt.oid4vc.util.JwtUtils
import id.walt.test.integration.environment.api.issuer.IssuerApi
import id.walt.test.integration.environment.api.wallet.WalletApi
import id.walt.w3c.schemes.JwsSignatureScheme
import id.walt.webwallet.db.models.AccountWalletListing.WalletListing
import io.klogging.Klogging
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import kotlin.test.*
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

@TestMethodOrder(OrderAnnotation::class)
class IssueJwtCredentialIntegrationTest : AbstractIntegrationTest(), Klogging {
    companion object {

        lateinit var issuerApi: IssuerApi
        lateinit var walletApi: WalletApi
        lateinit var wallet: WalletListing

        lateinit var offerUrl: String
        lateinit var credentialId: String

        @JvmStatic
        @BeforeAll
        fun loadWalletAndDefaultDid(): Unit = runBlocking {
            issuerApi = getIssuerApi()
            walletApi = getDefaultAccountWalletApi()
            wallet = walletApi.listAccountWallets().wallets.first()
        }
    }

    @Order(0)
    @Test
    fun shouldIssueCredential() = runTest {
        offerUrl = issuerApi.issueJwtCredential(jwtCredential)
        assertTrue(offerUrl.contains("draft13"))
        assertFalse(offerUrl.contains("draft11"))
    }

    @Order(1)
    @Test
    fun shouldResolveCredentialOffer() = runTest {
        assertNotNull(offerUrl, "The offer URL should be set - test order?")
        val result = walletApi.resolveCredentialOffer(wallet.id, offerUrl)
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
        val claimedCredentials = walletApi.claimCredential(wallet.id, offerUrl)
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
        val credential = walletApi.getCredential(wallet.id, credentialId)
        assertNotNull(credential).also {
            assertEquals(credentialId, credential.id)
        }
    }

    @Order(5)
    @Test
    fun shouldAcceptCredential() = runTest {
        assertNotNull(credentialId, "Credential ID should be set - test order?")
        walletApi.acceptCredential(wallet.id, credentialId)
        logger.info("Credential '$credentialId' accepted")
        val credential = walletApi.getCredential(wallet.id, credentialId)
        assertNotNull(credential)
        // TODO: accept doesn't seem to have an effect on the credential - what should be asserted
    }

    @Order(6)
    @Test
    fun shouldDeleteCredential() = runTest {
        assertNotNull(credentialId, "Credential ID should be set - test order?")
        walletApi.deleteCredential(wallet.id, credentialId)
        logger.info("Credential '$credentialId' deleted")
        val credential = walletApi.getCredential(wallet.id, credentialId)
        assertNotNull(credential.deletedOn)
    }

    @Order(7)
    @Test
    fun shouldRestoreCredential() = runTest {
        assertNotNull(credentialId, "Credential ID should be set - test order?")
        walletApi.restoreCredential(wallet.id, credentialId)
        logger.info("Credential '$credentialId' restored")
        val credential = walletApi.getCredential(wallet.id, credentialId)
        assertNull(credential.deletedOn)
    }


}