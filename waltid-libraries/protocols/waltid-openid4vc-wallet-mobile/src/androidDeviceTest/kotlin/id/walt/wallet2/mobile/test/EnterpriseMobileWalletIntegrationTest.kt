package id.walt.wallet2.mobile.test

import id.walt.wallet2.mobile.identity.SigningIdentityOperationResult
import id.walt.wallet2.mobile.identity.SigningIdentity
import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import id.walt.wallet2.mobile.ScaAuthorizationTestActivity
import id.walt.wallet2.handlers.WalletIssuanceErrorCode
import org.junit.Assume.assumeTrue
import androidx.test.platform.app.InstrumentationRegistry
import id.walt.mobile.test.backend.EnterpriseMobileAttestationConfig
import id.walt.mobile.test.backend.EnterpriseMobileFixtureClient
import id.walt.mobile.test.backend.EnterpriseMobilePlatform
import id.walt.mobile.test.backend.EnterpriseMobileScenario
import id.walt.wallet2.handlers.WalletIssuanceOutcome
import id.walt.wallet2.mobile.MobileWallet
import id.walt.wallet2.mobile.MobileWalletConfig
import id.walt.wallet2.mobile.MobileWalletFactory
import id.walt.wallet2.mobile.MobileWalletCredentialOffer
import id.walt.wallet2.mobile.MobileWalletCredentialSelection
import id.walt.wallet2.mobile.MobileWalletIssuanceRequest
import id.walt.wallet2.mobile.MobileWalletPresentationCredentialSelection
import id.walt.wallet2.mobile.MobileWalletPresentationPreviewResult
import id.walt.wallet2.mobile.MobileWalletPresentationResult
import id.walt.wallet2.mobile.WalletAttestationConfig
import id.walt.crypto2.keys.KeyUseAuthorizationPolicy
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.util.UUID
import kotlin.test.assertIs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@EnterpriseMobileTest
class EnterpriseMobileWalletIntegrationTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val fixtureBaseUrl: String?
        get() = InstrumentationRegistry.getArguments().getString("enterprise_fixture_base_url")

    @Test
    fun receiveEnterpriseMdlFromEnterpriseIssuer2() = runBlocking {
        receiveCredentialFromEnterpriseIssuer2("enterprise-mdl")
    }

    @Test
    fun receiveEnterpriseMdlWithClientAttestationFromEnterpriseIssuer2() = runBlocking {
        receiveCredentialFromEnterpriseIssuer2("enterprise-mdl-client-attestation")
    }

    @Test
    fun receiveAndPresentEnterpriseMdlIssuer2Verifier2Flow() = runBlocking {
        receiveAndPresentEnterpriseCredential("enterprise-mdl")
    }

    @Test
    fun receiveAndPresentEnterpriseMdlWithClientAttestationIssuer2Verifier2Flow() = runBlocking {
        receiveAndPresentEnterpriseCredential("enterprise-mdl-client-attestation")
    }

    @Test
    fun enterpriseCredentialPersistsAcrossWalletRecreation() = runBlocking {
        val fixture = requireFixture()
        val scenario = enterpriseScenario(fixture, "enterprise-mdl")
        val walletId = "android-enterprise-persist-${scenario.id}-${UUID.randomUUID()}"
        val offer = fixture.createOffer(scenario, EnterpriseMobilePlatform.ANDROID)

        val wallet1 = createWallet(walletId, offer.attestation)
        val bootstrapResult = wallet1.signingIdentity.initialize().activeIdentity()
        wallet1.receiveCredential(offer.offerUrl, offer.txCode)

        val wallet2 = createWallet(walletId, offer.attestation)
        val credentials = wallet2.credentials()
        assertTrue(credentials.isNotEmpty(), "Enterprise credential should persist across wallet recreation")

        val session = fixture.createVerifierSession(scenario, EnterpriseMobilePlatform.ANDROID)
        val presentResult = wallet2.present(session.authorizationRequestUri, did = bootstrapResult.did)
        assertIs<MobileWalletPresentationResult.Transmitted.Succeeded>(
            presentResult,
            "Should present persisted Enterprise credential for ${scenario.displayName}: credentials=$credentials, result=$presentResult",
        )
        fixture.waitForVerifierSuccess(session.sessionId)
    }

    @Test
    fun batchHolderBindingsPresentEachCopyAfterWalletRecreation() = runBlocking {
        val fixture = requireFixture()
        val scenario = enterpriseScenario(fixture, "enterprise-mdl")
        val offer = fixture.createOffer(scenario, EnterpriseMobilePlatform.ANDROID)
        val walletId = "android-enterprise-batch-${UUID.randomUUID()}"
        val wallet = createWallet(walletId, null)
        val identity = wallet.signingIdentity.initialize().activeIdentity()
        val session = wallet.startIssuance(MobileWalletIssuanceRequest(MobileWalletCredentialOffer.Uri(offer.offerUrl)))
        val holders = wallet.createIssuanceHolderKeys(count = 2)
        assertEquals(2, holders.map { it.keyId }.toSet().size)
        assertTrue(holders.none { it.keyId == identity.keyId })
        val outcome = assertIs<WalletIssuanceOutcome.Stored>(wallet.continuePreAuthorizedIssuance(
            sessionId = session.id,
            credentials = listOf(MobileWalletCredentialSelection(session.offer.credentials.single().configurationId, holders)),
        ))
        assertEquals(2, outcome.credentialIds.toSet().size)

        assertCopiesPresentAfterRecreation(walletId, outcome.credentialIds, fixture, scenario)
    }

    private suspend fun assertCopiesPresentAfterRecreation(
        walletId: String,
        credentialIds: List<String>,
        fixture: EnterpriseMobileFixtureClient,
        scenario: EnterpriseMobileScenario,
    ) {
        val reopened = createWallet(walletId, null)
        reopened.signingIdentity.initialize().activeIdentity()
        assertEquals(credentialIds.toSet(), reopened.credentials().map { it.id }.toSet())
        for (credentialId in credentialIds) {
            val verification = fixture.createVerifierSession(scenario, EnterpriseMobilePlatform.ANDROID)
            val preview = assertIs<MobileWalletPresentationPreviewResult.Ready>(
                reopened.previewPresentation(verification.authorizationRequestUri),
            ).preview
            val option = preview.credentialOptions.first { it.credentialId == credentialId }
            // No key override: each copy must use its own persisted holder binding.
            assertIs<MobileWalletPresentationResult.Transmitted.Succeeded>(reopened.submitPresentation(
                previewHandle = preview.previewHandle,
                selectedCredentialOptions = listOf(MobileWalletPresentationCredentialSelection(option.queryId, option.credentialId)),
            ))
            fixture.waitForVerifierSuccess(verification.sessionId)
        }
    }

    /** Opens the installed browser; the fixture simulates only external identity-provider login. */
    @Test
    fun browserAuthorizationRetainsBatchSelectionAcrossWalletRecreation() = runBlocking {
        assumeTrue("Requires a browser and permission to open the callback app",
            InstrumentationRegistry.getArguments().getString("wallet.batch.browser") == "true")
        val fixture = requireFixture()
        val scenario = enterpriseScenario(fixture, "enterprise-mdl-authorized")
        val offer = fixture.createOffer(scenario, EnterpriseMobilePlatform.ANDROID)
        val walletId = "android-enterprise-batch-browser-${UUID.randomUUID()}"
        val wallet = createWallet(walletId, null)
        wallet.signingIdentity.initialize().activeIdentity()
        val session = wallet.startIssuance(MobileWalletIssuanceRequest(
            MobileWalletCredentialOffer.Uri(offer.offerUrl), clientId = "wallet-client", redirectUri = "walbatchtest://callback",
        ))
        val holders = wallet.createIssuanceHolderKeys(2)
        val authorization = wallet.beginAuthorizationIssuance(session.id,
            listOf(MobileWalletCredentialSelection(session.offer.credentials.single().configurationId, holders)))
        // Recreate the SDK before the browser callback; accepted choices must come from persisted state.
        val reopened = createWallet(walletId, null)
        reopened.signingIdentity.initialize().activeIdentity()
        BatchAuthorizationTestActivity.callback = CompletableDeferred()
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(authorization.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val callback = withTimeout(120_000) { BatchAuthorizationTestActivity.callback.await() }
        val stored = assertIs<WalletIssuanceOutcome.Stored>(
            reopened.continueAuthorizationIssuance(session.id, callback))
        assertEquals(2, stored.credentialIds.toSet().size)
        assertCopiesPresentAfterRecreation(walletId, stored.credentialIds, fixture, scenario)
    }

    /** Operator-gated physical-device check; cancel the second holder's signing prompt. */
    @Test
    fun batchSigningCancellationStoresNoCredential() = runBlocking {
        assumeTrue("Requires an operator cancelling the signing prompt",
            InstrumentationRegistry.getArguments().getString("wallet.batch.cancel") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(
            Intent(context, ScaAuthorizationTestActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        ) as ScaAuthorizationTestActivity
        instrumentation.runOnMainSync {
            activity.setContentView(android.widget.TextView(activity).apply {
                text = "Batch issuance check: cancel the system signing prompt."
            })
        }
        try {
            val fixture = requireFixture()
            val scenario = enterpriseScenario(fixture, "enterprise-mdl")
            val offer = fixture.createOffer(scenario, EnterpriseMobilePlatform.ANDROID)
            val walletId = "android-enterprise-batch-cancel-${UUID.randomUUID()}"
            val wallet = MobileWalletFactory(activity, interactionContextProvider = { activity }).create(
                MobileWalletConfig(walletId = walletId, defaultKeyUseAuthorizationPolicy = KeyUseAuthorizationPolicy.None),
            )
            wallet.signingIdentity.initialize().activeIdentity()
            val session = wallet.startIssuance(MobileWalletIssuanceRequest(MobileWalletCredentialOffer.Uri(offer.offerUrl)))
            val first = wallet.createIssuanceHolderKeys(1, keyUseAuthorizationPolicy = KeyUseAuthorizationPolicy.None)
            val second = wallet.createIssuanceHolderKeys(1, keyUseAuthorizationPolicy = KeyUseAuthorizationPolicy.BiometricCurrentSet)
            val failed = assertIs<WalletIssuanceOutcome.Failed>(wallet.continuePreAuthorizedIssuance(
                sessionId = session.id,
                credentials = listOf(MobileWalletCredentialSelection(session.offer.credentials.single().configurationId, first + second)),
            ))
            assertEquals(WalletIssuanceErrorCode.CRYPTO, failed.error.code)
            assertTrue(failed.storedCredentialIds.isEmpty())
            assertTrue(failed.deferredCredentials.isEmpty())
            assertTrue(createWallet(walletId, null).credentials().isEmpty())
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    private suspend fun receiveCredentialFromEnterpriseIssuer2(scenarioId: String) {
        val fixture = requireFixture()
        val scenario = enterpriseScenario(fixture, scenarioId)
        val offer = fixture.createOffer(scenario, EnterpriseMobilePlatform.ANDROID)
        val wallet = createWallet(
            walletId = "android-enterprise-receive-${scenario.id}-${UUID.randomUUID()}",
            attestation = offer.attestation,
        )
        wallet.signingIdentity.initialize().activeIdentity()

        val credentialIds = wallet.receiveCredential(offer.offerUrl, offer.txCode)

        assertTrue(
            credentialIds.isNotEmpty(),
            "Should receive ${scenario.displayName} from Enterprise issuer2",
        )
    }

    private suspend fun receiveAndPresentEnterpriseCredential(scenarioId: String) {
        val fixture = requireFixture()
        val scenario = enterpriseScenario(fixture, scenarioId)
        assertTrue(scenario.supportsPresentation, "${scenario.displayName} should support presentation")

        val offer = fixture.createOffer(scenario, EnterpriseMobilePlatform.ANDROID)
        val wallet = createWallet(
            walletId = "android-enterprise-present-${scenario.id}-${UUID.randomUUID()}",
            attestation = offer.attestation,
        )
        val bootstrapResult = wallet.signingIdentity.initialize().activeIdentity()

        val credentialIds = wallet.receiveCredential(offer.offerUrl, offer.txCode)
        assertTrue(credentialIds.isNotEmpty(), "Should receive ${scenario.displayName}")

        val credentials = wallet.credentials()
        assertTrue(credentials.isNotEmpty(), "Should have stored ${scenario.displayName} credentials")

        val session = fixture.createVerifierSession(scenario, EnterpriseMobilePlatform.ANDROID)
        val presentResult = wallet.present(session.authorizationRequestUri, did = bootstrapResult.did)
        assertIs<MobileWalletPresentationResult.Transmitted.Succeeded>(
            presentResult,
            "Enterprise verifier2 presentation should succeed for ${scenario.displayName}: credentials=$credentials, result=$presentResult",
        )

        fixture.waitForVerifierSuccess(session.sessionId)
    }

    private fun requireFixture(): EnterpriseMobileFixtureClient {
        val baseUrl = fixtureBaseUrl
        require(!baseUrl.isNullOrBlank()) {
            "Set enterprise_fixture_base_url to run Enterprise mobile integration tests"
        }
        return EnterpriseMobileFixtureClient(baseUrl)
    }

    private suspend fun enterpriseScenario(
        fixture: EnterpriseMobileFixtureClient,
        scenarioId: String,
    ): EnterpriseMobileScenario =
        fixture.scenarios().first { it.id == scenarioId }

    private suspend fun createWallet(
        walletId: String,
        attestation: EnterpriseMobileAttestationConfig?,
    ) = MobileWalletFactory(context).create(
        MobileWalletConfig(
            walletId = walletId,
            attestationConfig = attestation?.toWalletAttestationConfig(),
            onEvent = { event -> println("WALLET EVENT: $event") },
            defaultKeyUseAuthorizationPolicy = KeyUseAuthorizationPolicy.None,
        )
    )

    private fun EnterpriseMobileAttestationConfig.toWalletAttestationConfig() =
        WalletAttestationConfig(
            baseUrl = baseUrl,
            attesterPath = attesterPath,
            bearerToken = bearerToken,
            hostHeader = hostHeader,
        )

    private suspend fun MobileWallet.receiveCredential(
        offerUrl: String,
        transactionCode: String?,
    ): List<String> =
        when (
            val outcome = continuePreAuthorizedIssuance(
                sessionId = startIssuance(
                    MobileWalletIssuanceRequest(offer = MobileWalletCredentialOffer.Uri(offerUrl))
                ).id,
                transactionCode = transactionCode,
            )
        ) {
            is WalletIssuanceOutcome.Stored -> outcome.credentialIds
            is WalletIssuanceOutcome.Deferred -> error("Expected stored credentials, got deferred outcome: $outcome")
            is WalletIssuanceOutcome.Cancelled -> error("Expected stored credentials, got cancelled outcome")
            is WalletIssuanceOutcome.Failed -> error("Expected stored credentials, got failed outcome: ${outcome.error.message}")
        }
}

private fun SigningIdentityOperationResult.activeIdentity(): SigningIdentity =
    kotlin.test.assertIs<SigningIdentityOperationResult.Active>(this).identity
