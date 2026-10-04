package id.walt.wallet2.consent

import id.walt.credentials.formats.SdJwtCredential
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.crypto2.keys.*
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.dcql.DcqlMatcher
import id.walt.dcql.RawDcqlCredential
import id.walt.dcql.models.CredentialFormat
import id.walt.dcql.models.CredentialQuery
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.verifier.openid.models.openid.OpenID4VPResponseMode
import id.walt.wallet2.data.WalletKeyStoreEntry
import id.walt.wallet2.handlers.PresentationCredentialSelection
import id.walt.wallet2.handlers.PresentationDisclosureSelection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import kotlin.test.*

class RetainedPaymentConsentTest {
    private fun test(block: suspend Context.() -> Unit) = runTest { withContext(Dispatchers.Default) {
        val fixture = PaymentConsentFixture.create()
        try { Context(fixture, fixture.credential()).block() } finally { fixture.close() }
    } }

    private class Context(val fixture: PaymentConsentFixture, val credential: SdJwtCredential) {
        val consent = RetainedPaymentConsent()
        val key = WalletKeyStoreEntry("holder", null, fixture.issuerKey) // Synthetic test key; no native authorization claimed.
        val policy = PaymentConsentPolicy(fixture.resolver, listOf("en"))
        val credentials = listOf(PresentationCredentialSelection("payment", "card"))
        val disclosures = listOf(PresentationDisclosureSelection("payment", "card", "card_last4"))
        val transaction = buildJsonObject {
            put("type", TS12_PAYMENT_TYPE); put("credential_ids", JsonArray(listOf(JsonPrimitive("payment"))))
            put("payload", fixture.payload)
        }.toString().encodeToByteArray().encodeToBase64Url()
        val request = AuthorizationRequest(nonce = "nonce", responseMode = OpenID4VPResponseMode.DC_API, transactionData = listOf(transaction))
        val selected = mapOf("payment" to listOf(DcqlMatcher.DcqlMatchResult(
            RawDcqlCredential("card", "dc+sd-jwt", credential.credentialData, originalCredential = credential), null,
            CredentialQuery(id = "payment", format = CredentialFormat.DC_SD_JWT, meta = id.walt.dcql.models.meta.SdJwtVcMeta(vctValues = listOf("$ISSUER/vct"))),
        )))
        suspend fun prepare() = assertNotNull(consent.prepare(request, selected, credentials, disclosures, key, policy))
        suspend fun confirm(revision: String?, request: AuthorizationRequest = this.request,
            credentials: List<PresentationCredentialSelection> = this.credentials,
            disclosures: List<PresentationDisclosureSelection>? = this.disclosures,
            key: WalletKeyStoreEntry = this.key, policy: PaymentConsentPolicy = this.policy,
            selected: Map<String, List<DcqlMatcher.DcqlMatchResult>> = this.selected,
        ) = consent.confirm(revision, request, selected, credentials, disclosures, key, policy)
    }

    @Test fun confirmationUsesReviewedSnapshotAndIsNeverReusable() = test {
        val prepared = prepare()
        val fetches = fixture.requests.size
        fixture.metadata = JsonObject(emptyMap()) // A changing remote document must not replace the reviewed snapshot.
        confirm(prepared.revision)
        assertEquals(fetches, fixture.requests.size)
        assertFailure(PaymentConsentFailure.CONSENT_REQUIRED) { confirm(prepared.revision) }
    }

    @Test fun missingWrongAndCrossSessionRevisionsDoNotAuthorize() = test {
        assertFailure(PaymentConsentFailure.CONSENT_REQUIRED) { confirm(null) }
        prepare()
        assertFailure(PaymentConsentFailure.STALE_CONSENT) { confirm("not-the-reviewed-revision") }
        val prepared = prepare()
        assertFailure(PaymentConsentFailure.CONSENT_REQUIRED) {
            RetainedPaymentConsent().confirm(prepared.revision, request, selected, credentials, disclosures, key, policy)
        }
    }

    @Test fun changedRequestBytesKeySelectionDisclosureOrLanguageRequireReviewAgain() = test {
        suspend fun stale(action: suspend (String) -> Unit) {
            val revision = prepare().revision
            assertFailure(PaymentConsentFailure.STALE_CONSENT) { action(revision) }
            assertFailure(PaymentConsentFailure.CONSENT_REQUIRED) { confirm(revision) }
        }
        stale { confirm(it, request = request.copy(nonce = "different")) }
        stale { confirm(it, request = request.copy(transactionData = listOf(
            (" " + Json.parseToJsonElement(kotlin.io.encoding.Base64.UrlSafe.withPadding(kotlin.io.encoding.Base64.PaddingOption.ABSENT_OPTIONAL)
                .decode(transaction).decodeToString()).toString()).encodeToByteArray().encodeToBase64Url(),
        ))) }
        stale { confirm(it, credentials = listOf(PresentationCredentialSelection("payment", "other"))) }
        stale { confirm(it, disclosures = emptyList()) }
        stale { confirm(it, policy = PaymentConsentPolicy(fixture.resolver, listOf("de"))) }
        stale { confirm(it, key = key.copy(keyId = "replacement")) }
        val replacement = fixture.runtime.generateSoftwareKey(GenerateSoftwareKeyRequest(KeyId("issuer"), KeySpec.Ec(EcCurve.P256), setOf(KeyUsage.SIGN, KeyUsage.VERIFY)))
        stale { confirm(it, key = key.copy(crypto2Key = replacement)) }
        val revision = prepare().revision
        request.responseMode = OpenID4VPResponseMode.DC_API_JWT // AuthorizationRequest has a mutable property.
        assertFailure(PaymentConsentFailure.STALE_CONSENT) { confirm(revision) }
    }

    @Test fun allSelectedCredentialsAreBoundAndMultiplePaymentAuthorizationsAreUnsupported() = test {
        val prepared = prepare()
        val changed = selected.mapValues { (_, values) -> values.map { match -> match.copy(
            credential = (match.credential as RawDcqlCredential).copy(originalCredential = credential.copy(
                credentialData = JsonObject(credential.credentialData + ("card_last4" to JsonPrimitive("9999"))),
            )),
        ) } }
        assertFailure(PaymentConsentFailure.STALE_CONSENT) { confirm(prepared.revision, selected = changed) }
        assertFailure(PaymentConsentFailure.UNSUPPORTED_PAYMENT) {
            consent.prepare(request.copy(transactionData = listOf(transaction, transaction)), selected, credentials, disclosures, key, policy)
        }
        assertFailure(PaymentConsentFailure.UNSUPPORTED_PAYMENT) {
            consent.prepare(request, selected.mapValues { it.value + it.value }, credentials, disclosures, key, policy)
        }
    }

    @Test fun failedPreparationInvalidatesTheEarlierAcknowledgment() = test {
        val earlier = prepare()
        fixture.metadata = JsonObject(emptyMap())
        assertFailure(PaymentConsentFailure.INVALID_METADATA) { prepare() }
        assertFailure(PaymentConsentFailure.CONSENT_REQUIRED) { confirm(earlier.revision) }
    }
}
