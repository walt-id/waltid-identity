package id.walt.wallet2.consent

import dev.whyoleg.cryptography.random.CryptographyRandom
import id.walt.credentials.formats.DigitalCredential
import id.walt.credentials.formats.SdJwtCredential
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.crypto2.jose.Jwk
import id.walt.crypto2.keys.toPublicJwk
import id.walt.dcql.DcqlMatcher
import id.walt.dcql.RawDcqlCredential
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.verifier.openid.transactiondata.decodeList
import id.walt.wallet2.data.WalletKeyStoreEntry
import id.walt.wallet2.handlers.PresentationCredentialSelection
import id.walt.wallet2.handlers.PresentationDisclosureSelection
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.Json

/** Mobile consent policy; kept outside protocol presenters and stateless HTTP APIs. */
class PaymentConsentPolicy(val resolver: PaymentConsentResolver, preferredLocales: List<String>) {
    val preferredLocales: List<String> = preferredLocales.toList()
}

/** Opaque acknowledgment of one immutable review. A host must display [payment] before submission. */
class PreparedPaymentConsent internal constructor(
    val revision: String, val payment: PaymentConsent,
    /** Wallet-owned warning must remain visible even when the issuer supplies no security hint. */
    val requiresUnsignedRequestWarning: Boolean,
)

/** Accessed only under the owning preview session's exclusive lease. */
internal class RetainedPaymentConsent {
    private var prepared: Pair<PaymentConsentBinding, PreparedPaymentConsent>? = null

    suspend fun prepare(
        request: AuthorizationRequest,
        selection: Map<String, List<DcqlMatcher.DcqlMatchResult>>,
        credentials: List<PresentationCredentialSelection>,
        disclosures: List<PresentationDisclosureSelection>?,
        key: WalletKeyStoreEntry,
        policy: PaymentConsentPolicy,
        holderDid: String? = null,
        requiresUnsignedRequestWarning: Boolean = false,
    ): PreparedPaymentConsent? {
        prepared = null // Failed re-preparation cannot leave an older acknowledgment available.
        val payment = selectedPayment(request, selection) ?: return null
        val binding = binding(request, selection, credentials, disclosures, key, policy, holderDid)
        val consent = policy.resolver.resolve(payment.first, payment.second, policy.preferredLocales)
        return PreparedPaymentConsent(CryptographyRandom.nextBytes(32).encodeToBase64Url(), consent, requiresUnsignedRequestWarning).also {
            prepared = binding to it
        }
    }

    suspend fun confirm(
        revision: String?,
        request: AuthorizationRequest,
        selection: Map<String, List<DcqlMatcher.DcqlMatchResult>>,
        credentials: List<PresentationCredentialSelection>,
        disclosures: List<PresentationDisclosureSelection>?,
        key: WalletKeyStoreEntry,
        policy: PaymentConsentPolicy,
        holderDid: String? = null,
    ) {
        if (selectedPayment(request, selection) == null) return
        val snapshot = prepared
        prepared = null // Every attempt needs a fresh review after cancellation or failure.
        if (revision == null || snapshot == null) consentFailure(PaymentConsentFailure.CONSENT_REQUIRED)
        if (revision != snapshot.second.revision || snapshot.first != binding(request, selection, credentials, disclosures, key, policy, holderDid)) {
            consentFailure(PaymentConsentFailure.STALE_CONSENT)
        }
    }
}

private fun selectedPayment(
    request: AuthorizationRequest,
    selection: Map<String, List<DcqlMatcher.DcqlMatchResult>>,
): Pair<SdJwtCredential, JsonObject>? {
    val payments = decodeList(request.transactionData.orEmpty()).filter { it.transactionData.type == TS12_PAYMENT_TYPE }
    val paymentCredentials = payments.flatMap { payment -> payment.transactionData.credentialIds.flatMap { query ->
        selection[query].orEmpty().map { (it.credential as RawDcqlCredential).originalCredential as DigitalCredential }
    } }
    if (paymentCredentials.none { it is SdJwtCredential }) return null // Legacy mdoc retains generic mandatory review.
    if (payments.size != 1 || paymentCredentials.size != 1) consentFailure(PaymentConsentFailure.UNSUPPORTED_PAYMENT)
    val details = payments.single().details
    if (details.keys != setOf("payload")) consentFailure(PaymentConsentFailure.UNSUPPORTED_PAYMENT)
    return (paymentCredentials.single() as SdJwtCredential) to
        (details["payload"] as? JsonObject ?: consentFailure(PaymentConsentFailure.INVALID_PAYMENT))
}

private data class PaymentConsentBinding(
    val requestJson: String,
    val credentials: List<PresentationCredentialSelection>,
    val disclosures: List<PresentationDisclosureSelection>?,
    val credentialWires: Map<String, String>,
    val credentialData: Map<String, String>,
    val holderDid: String?,
    val keyId: String,
    val keyThumbprint: String,
    val locales: List<String>,
)

private suspend fun binding(
    request: AuthorizationRequest,
    selection: Map<String, List<DcqlMatcher.DcqlMatchResult>>,
    credentials: List<PresentationCredentialSelection>,
    disclosures: List<PresentationDisclosureSelection>?,
    key: WalletKeyStoreEntry,
    policy: PaymentConsentPolicy,
    holderDid: String?,
): PaymentConsentBinding {
    val selected = selection.values.flatten().associate { match ->
        val raw = match.credential as RawDcqlCredential
        raw.id to (raw.originalCredential as DigitalCredential)
    }
    val holder = key.crypto2Key ?: consentFailure(PaymentConsentFailure.STALE_CONSENT)
    val publicKey = holder.capabilities.publicKeyExporter?.exportPublicKey()?.toPublicJwk(holder.spec)
        ?: consentFailure(PaymentConsentFailure.STALE_CONSENT)
    return PaymentConsentBinding(
        Json.encodeToString(AuthorizationRequest.serializer(), request),
        credentials.sortedWith(compareBy({ it.queryId }, { it.credentialId })).toList(),
        disclosures?.sortedWith(compareBy({ it.queryId }, { it.credentialId }, { it.path }))?.toList(),
        selected.mapValues { (_, credential) -> (credential as? SdJwtCredential)?.signedWithDisclosures ?: credential.signed.orEmpty() },
        selected.mapValues { it.value.credentialData.toString() },
        holderDid, key.keyId, Jwk.sha256Thumbprint(publicKey), policy.preferredLocales.toList(),
    )
}
