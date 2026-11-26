package id.walt.wallet2.mobile.digitalcredentials

import id.walt.mdoc.readertrust.ReaderTrustDecision
import id.walt.mdoc.readertrust.ReaderTrustState
import id.walt.wallet2.mobile.MobileWallet
import id.walt.wallet2.mobile.presentation.MobileWalletPresentationCredentialOption
import id.walt.wallet2.mobile.presentation.MobileWalletPresentationCredentialRequirement
import id.walt.wallet2.mobile.presentation.MobileWalletPresentationRequestInfo
import id.walt.wallet2.mobile.presentation.MobileWalletTransactionDataItem
import id.walt.wallet2.mobile.presentation.MobileWalletVerifierMetadata

/**
 * Platform-neutral request passed by Android Credential Manager or an Apple provider extension.
 *
 * @property protocol Platform protocol identifier.
 * @property dataJson Protocol request data encoded as JSON.
 * @property verifiedOrigin Authenticated origin reported by the platform.
 * @property selectedRegistryEntryIds Entries selected by the platform matcher.
 */
public data class MobileWalletDigitalCredentialRequest(
    public val protocol: String,
    public val dataJson: String,
    public val verifiedOrigin: String,
    public val selectedRegistryEntryIds: List<String> = emptyList(),
)

/**
 * Verifier and transaction metadata extracted from an OpenID4VP request received through a
 * Digital Credentials API.
 *
 * Unlike [MobileWalletPresentationRequestInfo], [clientId] is nullable: the OpenID4VP DC API
 * requires unsigned requests to omit and ignore `client_id`. The authenticated platform origin
 * is exposed separately by [MobileWalletDigitalCredentialPreview.verifiedOrigin] and is the
 * authoritative requester identity for those requests.
 *
 * @property clientId Authenticated signed-request client identifier, or null for unsigned requests.
 * @property verifierMetadata Typed verifier metadata supplied by the request, when available.
 * @property nonce Required OpenID4VP nonce value supplied by the verifier.
 * @property responseMode Serialized OpenID4VP DC API response mode, when provided.
 * @property transactionData Decoded transaction data items requested by the verifier.
 */
public data class MobileWalletDigitalCredentialRequestInfo(
    public val clientId: String?,
    public val verifierMetadata: MobileWalletVerifierMetadata?,
    public val nonce: String,
    public val responseMode: String?,
    public val transactionData: List<MobileWalletTransactionDataItem> = emptyList(),
) {
    init {
        require(clientId == null || clientId.isNotBlank()) {
            "A Digital Credentials request client ID must not be blank."
        }
        require(nonce.isNotBlank()) { "A Digital Credentials request nonce must not be blank." }
    }
}

/**
 * Consent preview retained by the SDK until [MobileWallet.submitDigitalCredentialPresentation].
 *
 * @property requestId Opaque identifier binding the later submission to this preview.
 * @property protocol Platform protocol identifier.
 * @property verifiedOrigin Authenticated requesting origin.
 * @property request Parsed presentation request metadata.
 * @property credentialOptions Matching wallet credentials.
 * @property credentialRequirements Required credential-query combinations.
 * @property readerAuthentication Whether and how the reader authenticated the request.
 * @property readerTrust Trust policy decision for the authenticated reader; present only when
 * [readerAuthentication] is [MobileWalletReaderAuthentication.VERIFIED].
 */
public data class MobileWalletDigitalCredentialPreview(
    public val requestId: String,
    public val protocol: String,
    public val verifiedOrigin: String,
    public val request: MobileWalletDigitalCredentialRequestInfo,
    public val credentialOptions: List<MobileWalletPresentationCredentialOption>,
    public val credentialRequirements: List<MobileWalletPresentationCredentialRequirement>,
    public val readerAuthentication: MobileWalletReaderAuthentication,
    public val readerTrust: ReaderTrustDecision?,
)

/**
 * Reader authentication state of a presentation request.
 *
 * A request whose reader authentication fails cryptographic verification is rejected and produces no
 * state at all, so every state here describes a still-processable request. Only [VERIFIED] comes with
 * a trust decision, and only a [ReaderTrustState.TRUSTED] decision means a reader was identified.
 */
public enum class MobileWalletReaderAuthentication {
    /** The protocol carries no reader authentication, as with the OpenID4VP Digital Credentials API. */
    NOT_APPLICABLE,

    /** The request supports reader authentication but carried none, so the reader is anonymous. */
    NOT_AUTHENTICATED,

    /**
     * Reader authentication has not been checked yet because the platform withholds the raw request
     * until the user consents.
     *
     * Apple's IdentityDocumentServices exposes only a parsed request before consent. The signature is
     * still verified before any credential data is released, but that happens at submission, so a
     * consent dialog cannot yet name the reader.
     */
    PENDING_RAW_REQUEST,

    /** Every reader authentication signature verified, and the reader trust policy was consulted. */
    VERIFIED,
}

/**
 * OS-mediated response. [dataJson] is returned to the platform and is never direct-posted over HTTP.
 *
 * @property protocol Protocol identifier from the request.
 * @property dataJson Platform response data encoded as JSON.
 */
public data class MobileWalletDigitalCredentialResponse(
    public val protocol: String,
    public val dataJson: String,
)

/**
 * Selected platform registry entry no longer maps to a current wallet credential.
 *
 * @property registryEntryId Stale platform registry entry identifier.
 */
public class MobileWalletStaleRegistryEntryException(public val registryEntryId: String) :
    IllegalArgumentException("Selected credential registry entry is stale")
