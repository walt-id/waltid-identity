package id.walt.wallet2.mobile.test

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import id.walt.mobile.test.backend.DemoTestBackend
import id.walt.mobile.test.backend.EudiTestBackend
import id.walt.wallet2.mobile.MobileWalletConfig
import id.walt.wallet2.mobile.MobileWalletCredential
import id.walt.wallet2.mobile.MobileWalletFactory
import id.walt.wallet2.mobile.MobileWalletPresentationCredentialSelection
import id.walt.wallet2.mobile.MobileWalletPresentationDisclosureSelection
import id.walt.wallet2.mobile.MobileWalletTransactionDataProfile
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import java.util.Base64
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Android integration tests for the mobile wallet library.
 *
 * Exercises the full mobile stack: AndroidKeyStore crypto + SQLDelight persistence
 * + OID4VCI/VP protocol against the public EUDI test backend.
 *
 * Uses runBlocking (not runTest) because real network I/O requires real time
 * dispatchers — runTest's virtual time expires HTTP timeouts immediately.
 *
 * These are integration tests (not E2E UI tests) - they test the library directly
 * without UI automation. Run on every PR for fast feedback.
 */
class MobileWalletIntegrationTest {

    companion object {
        private const val TEST_WALLET_ID = "android-device-test-wallet"
        private const val PAYMENT_AUTHORIZATION_TYPE = "org.waltid.transaction-data.payment-authorization"

        private val DEMO_TRANSACTION_DATA_PROFILES = demoTransactionDataProfiles(
            paymentAuthorizationFields = listOf("amount", "currency", "payee"),
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
        val client = MobileWalletFactory(context).create()
        val result = client.bootstrap()
        assertNotNull(result.keyId, "bootstrap should create a key")
        assertNotNull(result.did, "bootstrap should create a DID")
        assertTrue(result.did.startsWith("did:"), "DID should start with 'did:'")
    }

    @Test
    fun receiveCredentialFromEudi() = runBlocking {
        val client = MobileWalletFactory(context).create()
        client.bootstrap()

        val offer = EudiTestBackend.generateOffer()
        val resolution = client.resolveOffer(offer.offerUrl)
        assertTrue(resolution.transactionCodeRequired, "EUDI offer should require a transaction code")
        val credentialIds = client.receive(offer.offerUrl, txCode = offer.txCode)
        assertTrue(credentialIds.isNotEmpty(), "Should receive at least one credential")
    }

    @Test
    fun receiveEudiPidSdJwtFromDemoIssuer2() = runBlocking {
        receiveCredentialFromDemoIssuer2("eudi-pid-sdjwt")
    }

    @Test
    fun receiveEudiPidMdocFromDemoIssuer2() = runBlocking {
        receiveCredentialFromDemoIssuer2("eudi-pid-mdoc")
    }

    @Test
    fun receiveIsoMdlFromDemoIssuer2() = runBlocking {
        receiveCredentialFromDemoIssuer2("iso-mdl")
    }

    @Test
    fun receiveAndPresentFullFlow() = runBlocking {
        val client = MobileWalletFactory(context).create(
            MobileWalletConfig(onEvent = { event -> println("WALLET EVENT: $event") })
        )
        val bootstrapResult = client.bootstrap()

        val offer = EudiTestBackend.generateOffer()
        val credentialIds = client.receive(offer.offerUrl, txCode = offer.txCode)
        assertTrue(credentialIds.isNotEmpty(), "Should receive at least one credential")

        val credentials = client.credentials()
        assertTrue(credentials.isNotEmpty(), "Should have stored credentials")

        val credentialId = EudiTestBackend.extractCredentialIdFromOfferUrl(offer.offerUrl)
        val transaction = EudiTestBackend.createVerifierTransaction(credentialId)
        val presentResult = client.present(transaction.authorizationRequestUri, did = bootstrapResult.did)
        assertTrue(presentResult.success, "Presentation should succeed: credentials=$credentials, result=$presentResult")

        EudiTestBackend.waitForVerifierSuccess(transaction.transactionId)
    }

    @Test
    fun previewAndSubmitFullFlowAgainstEudi() = runBlocking {
        val client = MobileWalletFactory(context).create(walletConfig("eudi-preview-submit"))
        val bootstrapResult = client.bootstrap()

        val offer = EudiTestBackend.generateOffer()
        val credentialIds = client.receive(offer.offerUrl, txCode = offer.txCode)
        assertTrue(credentialIds.isNotEmpty(), "Should receive at least one EUDI credential")

        val credentialId = EudiTestBackend.extractCredentialIdFromOfferUrl(offer.offerUrl)
        val transaction = EudiTestBackend.createVerifierTransaction(credentialId)
        val preview = client.previewPresentation(transaction.authorizationRequestUri)
        assertTrue(
            preview.credentialOptions.isNotEmpty(),
            "Should preview at least one matching EUDI credential: preview=$preview",
        )
        assertTrue(
            preview.credentialOptions.all { it.credentialId in credentialIds },
            "Preview should only offer credentials received in this test: received=$credentialIds, preview=$preview",
        )

        val result = client.submitPresentation(
            requestUrl = transaction.authorizationRequestUri,
            selectedCredentialOptions = preview.credentialOptions.map { option ->
                MobileWalletPresentationCredentialSelection(
                    queryId = option.queryId,
                    credentialId = option.credentialId,
                )
            },
            did = bootstrapResult.did,
        )
        assertTrue(
            result.success,
            "EUDI stepwise presentation should succeed: preview=$preview, result=$result",
        )

        EudiTestBackend.waitForVerifierSuccess(transaction.transactionId)
    }

    @Test
    fun receiveAndPresentEudiPidSdJwtAgainstDemoIssuer2AndVerifier2() = runBlocking {
        receiveAndPresentDemoCredential("eudi-pid-sdjwt")
    }

    @Test
    fun receiveAndPresentEudiPidMdocAgainstDemoIssuer2AndVerifier2() = runBlocking {
        receiveAndPresentDemoCredential("eudi-pid-mdoc")
    }

    @Test
    fun previewAndSubmitEudiPidSdJwtAgainstDemoIssuer2AndVerifier2() = runBlocking {
        previewAndSubmitDemoCredential("eudi-pid-sdjwt")
    }

    @Test
    fun previewAndSubmitEudiPidMdocAgainstDemoIssuer2AndVerifier2() = runBlocking {
        previewAndSubmitDemoCredential("eudi-pid-mdoc")
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
        val bootstrapResult = client.bootstrap()

        val offer = DemoTestBackend.createOffer(scenario)
        val credentialIds = client.receive(offer.offerUrl, txCode = offer.txCode)
        assertTrue(
            credentialIds.isNotEmpty(),
            "Should receive ${scenario.displayName} from public demo issuer2",
        )

        val session = DemoTestBackend.createTransactionDataVerifierSession(scenario)
        val preview = client.previewPresentation(session.authorizationRequestUri)
        val transactionData = preview.request.transactionData.singleOrNull()
        assertNotNull(transactionData, "Preview should expose payment transaction data: preview=$preview")
        assertEquals("Payment Authorization", transactionData.displayName)
        assertEquals(listOf("pid"), transactionData.credentialQueryIds)
        assertTrue(
            transactionData.detailsJson.contains("\"amount\":\"42.00\"") &&
                transactionData.detailsJson.contains("\"currency\":\"EUR\"") &&
                transactionData.detailsJson.contains("\"payee\":\"ACME Corp\""),
            "Preview should expose readable payment details: ${transactionData.detailsJson}",
        )
        val result = client.submitPresentation(
            requestUrl = session.authorizationRequestUri,
            selectedCredentialOptions = preview.credentialOptions.map { option -> option.selection },
            did = bootstrapResult.did,
        )
        assertTrue(
            result.success,
            "public demo verifier2 transaction-data presentation should succeed: preview=$preview, result=$result",
        )

        DemoTestBackend.waitForVerifierSuccess(session.sessionId)
    }

    @Test
    fun previewAndSubmitOptionalBirthDateClaimSetAgainstDemoIssuer2AndVerifier2() = runBlocking {
        val scenario = DemoTestBackend.optionalBirthDatePresentationScenario
        val client = MobileWalletFactory(context).create(walletConfig("optional-birth-date-${scenario.id}"))
        val bootstrapResult = client.bootstrap()

        val offer = DemoTestBackend.createOffer(scenario)
        val credentialIds = client.receive(offer.offerUrl, txCode = offer.txCode)
        assertTrue(
            credentialIds.isNotEmpty(),
            "Should receive ${scenario.displayName} from public demo issuer2",
        )

        val defaultOffSession = DemoTestBackend.createVerifierSession(scenario)
        val defaultOffPreview = client.previewPresentation(defaultOffSession.authorizationRequestUri)
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

        val defaultOffResult = client.submitPresentation(
            requestUrl = defaultOffSession.authorizationRequestUri,
            selectedCredentialOptions = listOf(defaultOffOption.selection),
            selectedDisclosureOptions = emptyList(),
            did = bootstrapResult.did,
        )
        assertTrue(
            defaultOffResult.success,
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
        val selectedPreview = client.previewPresentation(selectedSession.authorizationRequestUri)
        val selectedOption = selectedPreview.singleReceivedCredentialOption(credentialIds)
        val selectedBirthDate = selectedOption.disclosures.singleOrNull { it.name == "birth_date" }
        assertNotNull(
            selectedBirthDate,
            "Preview should expose optional birth_date before selected submission: preview=$selectedPreview",
        )

        val selectedResult = client.submitPresentation(
            requestUrl = selectedSession.authorizationRequestUri,
            selectedCredentialOptions = listOf(selectedOption.selection),
            selectedDisclosureOptions = listOf(
                MobileWalletPresentationDisclosureSelection(
                    queryId = selectedOption.queryId,
                    credentialId = selectedOption.credentialId,
                    path = selectedBirthDate.path,
                )
            ),
            did = bootstrapResult.did,
        )
        assertTrue(
            selectedResult.success,
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
        receiveAndPresentDemoCredential("iso-mdl")
    }

    @Test
    fun credentialPersistsAcrossWalletRecreation() = runBlocking {
        val walletConfig = MobileWalletConfig(
            walletId = TEST_WALLET_ID,
            onEvent = { event -> println("WALLET EVENT: $event") },
        )

        val client1 = MobileWalletFactory(context).create(walletConfig)
        val bootstrapResult = client1.bootstrap()

        val offer = EudiTestBackend.generateOffer()
        client1.receive(offer.offerUrl, txCode = offer.txCode)

        val client2 = MobileWalletFactory(context).create(walletConfig)
        val credentials = client2.credentials()
        assertTrue(credentials.isNotEmpty(), "Credentials should persist across client recreation")

        val credentialId = EudiTestBackend.extractCredentialIdFromOfferUrl(offer.offerUrl)
        val transaction = EudiTestBackend.createVerifierTransaction(credentialId)
        val presentResult = client2.present(transaction.authorizationRequestUri, did = bootstrapResult.did)
        assertTrue(presentResult.success, "Should present from persisted credentials: credentials=$credentials, result=$presentResult")
        EudiTestBackend.waitForVerifierSuccess(transaction.transactionId)
    }

    @Test
    fun demoCredentialPersistsAcrossWalletRecreation() = runBlocking {
        val scenario = DemoTestBackend.persistenceScenario
        val walletConfig = walletConfig("persist-${scenario.id}")

        val client1 = MobileWalletFactory(context).create(walletConfig)
        val bootstrapResult = client1.bootstrap()

        val offer = DemoTestBackend.createOffer(scenario)
        client1.receive(offer.offerUrl, txCode = offer.txCode)

        val client2 = MobileWalletFactory(context).create(walletConfig)
        val credentials = client2.credentials()
        assertTrue(credentials.isNotEmpty(), "public demo credential should persist across client recreation")
        assertStoredCredentialDisplayData(scenario = DemoTestBackend.persistenceScenario, credentials = credentials)

        val session = DemoTestBackend.createVerifierSession(scenario)
        val presentResult = client2.present(session.authorizationRequestUri, did = bootstrapResult.did)
        assertTrue(
            presentResult.success,
            "Should present persisted public demo credential for ${scenario.displayName}: credentials=$credentials, result=$presentResult",
        )
        DemoTestBackend.waitForVerifierSuccess(session.sessionId)
    }

    private fun walletConfig(
        prefix: String,
        transactionDataProfiles: List<MobileWalletTransactionDataProfile> = DEMO_TRANSACTION_DATA_PROFILES,
    ) = MobileWalletConfig(
        walletId = "android-demo-$prefix-${UUID.randomUUID()}",
        onEvent = { event -> println("WALLET EVENT: $event") },
        transactionDataProfiles = transactionDataProfiles,
    )

    private suspend fun receiveCredentialFromDemoIssuer2(scenarioId: String) {
        val scenario = demoScenario(scenarioId)
        val client = MobileWalletFactory(context).create(walletConfig("receive-${scenario.id}"))
        client.bootstrap()

        val offer = DemoTestBackend.createOffer(scenario)
        val credentialIds = client.receive(offer.offerUrl, txCode = offer.txCode)
        assertTrue(
            credentialIds.isNotEmpty(),
            "Should receive at least one ${scenario.displayName} credential from public demo issuer2",
        )
        assertStoredCredentialDisplayData(scenario = scenario, credentials = client.credentials())
    }

    private suspend fun receiveAndPresentDemoCredential(scenarioId: String) {
        val scenario = demoPresentationScenario(scenarioId)
        val client = MobileWalletFactory(context).create(walletConfig("present-${scenario.id}"))
        val bootstrapResult = client.bootstrap()

        val offer = DemoTestBackend.createOffer(scenario)
        val credentialIds = client.receive(offer.offerUrl, txCode = offer.txCode)
        assertTrue(
            credentialIds.isNotEmpty(),
            "Should receive ${scenario.displayName} from public demo issuer2",
        )

        val credentials = client.credentials()
        assertTrue(credentials.isNotEmpty(), "Should have stored ${scenario.displayName} credentials")
        assertStoredCredentialDisplayData(scenario = scenario, credentials = credentials)

        val session = DemoTestBackend.createVerifierSession(scenario)
        val presentResult = client.present(session.authorizationRequestUri, did = bootstrapResult.did)
        assertTrue(
            presentResult.success,
            "public demo verifier2 presentation should succeed for ${scenario.displayName}: credentials=$credentials, result=$presentResult",
        )

        DemoTestBackend.waitForVerifierSuccess(session.sessionId)
    }

    private suspend fun previewAndSubmitDemoCredential(scenarioId: String) {
        val scenario = demoPresentationScenario(scenarioId)
        val client = MobileWalletFactory(context).create(walletConfig("preview-submit-${scenario.id}"))
        val bootstrapResult = client.bootstrap()

        val offer = DemoTestBackend.createOffer(scenario)
        val credentialIds = client.receive(offer.offerUrl, txCode = offer.txCode)
        assertTrue(
            credentialIds.isNotEmpty(),
            "Should receive ${scenario.displayName} from public demo issuer2",
        )

        val session = DemoTestBackend.createVerifierSession(scenario)
        val preview = client.previewPresentation(session.authorizationRequestUri)
        assertTrue(
            preview.credentialOptions.isNotEmpty(),
            "Should preview at least one matching credential for ${scenario.displayName}: preview=$preview",
        )
        assertTrue(
            preview.credentialOptions.all { it.credentialId in credentialIds },
            "Preview should only offer credentials received in this test: received=$credentialIds, preview=$preview",
        )

        val result = client.submitPresentation(
            requestUrl = session.authorizationRequestUri,
            selectedCredentialOptions = preview.credentialOptions.map { option ->
                MobileWalletPresentationCredentialSelection(
                    queryId = option.queryId,
                    credentialId = option.credentialId,
                )
            },
            did = bootstrapResult.did,
        )
        assertTrue(
            result.success,
            "public demo verifier2 stepwise presentation should succeed for ${scenario.displayName}: preview=$preview, result=$result",
        )

        DemoTestBackend.waitForVerifierSuccess(session.sessionId)
    }

    private fun demoScenario(id: String) = DemoTestBackend.scenarios.first { it.id == id }

    private fun demoPresentationScenario(id: String) = DemoTestBackend.presentationScenarios.first { it.id == id }

    private val id.walt.wallet2.mobile.MobileWalletPresentationCredentialOption.selection
        get() = MobileWalletPresentationCredentialSelection(
            queryId = queryId,
            credentialId = credentialId,
        )

    private fun id.walt.wallet2.mobile.MobileWalletPresentationPreview.singleReceivedCredentialOption(
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
