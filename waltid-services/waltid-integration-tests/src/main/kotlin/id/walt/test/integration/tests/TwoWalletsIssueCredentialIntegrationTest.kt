@file:OptIn(ExperimentalUuidApi::class)

package id.walt.test.integration.tests

import id.walt.commons.testing.utils.ServiceTestUtils.loadResource
import id.walt.crypto.utils.JsonUtils.toJsonElement
import id.walt.issuer.issuance.IssuanceRequest
import id.walt.test.integration.assertContainsPresentationDefinitionUri
import id.walt.test.integration.environment.api.wallet.WalletApi
import id.walt.test.integration.environment.api.wallet.WalletContainerApi
import id.walt.test.integration.expectError
import id.walt.test.integration.loadJsonResource
import id.walt.w3c.utils.VCFormat
import id.walt.webwallet.web.controllers.exchange.UsePresentationRequest
import id.walt.webwallet.web.model.EmailAccountRequest
import io.ktor.http.*
import io.ktor.server.util.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import kotlin.test.assertEquals
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private val accountA = EmailAccountRequest(
    name = "userA",
    email = "user-a@walt.id",
    password = "passw0rd-a",
)

private val accountB = EmailAccountRequest(
    name = "userB",
    email = "user-b@walt.id",
    password = "passw0rd-b",
)

private val issuerKey = loadJsonResource("issuance/key.json")
private val issuerDid = loadResource("issuance/did.txt")


/**
 * Test to reproduce https://linear.app/walt-new/issue/WAL-199/wallet-vp-flow-does-not-respect-account-boundaries
 */
@TestMethodOrder(OrderAnnotation::class)
class TwoWalletsIssueCredentialIntegrationTest : AbstractIntegrationTest() {

    companion object {
        lateinit var walletA: WalletApi
        lateinit var walletContainerA: WalletContainerApi
        lateinit var walletB: WalletApi
        lateinit var walletContainerB: WalletContainerApi
    }

    @Test
    @Order(0)
    fun setupWallets() = runTest {
        walletContainerApi.register(accountA)
        walletContainerApi.register(accountB)
        walletContainerA = walletContainerApi.login(accountA)
        assertEquals(1, walletContainerA.listAccountWallets().wallets.size)
        walletA = walletContainerA.selectDefaultWallet()
        walletContainerB = walletContainerApi.login(accountB)
        walletB = walletContainerB.selectDefaultWallet()
        walletContainerB.listAccountWallets().let {
            assertEquals(1, it.wallets.size)
        }

    }

    @Test
    @Order(1)
    fun shouldClaimCredentials() = runTest {
        val credentialData = loadJsonResource("issuance/openbadgecredential.json")
            .toMutableMap()
            .apply {
                put("id", "TEST".toJsonElement())
            }.toJsonElement().jsonObject

        val offerUrlA = issuerApi.issueJwtCredential(
            IssuanceRequest(
                issuerKey = issuerKey,
                issuerDid = issuerDid,
                credentialConfigurationId = "OpenBadgeCredential_jwt_vc_json",
                credentialData = credentialData,
                mapping = loadJsonResource("issuance/mapping-without-id.json")
            )
        )
        walletA.claimCredential(offerUrlA)
        walletA.listCredentials().also {
            assertEquals(1, it.size, "Wallet A should have one credential")
        }
        val offerUrlB = issuerApi.issueJwtCredential(
            IssuanceRequest(
                issuerKey = issuerKey,
                issuerDid = issuerDid,
                credentialConfigurationId = "OpenBadgeCredential_jwt_vc_json",
                credentialData = credentialData,
                mapping = loadJsonResource("issuance/mapping-without-id.json")
            )
        )
        walletB.claimCredential(offerUrlB)
        walletB.listCredentials().also {
            assertEquals(1, it.size, "Wallet B should have one credential")
        }
    }

    @Test
    @Order(3)
    fun shouldPresentOnlyHisOwnCredentialToVerifier() = runTest {
        val credentialA = walletA.listCredentials().first()
        val credentialB = walletB.listCredentials().first()
        val sessionId = Uuid.random().toString()
        val openBadgeNoDisclosurePresentationRequest = buildJsonObject {
            put("request_credentials", buildJsonArray {
                addJsonObject {
                    put("format", VCFormat.jwt_vc_json.toJsonElement())
                    put("type", "OpenBadgeCredential".toJsonElement())
                }
            })
        }

        val presentationUrlA = verifierApi.verify(openBadgeNoDisclosurePresentationRequest, sessionId)
            .assertContainsPresentationDefinitionUri()

        val resolvedPresentationOfferStringA =
            walletA.resolvePresentationRequest(presentationUrlA)
        val presentationDefinitionA =
            Url(resolvedPresentationOfferStringA).parameters.getOrFail("presentation_definition")
        val matchedCredentials = walletA.matchCredentialsForPresentationDefinition(presentationDefinitionA)
        assertEquals(1, matchedCredentials.size)
        matchedCredentials.first().also {
            assertEquals(walletA.walletId, it.wallet)
            assertEquals(credentialA.id, it.id)
        }

        walletA.usePresentationRequest(
            UsePresentationRequest(
                presentationRequest = presentationUrlA,
                selectedCredentials = listOf(credentialA.id, credentialB.id),
            )
        )
    }

    @Test
    @Order(4)
    fun shouldNotBeAbleToTakeForeignWallet() = runTest {
        val evilWallet = walletContainerA.selectWallet(walletB.walletId)
        evilWallet.listCredentialsRaw().also { response ->
            response.expectError()
        }
        val sessionId = Uuid.random().toString()
        val openBadgeNoDisclosurePresentationRequest = buildJsonObject {
            put("request_credentials", buildJsonArray {
                addJsonObject {
                    put("format", VCFormat.jwt_vc_json.toJsonElement())
                    put("type", "OpenBadgeCredential".toJsonElement())
                }
            })
        }

        val presentationUrl = verifierApi.verify(openBadgeNoDisclosurePresentationRequest, sessionId)
            .assertContainsPresentationDefinitionUri()

        evilWallet.resolvePresentationRequestRaw(presentationUrl).also {
            it.expectError()
        }

        val resolvedPresentationOfferString =
            walletA.resolvePresentationRequest(presentationUrl)

        val presentationDefinition =
            Url(resolvedPresentationOfferString).parameters.getOrFail("presentation_definition")
        evilWallet.matchCredentialsForPresentationDefinitionRaw(presentationDefinition).also {
            it.expectError()
        }
    }
}