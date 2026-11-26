package id.walt.wallet2.mobile.test

import id.walt.wallet2.mobile.issuance.MobileWalletIssuanceSession
import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import id.walt.certificate.x509.X509CertificateUtil
import id.walt.certificate.x509.truststore.InMemoryTrustStore
import id.walt.credentials.status.CredentialStatusCoverage
import id.walt.credentials.status.CredentialStatusFreshness
import id.walt.credentials.status.CredentialStatusIssue
import id.walt.credentials.status.CredentialStatusKind
import id.walt.credentials.status.StatusMeaning
import id.walt.credentials.status.StatusProtocol
import id.walt.crypto2.keys.KeyUseAuthorizationPolicy
import id.walt.dcql.models.CredentialFormat
import id.walt.dcql.models.CredentialQuery
import id.walt.dcql.models.DcqlQuery
import id.walt.dcql.models.meta.NoMeta
import id.walt.mobile.test.backend.DemoTestBackend
import id.walt.mobile.test.backend.EudiTestBackend
import id.walt.mobile.test.backend.PublicDemoSignedRequestContractUnavailable
import id.walt.openid4vp.clientidprefix.ClientIdTrustConfiguration
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.verifier.openid.models.authorization.ClientMetadata
import id.walt.verifier.openid.models.openid.OpenID4VPResponseMode
import id.walt.wallet2.handlers.WalletIssuanceMetadataProvenance
import id.walt.wallet2.handlers.WalletIssuanceOutcome
import id.walt.wallet2.mobile.MobileWallet
import id.walt.wallet2.mobile.MobileWalletConfig
import id.walt.wallet2.mobile.MobileWalletFactory
import id.walt.wallet2.mobile.credentials.MobileWalletCredential
import id.walt.wallet2.mobile.identity.MobileWalletKeyAuthorization
import id.walt.wallet2.mobile.identity.SigningIdentity
import id.walt.wallet2.mobile.identity.SigningIdentityOperationResult
import id.walt.wallet2.mobile.issuance.MobileWalletCredentialOffer
import id.walt.wallet2.mobile.issuance.MobileWalletIssuanceConfig
import id.walt.wallet2.mobile.issuance.MobileWalletIssuanceRequest
import id.walt.wallet2.mobile.presentation.MobileWalletClientIdScheme
import id.walt.wallet2.mobile.presentation.MobileWalletPresentationConfig
import id.walt.wallet2.mobile.presentation.MobileWalletPresentationCredentialSelection
import id.walt.wallet2.mobile.presentation.MobileWalletPresentationDisclosureSelection
import id.walt.wallet2.mobile.presentation.MobileWalletPresentationErrorCode
import id.walt.wallet2.mobile.presentation.MobileWalletPresentationPreview
import id.walt.wallet2.mobile.presentation.MobileWalletPresentationPreviewResult
import id.walt.wallet2.mobile.presentation.MobileWalletPresentationResult
import id.walt.wallet2.mobile.presentation.MobileWalletPresentationSelection
import id.walt.wallet2.mobile.presentation.MobileWalletRequestAuthentication
import id.walt.wallet2.mobile.presentation.MobileWalletResponseEncryption
import id.walt.wallet2.mobile.presentation.MobileWalletTransactionDataProfile
import id.walt.wallet2.status.CredentialStatusRefreshMode
import id.walt.wallet2.status.CredentialStatusRefreshOutcome
import id.walt.walletdemo.attestation.DemoKeyAttestationProviders
import id.waltid.openid4vci.wallet.metadata.MetadataSignerTrustType
import java.util.Base64
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Test

/**
 * Android integration tests for the mobile wallet library.
 *
 * Exercises the full mobile stack: AndroidKeyStore crypto + SQLDelight persistence
 * + OID4VCI/VP protocol against public walt.id demo and EUDI test backends.
 *
 * Uses runBlocking (not runTest) because real network I/O requires real time
 * dispatchers — runTest's virtual time expires HTTP timeouts immediately.
 *
 * These are integration tests (not E2E UI tests) - they test the library directly
 * without UI automation. Run on every PR for fast feedback.
 */
class MobileWalletIntegrationTest {

    companion object {
        private const val PAYMENT_AUTHORIZATION_TYPE = "org.waltid.transaction-data.payment-authorization"
        private const val EUDI_PID_SD_JWT_CREDENTIAL_ID = "eu.europa.ec.eudi.pid_vc_sd_jwt"
        private const val EUDI_EHIC_SD_JWT_CREDENTIAL_ID = "eu.europa.ec.eudi.ehic_sd_jwt_vc"

        private val DEMO_TRANSACTION_DATA_PROFILES = demoTransactionDataProfiles(
            paymentAuthorizationFields = listOf("merchant_name", "amount", "currency"),
        )

        @OptIn(ExperimentalSerializationApi::class)
        private val DEMO_VERIFIER_TRUST = ClientIdTrustConfiguration(
            preRegisteredClients = mapOf(
                DemoTestBackend.PUBLIC_DEMO_VERIFIER_CLIENT_ID to ClientMetadata(
                    jwks = ClientMetadata.Jwks(listOf(DemoTestBackend.publicDemoVerifierRequestObjectSigningJwk)),
                ),
            ),
        )

        private fun demoTransactionDataProfiles(
            paymentAuthorizationFields: Iterable<String>,
        ) = listOf(
            MobileWalletTransactionDataProfile(
                type = PAYMENT_AUTHORIZATION_TYPE,
                displayName = "Payment Authorization",
                fields = paymentAuthorizationFields.toList(),
            ),
            MobileWalletTransactionDataProfile(
                type = "org.waltid.transaction-data.account-access",
                displayName = "Account Access",
                fields = listOf("account_identifier", "access_scope"),
            ),
        )
    }

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun bootstrapCreatesKeyAndDid() = runBlocking {
        val client = MobileWalletFactory(context).create(
            MobileWalletConfig(
                keyAuthorization = MobileWalletKeyAuthorization(policy = KeyUseAuthorizationPolicy.None),
            )
        )
        val result = client.signingIdentity.initialize().activeIdentity()
        assertNotNull(result.keyId, "bootstrap should create a key")
        assertNotNull(result.did, "bootstrap should create a DID")
        assertTrue(result.did.startsWith("did:"), "DID should start with 'did:'")
    }

    @Test
    fun receiveEudiPidSdJwtFromEudi() = runBlocking {
        val client = createEudiWallet(
            walletConfig("eudi-pid-status")
        )
        try {
            client.signingIdentity.initialize().activeIdentity()

            val offer = EudiTestBackend.generateOffer(EUDI_PID_SD_JWT_CREDENTIAL_ID)
            val session = client.startIssuance(
                MobileWalletIssuanceRequest(
                    offer = MobileWalletCredentialOffer.Uri(offer.offerUrl),
                )
            )
            assertTrue(session.offer.issuer.identifier.isNotBlank(), "Resolved issuer metadata should include its identifier")
            assertTrue(session.offer.credentials.isNotEmpty(), "Offer should resolve credential metadata")
            assertTrue(session.offer.credentials.all { it.configurationId.isNotBlank() && it.format.isNotBlank() })
            assertNotNull(session.offer.transactionCode, "EUDI offer should require a transaction code")
            val credentialIds = (session as MobileWalletIssuanceSession.PreAuthorized).accept(offer.txCode, clientId = "eudiw-abca").storedCredentialIds()
            assertTrue(credentialIds.isNotEmpty(), "Should receive at least one credential")
            client.checkEudiPidStatus(credentialIds)
        } finally {
            client.close()
        }
    }

    @Test
    fun receiveAndPresentUsingSignedMetadataAgainstDemoIssuer2AndVerifier2() = runBlocking {
        val scenario = demoPresentationScenario("eudi-pid-mdoc")
        val client = MobileWalletFactory(context).create(
            walletConfig("signed-${scenario.id}", verifierTrust = DEMO_VERIFIER_TRUST).copy(
                issuance = MobileWalletIssuanceConfig(metadataTrustResolver = DemoTestBackend.publicDemoIssuerMetadataTrustResolver),
            ),
        )
        val bootstrap = client.signingIdentity.initialize().activeIdentity()
        val offer = DemoTestBackend.createOffer(scenario)

        val issuanceSession = client.startIssuance(
            MobileWalletIssuanceRequest(
                offer = MobileWalletCredentialOffer.Uri(offer.offerUrl),
            ),
        )
        val issuerProvenance = assertIs<WalletIssuanceMetadataProvenance.Signed>(
            issuanceSession.offer.issuer.metadataProvenance,
        )
        assertTrue(issuerProvenance.compactJwt.isNotBlank())
        assertEquals("ES256", issuerProvenance.algorithm)
        assertEquals(MetadataSignerTrustType.TRUSTED_ISSUER, issuerProvenance.trustType)
        assertNotNull(issuerProvenance.keyId)

        val credentialIds = (issuanceSession as MobileWalletIssuanceSession.PreAuthorized).accept(offer.txCode, clientId = "eudiw-abca").storedCredentialIds()
        assertTrue(credentialIds.isNotEmpty(), "Should receive a credential from reviewed signed metadata")

        val signedSession = try {
            DemoTestBackend.createVerifierSession(scenario, signedRequest = true)
        } catch (e: PublicDemoSignedRequestContractUnavailable) {
            println("Skipping signed public-demo presentation: ${e.message}")
            return@runBlocking
        }
        val preview = client.previewPresentation(signedSession.authorizationRequestUri).requireReadyPreview()
        val verifierAuthentication = assertIs<MobileWalletRequestAuthentication.Authenticated>(
            preview.request.requestAuthentication,
        )
        assertTrue(verifierAuthentication.compactRequestObject.isNotBlank())
        assertEquals("ES256", verifierAuthentication.algorithm)
        assertNotNull(verifierAuthentication.keyId)
        assertEquals(MobileWalletClientIdScheme.PRE_REGISTERED, verifierAuthentication.clientIdScheme)

        val result = client.submitPresentation(client.reviewPresentation(
            previewHandle = preview.previewHandle,
            selection = MobileWalletPresentationSelection(credentials = preview.credentialOptions.map { it.selection }),
            did = bootstrap.did,
        ))
        assertIs<MobileWalletPresentationResult.Transmitted.Succeeded>(result)
        DemoTestBackend.waitForVerifierSuccess(signedSession.sessionId)
    }

    @Test
    fun receiveAndPresentEudiEhicSdJwtAgainstEudi() = runBlocking {
        receiveAndPresentEudiCredential(EUDI_EHIC_SD_JWT_CREDENTIAL_ID)
    }

    @Test
    fun previewAndSubmitEudiEhicSdJwtAgainstEudi() = runBlocking {
        previewAndSubmitEudiCredential(EUDI_EHIC_SD_JWT_CREDENTIAL_ID)
    }

    @Test
    fun receiveAndPresentEudiPidSdJwtAgainstEudi() = runBlocking {
        receiveAndPresentEudiCredential(EUDI_PID_SD_JWT_CREDENTIAL_ID)
    }

    @Test
    fun previewAndSubmitEudiPidSdJwtAgainstEudi() = runBlocking {
        previewAndSubmitEudiCredential(EUDI_PID_SD_JWT_CREDENTIAL_ID)
    }

    @Test
    fun publicDemoPidSdJwtSupportsDirectAndStepwisePresentation() = runBlocking {
        exerciseDemoPresentations("eudi-pid-sdjwt", DemoPresentation.OpenWallet, DemoPresentation.ReopenedWallet)
    }

    @Test
    fun publicDemoPidMdocPersistsAndSupportsDirectAndStepwisePresentation() = runBlocking {
        assertEquals("eudi-pid-mdoc", DemoTestBackend.persistenceScenario.id)
        // Reopen before any presentation can initialize state needed by the persistence path.
        exerciseDemoPresentations(
            DemoTestBackend.persistenceScenario.id,
            DemoPresentation.OpenWallet, DemoPresentation.ReopenedWallet,
        )
    }

    @Test
    fun previewAndSubmitTransactionDataAgainstDemoIssuer2AndVerifier2() = runBlocking {
        val scenario = DemoTestBackend.transactionDataPresentationScenario
        val paymentAuthorizationFields = DemoTestBackend.transactionDataProfileFields(PAYMENT_AUTHORIZATION_TYPE)
        val client = MobileWalletFactory(context).create(
            walletConfig(
                prefix = "transaction-data-${scenario.id}",
                transactionDataProfiles = demoTransactionDataProfiles(paymentAuthorizationFields),
            ),
        )
        val bootstrapResult = client.signingIdentity.initialize().activeIdentity()

        val offer = DemoTestBackend.createOffer(scenario)
        val credentialIds = client.receiveCredential(offer.offerUrl, offer.txCode)
        assertTrue(
            credentialIds.isNotEmpty(),
            "Should receive ${scenario.displayName} from public demo issuer2",
        )

        val session = DemoTestBackend.createTransactionDataVerifierSession(scenario)
        val preview = client.previewPresentation(session.authorizationRequestUri).requireReadyPreview()
        val transactionData = preview.request.transactionData.singleOrNull()
        assertNotNull(transactionData, "Preview should expose payment transaction data: preview=$preview")
        assertEquals("Payment Authorization", transactionData.displayName)
        assertEquals(listOf("pid"), transactionData.credentialQueryIds)
        assertTrue(
            transactionData.detailsJson.contains("\"amount\":\"42.00\"") &&
                transactionData.detailsJson.contains("\"currency\":\"EUR\"") &&
                transactionData.detailsJson.contains("\"merchant_name\":\"ACME Corp\""),
            "Preview should expose readable payment details: ${transactionData.detailsJson}",
        )
        val result = client.submitPresentation(client.reviewPresentation(
            previewHandle = preview.previewHandle,
            selection = MobileWalletPresentationSelection(
                credentials = preview.credentialOptions.map { option -> option.selection },
            ),
            did = bootstrapResult.did,
        ))
        assertIs<MobileWalletPresentationResult.Transmitted.Succeeded>(
            result,
            "public demo verifier2 transaction-data presentation should succeed: preview=$preview, result=$result",
        )

        DemoTestBackend.waitForVerifierSuccess(session.sessionId)
    }

    @Test
    fun rejectPresentationAgainstDemoVerifier2() = runBlocking {
        val scenario = demoPresentationScenario("eudi-pid-sdjwt")
        val client = MobileWalletFactory(context).create(walletConfig("reject-${scenario.id}"))
        client.signingIdentity.initialize().activeIdentity()

        val session = DemoTestBackend.createResponseBoundVerifierSession(scenario)
        val previewHandle = when (val preview = client.previewPresentation(session.authorizationRequestUri)) {
            is MobileWalletPresentationPreviewResult.Ready -> preview.preview.previewHandle
            is MobileWalletPresentationPreviewResult.Invalid -> preview.previewHandle
        }
        val result = client.rejectPresentation(previewHandle)

        assertIs<MobileWalletPresentationResult.Transmitted.Succeeded>(
            result,
            "Wallet should deliver access_denied to public demo verifier2: $result",
        )
        val info = DemoTestBackend.waitForVerifierFailure(
            sessionId = session.sessionId,
            expectedError = "access_denied",
        )
        assertEquals("wallet_error_response", info["failure"]?.jsonObject?.get("type")?.jsonPrimitive?.contentOrNull)
    }

    @Test
    fun invalidTransactionDataCanBeReviewedAndReportedWithoutBackendSupport() = runBlocking {
        val client = MobileWalletFactory(context).create(walletConfig("invalid-transaction-data"))
        client.signingIdentity.initialize().activeIdentity()
        val requestUrl = invalidTransactionDataRequestUrl()

        val preview = assertIs<MobileWalletPresentationPreviewResult.Invalid>(
            client.previewPresentation(requestUrl),
        )

        assertEquals(MobileWalletPresentationErrorCode.invalidTransactionData, preview.errorCode)
        assertEquals("redirect_uri:https://verifier.example/callback", preview.request.clientId)

        val result = assertIs<MobileWalletPresentationResult.Prepared.OpenUrl>(
            client.rejectPresentation(preview.previewHandle),
        )
        assertEquals(
            "https://verifier.example/callback#error=invalid_transaction_data&state=state-123",
            result.url,
        )
    }

    @Test
    fun previewAndSubmitOptionalBirthDateClaimSetAgainstDemoIssuer2AndVerifier2() = runBlocking {
        val scenario = DemoTestBackend.optionalBirthDatePresentationScenario
        val client = MobileWalletFactory(context).create(walletConfig("optional-birth-date-${scenario.id}"))
        val bootstrapResult = client.signingIdentity.initialize().activeIdentity()

        val offer = DemoTestBackend.createOffer(scenario)
        val credentialIds = client.receiveCredential(offer.offerUrl, offer.txCode)
        assertTrue(
            credentialIds.isNotEmpty(),
            "Should receive ${scenario.displayName} from public demo issuer2",
        )

        val defaultOffSession = DemoTestBackend.createVerifierSession(scenario)
        val defaultOffPreview = client.previewPresentation(defaultOffSession.authorizationRequestUri).requireReadyPreview()
        val defaultOffOption = defaultOffPreview.singleReceivedCredentialOption(credentialIds)
        val defaultOffBirthDate = defaultOffOption.disclosures.singleOrNull { it.name == "birth_date" }
        assertNotNull(
            defaultOffBirthDate,
            "Preview should expose optional birth_date from alternative DCQL claim_set: preview=$defaultOffPreview",
        )
        assertTrue(defaultOffBirthDate.selectivelyDisclosable, "birth_date should be selectively disclosable")
        assertTrue(defaultOffBirthDate.selectable, "birth_date should be selectable")
        assertEquals(false, defaultOffBirthDate.required, "birth_date should not be required by the minimal claim_set")
        assertTrue(
            defaultOffOption.disclosures.filter { it.name == "given_name" || it.name == "family_name" }
                .all { it.required && !it.selectable },
            "Minimal claim-set disclosures should remain required and non-selectable: ${defaultOffOption.disclosures}",
        )

        val defaultOffResult = client.submitPresentation(client.reviewPresentation(
            previewHandle = defaultOffPreview.previewHandle,
            selection = MobileWalletPresentationSelection(
                credentials = listOf(defaultOffOption.selection),
                disclosures = emptyList(),
            ),
            did = bootstrapResult.did,
        ))
        assertIs<MobileWalletPresentationResult.Transmitted.Succeeded>(
            defaultOffResult,
            "Optional-off presentation should succeed against public demo verifier2: preview=$defaultOffPreview, result=$defaultOffResult",
        )
        DemoTestBackend.waitForVerifierSuccess(defaultOffSession.sessionId)
        val defaultOffInfo = DemoTestBackend.verifierSessionInfo(defaultOffSession.sessionId)
        assertEquals(
            false,
            defaultOffInfo.verifiedPresentationData().containsKey("birth_date"),
            "Default optional-off submission must not disclose birth_date",
        )
        assertEquals(
            false,
            defaultOffInfo.presentedCredentialData().any { it.containsKey("birth_date") },
            "Default optional-off verifier credential data must not include birth_date",
        )
        assertEquals(
            false,
            defaultOffInfo.presentedSdJwtDisclosureClaimNames().contains("birth_date"),
            "Default optional-off VP token must not include a birth_date disclosure",
        )

        val selectedSession = DemoTestBackend.createVerifierSession(scenario)
        val selectedPreview = client.previewPresentation(selectedSession.authorizationRequestUri).requireReadyPreview()
        val selectedOption = selectedPreview.singleReceivedCredentialOption(credentialIds)
        val selectedBirthDate = selectedOption.disclosures.singleOrNull { it.name == "birth_date" }
        assertNotNull(
            selectedBirthDate,
            "Preview should expose optional birth_date before selected submission: preview=$selectedPreview",
        )

        val selectedResult = client.submitPresentation(client.reviewPresentation(
            previewHandle = selectedPreview.previewHandle,
            selection = MobileWalletPresentationSelection(
                credentials = listOf(selectedOption.selection),
                disclosures = listOf(
                MobileWalletPresentationDisclosureSelection(
                    queryId = selectedOption.queryId,
                    credentialId = selectedOption.credentialId,
                    path = selectedBirthDate.path,
                )
            ),
            ),
            did = bootstrapResult.did,
        ))
        assertIs<MobileWalletPresentationResult.Transmitted.Succeeded>(
            selectedResult,
            "Optional-selected presentation should succeed against public demo verifier2: preview=$selectedPreview, result=$selectedResult",
        )
        DemoTestBackend.waitForVerifierSuccess(selectedSession.sessionId)
        val selectedInfo = DemoTestBackend.verifierSessionInfo(selectedSession.sessionId)
        assertTrue(
            selectedInfo.presentedSdJwtDisclosureClaimNames().contains("birth_date"),
            "Selected optional submission must include birth_date in the VP token disclosures: info=$selectedInfo",
        )
        assertTrue(
            selectedInfo.presentedCredentialData().any { it["birth_date"]?.jsonPrimitive?.contentOrNull == "1971-09-01" },
            "Selected optional submission must disclose birth_date in verifier credential data: info=$selectedInfo",
        )
    }

    @Test
    fun receiveAndPresentIsoMdlAgainstDemoIssuer2AndVerifier2() = runBlocking {
        exerciseDemoPresentations("iso-mdl", DemoPresentation.OpenWallet)
    }

    @Test
    fun eudiPidSdJwtPersistsAcrossWalletRecreation() = runBlocking {
        val walletConfig = walletConfig("eudi-pid-sd-jwt-persistence")

        val client1 = createEudiWallet(walletConfig)
        val (ids, checkedById) = try {
            client1.signingIdentity.initialize().activeIdentity()
            val offer = EudiTestBackend.generateOffer(EUDI_PID_SD_JWT_CREDENTIAL_ID)
            val ids = client1.receiveCredential(offer.offerUrl, offer.txCode)
            client1.checkEudiPidStatus(ids)
            ids to client1.credentialStatusSnapshot().credentials.associate {
                it.reference.credentialId to it.presentation
            }
        } finally {
            client1.close()
        }
        val client2 = createEudiWallet(walletConfig)
        try {
            val credentials = client2.credentials()
            assertEquals(ids.toSet(), credentials.map { it.id }.toSet(), "The issued credentials must survive recreation")
            val restored = client2.credentialStatusSnapshot()
            assertEquals(ids.toSet(), restored.credentials.map { it.reference.credentialId }.toSet())
            restored.credentials.forEach { record ->
                val checked = checkedById.getValue(record.reference.credentialId)
                val freshUntil = assertNotNull(checked.references.single().freshUntil)
                assertEquals(checked.lastCheckedAt, record.presentation.lastCheckedAt)
                assertEquals(freshUntil, record.presentation.references.single().freshUntil)
                val expected = if (restored.evaluatedAt < freshUntil) {
                    CredentialStatusFreshness.FRESH
                } else {
                    CredentialStatusFreshness.STALE
                }
                assertEquals(expected, record.presentation.freshness, "Restored evidence must honor its original freshness deadline")
            }
            client2.checkEudiPidStatus(ids)
            client2.credentialStatusSnapshot().credentials.forEach {
                val previousCheck = assertNotNull(checkedById.getValue(it.reference.credentialId).lastCheckedAt)
                assertTrue(assertNotNull(it.presentation.lastCheckedAt) > previousCheck)
            }
        } finally {
            client2.close()
        }
    }

    private suspend fun MobileWallet.checkEudiPidStatus(ids: List<String>) {
        val results = refreshCredentialStatuses(ids, CredentialStatusRefreshMode.FORCE)
        assertEquals(ids.toSet(), results.map { it.reference.credentialId }.toSet())
        results.forEach { assertEquals(CredentialStatusRefreshOutcome.COMPLETED, it.outcome) }
        val records = credentialStatusSnapshot().credentials.filter { it.reference.credentialId in ids }
        assertEquals(ids.size, records.size)
        records.forEach { record ->
            val status = record.presentation
            assertEquals(CredentialStatusKind.CHECKED, status.kind, "EUDI PID must have authenticated status, not merely a completed attempt")
            assertEquals(CredentialStatusFreshness.FRESH, status.freshness)
            assertEquals(CredentialStatusCoverage.COMPLETE, status.coverage)
            assertEquals(CredentialStatusIssue.NONE, status.issue)
            assertNull(status.failure)
            assertNotNull(status.lastCheckedAt)
            assertEquals(StatusProtocol.TOKEN_STATUS_LIST, status.references.single().protocol)
            assertEquals(StatusMeaning.CLEAR, status.references.single().result)
        }
    }

    private fun walletConfig(
        prefix: String,
        transactionDataProfiles: List<MobileWalletTransactionDataProfile> = DEMO_TRANSACTION_DATA_PROFILES,
        verifierTrust: ClientIdTrustConfiguration = ClientIdTrustConfiguration(),
    ) = MobileWalletConfig(
        walletId = "android-demo-$prefix-${UUID.randomUUID()}",
        keyAuthorization = MobileWalletKeyAuthorization(policy = KeyUseAuthorizationPolicy.None),
        presentation = MobileWalletPresentationConfig(verifierTrust, transactionDataProfiles),
        onEvent = { event -> println("WALLET EVENT: $event") },
    )

    private suspend fun MobileWallet.receiveCredential(
        offerUrl: String,
        transactionCode: String?,
    ): List<String> =
        (startIssuance(MobileWalletIssuanceRequest(
                    offer = MobileWalletCredentialOffer.Uri(offerUrl),
                )) as MobileWalletIssuanceSession.PreAuthorized).accept(transactionCode, clientId = "eudiw-abca").storedCredentialIds()

    private fun WalletIssuanceOutcome.storedCredentialIds(): List<String> =
        when (this) {
            is WalletIssuanceOutcome.Stored -> credentialIds
            is WalletIssuanceOutcome.Deferred -> error("Expected stored credentials, got deferred outcome: $this")
            is WalletIssuanceOutcome.Cancelled -> error("Expected stored credentials, got cancelled outcome")
            is WalletIssuanceOutcome.Failed -> error("Expected stored credentials, got ${error.code}: ${error.message}")
        }

    @OptIn(ExperimentalSerializationApi::class)
    private val eudiVerifierTrust = ClientIdTrustConfiguration(
        x509TrustAnchors = InMemoryTrustStore(listOf(X509CertificateUtil.parseCertificatePem(EudiTestBackend.verifierTrustAnchorPem))),
    )

    private suspend fun createEudiWallet(config: MobileWalletConfig) =
        MobileWalletFactory(context).create(config.copy(presentation = MobileWalletPresentationConfig(
            verifierTrust = eudiVerifierTrust,
            transactionDataProfiles = config.presentation.transactionDataProfiles,
            paymentCredentialIssuers = config.presentation.paymentCredentialIssuers,
        )))
            .attachKeyAttestationProviderResolver(DemoKeyAttestationProviders())

    private suspend fun receiveAndPresentEudiCredential(credentialId: String) {
        val client = createEudiWallet(walletConfig("eudi-present-$credentialId"))
        val bootstrapResult = client.signingIdentity.initialize().activeIdentity()

        val offer = EudiTestBackend.generateOffer(credentialId)
        val credentialIds = client.receiveCredential(
            offerUrl = offer.offerUrl,
            transactionCode = offer.txCode,
        )
        assertTrue(credentialIds.isNotEmpty(), "Should receive EUDI credential $credentialId")

        val credentials = client.credentials()
        assertTrue(credentials.isNotEmpty(), "Should store EUDI credential $credentialId")

        val offeredCredentialId = EudiTestBackend.extractCredentialIdFromOfferUrl(offer.offerUrl)
        val transaction = EudiTestBackend.createVerifierTransaction(offeredCredentialId)
        val preview = assertIs<MobileWalletPresentationPreviewResult.Ready>(client.previewPresentation(transaction.authorizationRequestUri)).preview
        val selection = preview.credentialOptions.single { it.credentialId in credentialIds }.selection
        val presentResult = client.submitPresentation(client.reviewPresentation(
            preview.previewHandle,
            MobileWalletPresentationSelection(credentials = listOf(selection)),
            did = bootstrapResult.did,
        ))
        assertIs<MobileWalletPresentationResult.Transmitted.Succeeded>(
            presentResult,
            "EUDI presentation should succeed for $credentialId: credentials=$credentials, result=$presentResult",
        )

        EudiTestBackend.waitForVerifierSuccess(transaction.transactionId)
    }

    private suspend fun previewAndSubmitEudiCredential(credentialId: String) {
        val client = createEudiWallet(walletConfig("eudi-preview-submit-$credentialId"))
        val bootstrapResult = client.signingIdentity.initialize().activeIdentity()

        val offer = EudiTestBackend.generateOffer(credentialId)
        val credentialIds = client.receiveCredential(
            offerUrl = offer.offerUrl,
            transactionCode = offer.txCode,
        )
        assertTrue(credentialIds.isNotEmpty(), "Should receive EUDI credential $credentialId")

        val offeredCredentialId = EudiTestBackend.extractCredentialIdFromOfferUrl(offer.offerUrl)
        val transaction = EudiTestBackend.createVerifierTransaction(offeredCredentialId)
        val preview = client.previewPresentation(transaction.authorizationRequestUri).requireReadyPreview()
        assertTrue(
            preview.credentialOptions.isNotEmpty(),
            "Should preview a matching EUDI credential for $credentialId: preview=$preview",
        )
        assertTrue(
            preview.credentialOptions.all { it.credentialId in credentialIds },
            "Preview should only offer credentials received in this test: received=$credentialIds, preview=$preview",
        )
        assertTrue(
            preview.request.responseEncryption is MobileWalletResponseEncryption.Required,
            "EUDI verifier should request an encrypted response: preview=$preview",
        )

        val result = client.submitPresentation(client.reviewPresentation(
            previewHandle = preview.previewHandle,
            selection = MobileWalletPresentationSelection(
                credentials = preview.credentialOptions.map { option ->
                MobileWalletPresentationCredentialSelection(
                    queryId = option.queryId,
                    credentialId = option.credentialId,
                )
            },
            ),
            did = bootstrapResult.did,
        ))
        assertIs<MobileWalletPresentationResult.Transmitted.Succeeded>(
            result,
            "EUDI stepwise presentation should succeed for $credentialId: preview=$preview, result=$result",
        )

        EudiTestBackend.waitForVerifierSuccess(transaction.transactionId)
    }

    private enum class DemoPresentation { OpenWallet, ReopenedWallet }

    private suspend fun exerciseDemoPresentations(scenarioId: String, vararg modes: DemoPresentation) {
        val scenario = demoPresentationScenario(scenarioId)
        val config = walletConfig("present-${scenario.id}")
        val issuingClient = MobileWalletFactory(context).create(config)
        val bootstrapResult = issuingClient.signingIdentity.initialize().activeIdentity()

        val offer = DemoTestBackend.createOffer(scenario)
        val credentialIds = issuingClient.receiveCredential(offer.offerUrl, offer.txCode)
        assertTrue(
            credentialIds.isNotEmpty(),
            "Should receive ${scenario.displayName} from public demo issuer2",
        )

        for (mode in modes) {
            val client = if (mode == DemoPresentation.OpenWallet) issuingClient else MobileWalletFactory(context).create(config)
            val credentials = client.credentials()
            assertEquals(credentialIds.toSet(), credentials.map { it.id }.toSet(), "${scenario.id}/$mode stored credentials")
            assertStoredCredentialDisplayData(scenario = scenario, credentials = credentials)

            val session = DemoTestBackend.createVerifierSession(scenario)
            val preview = client.previewPresentation(session.authorizationRequestUri).requireReadyPreview()
            assertTrue(preview.credentialOptions.isNotEmpty(), "${scenario.id}/$mode should offer matching credentials: $preview")
            assertTrue(
                preview.credentialOptions.all { it.credentialId in credentialIds },
                "Preview should only offer credentials received in this test: received=$credentialIds, preview=$preview",
            )
            val result = client.submitPresentation(client.reviewPresentation(
                previewHandle = preview.previewHandle,
                selection = MobileWalletPresentationSelection(credentials = preview.credentialOptions.map { it.selection }),
                did = bootstrapResult.did,
            ))
            assertIs<MobileWalletPresentationResult.Transmitted.Succeeded>(
                result,
                "public demo verifier2 presentation should succeed for ${scenario.id}/$mode: credentials=$credentials, result=$result",
            )
            DemoTestBackend.waitForVerifierSuccess(session.sessionId)
        }
    }

    private fun demoPresentationScenario(id: String) = DemoTestBackend.presentationScenarios.first { it.id == id }

    private fun MobileWalletPresentationPreviewResult.requireReadyPreview(): MobileWalletPresentationPreview =
        (this as? MobileWalletPresentationPreviewResult.Ready)?.preview
            ?: error("Expected a valid presentation preview, got $this")

    @OptIn(ExperimentalSerializationApi::class)
    private fun invalidTransactionDataRequestUrl(): String = AuthorizationRequest(
        clientId = "redirect_uri:https://verifier.example/callback",
        redirectUri = "https://verifier.example/callback",
        responseMode = OpenID4VPResponseMode.FRAGMENT,
        nonce = "nonce",
        state = "state-123",
        dcqlQuery = DcqlQuery(
            credentials = listOf(
                CredentialQuery(
                    id = "pid",
                    format = CredentialFormat.DC_SD_JWT,
                    meta = NoMeta,
                )
            )
        ),
        transactionData = listOf(
            buildJsonObject {
                put("type", "unsupported")
                put("credential_ids", buildJsonArray { add(JsonPrimitive("pid")) })
            }.toString().encodeToByteArray().let(Base64.getUrlEncoder().withoutPadding()::encodeToString),
        ),
    ).toHttpUrl().toString()

    private fun MobileWalletPresentationPreview.singleReceivedCredentialOption(
        credentialIds: List<String>,
    ) = credentialOptions.singleOrNull { it.credentialId in credentialIds }
        ?: error("Expected exactly one preview option for received credential IDs $credentialIds: $this")

    private fun JsonObject.verifiedPresentationData(): JsonObject =
        verifiedDataObjects().firstOrNull {
            it["given_name"]?.jsonPrimitive?.contentOrNull == "Anna Maria" &&
                    it["family_name"]?.jsonPrimitive?.contentOrNull == "Musterfrau"
        } ?: error("Missing verified_data object for EUDI PID presentation: $this")

    private fun JsonElement.verifiedDataObjects(): List<JsonObject> =
        when (this) {
            is JsonObject -> entries.flatMap { (key, value) ->
                if (key == "verified_data" && value is JsonObject) {
                    listOf(value)
                } else {
                    value.verifiedDataObjects()
                }
            }
            is JsonArray -> flatMap { it.verifiedDataObjects() }
            else -> emptyList()
        }

    private fun JsonObject.presentedCredentialData(): List<JsonObject> =
        this["presented_credentials"]
            ?.jsonObject
            ?.values
            ?.flatMap { credentialEntries ->
                credentialEntries.jsonArray.mapNotNull { credential ->
                    credential.jsonObject["credentialData"]?.jsonObject
                }
            }.orEmpty()

    private fun JsonObject.presentedSdJwtDisclosureClaimNames(): Set<String> =
        presentedVpTokens()
            .flatMap { token -> token.sdJwtDisclosureClaimNames() }
            .toSet()

    private fun JsonObject.presentedVpTokens(): List<String> {
        val vpToken = this["presented_raw_data"]
            ?.jsonObject
            ?.get("vpToken")
            ?: return emptyList()
        return when (vpToken) {
            is JsonObject -> vpToken.values.flatMap { value ->
                when (value) {
                    is JsonArray -> value.mapNotNull { it.jsonPrimitive.contentOrNull }
                    else -> value.jsonPrimitive.contentOrNull?.let(::listOf).orEmpty()
                }
            }
            is JsonArray -> vpToken.mapNotNull { it.jsonPrimitive.contentOrNull }
            else -> vpToken.jsonPrimitive.contentOrNull?.let(::listOf).orEmpty()
        }
    }

    private fun String.sdJwtDisclosureClaimNames(): List<String> =
        split("~")
            .drop(1)
            .filter { part -> part.isNotBlank() && "." !in part }
            .mapNotNull { encodedDisclosure ->
                runCatching {
                    val decoded = Base64.getUrlDecoder().decode(encodedDisclosure).decodeToString()
                    displayJson.parseToJsonElement(decoded)
                        .jsonArray
                        .getOrNull(1)
                        ?.jsonPrimitive
                        ?.contentOrNull
                }.getOrNull()
            }

    private fun assertStoredCredentialDisplayData(
        scenario: DemoTestBackend.CredentialScenario,
        credentials: List<MobileWalletCredential>,
    ) {
        val credential = credentials.firstOrNull { it.format == scenario.format } ?: credentials.single()
        assertEquals(scenario.format, credential.format, "${scenario.displayName} should expose the expected format")

        val credentialData = displayJson.parseToJsonElement(credential.credentialDataJson)
        assertTrue(
            credentialData.containsAnyUserFacingClaim(),
            "${scenario.displayName} display data should include readable user-facing claims: ${credential.credentialDataJson}",
        )
        assertTrue(
            credentialData.jsonObject.keys.any { it != "_sd" },
            "${scenario.displayName} display data should not expose only selective-disclosure commitments",
        )
    }

    private fun JsonElement.containsAnyUserFacingClaim(): Boolean =
        when (this) {
            is JsonObject -> keys.any { it.normalizedClaimName() in userFacingClaimNames } ||
                    values.any { it.containsAnyUserFacingClaim() }
            is JsonArray -> any { it.containsAnyUserFacingClaim() }
            else -> false
        }

    private fun String.normalizedClaimName(): String =
        filter { it.isLetterOrDigit() }.lowercase()

    private val displayJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val userFacingClaimNames = setOf(
        "birthdate",
        "birthplace",
        "documentnumber",
        "familyname",
        "familynamebirth",
        "givenname",
        "nationality",
        "portrait",
        "residentcity",
        "residentcountry",
        "residentstate",
        "residentstreet",
    )
}

private fun SigningIdentityOperationResult.activeIdentity(): SigningIdentity =
    kotlin.test.assertIs<SigningIdentityOperationResult.Active>(this).identity
