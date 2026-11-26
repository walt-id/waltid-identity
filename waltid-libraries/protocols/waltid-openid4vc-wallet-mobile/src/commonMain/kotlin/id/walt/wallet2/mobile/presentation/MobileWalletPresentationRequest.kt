package id.walt.wallet2.mobile.presentation

import id.walt.wallet2.mobile.digitalcredentials.MobileWalletDigitalCredentialRequestInfo

/**
 * Partial request context retained when an OpenID4VP request is invalid.
 *
 * A reportable invalid request has a validated, non-blank [clientId]. Its [nonce] remains nullable
 * because a missing nonce can itself be the validation error. A ready preview instead exposes a
 * validated, non-null nonce through [MobileWalletPresentationRequestInfo].
 *
 * @property clientId Required OpenID4VP `client_id` value identifying the verifier.
 * @property requestAuthentication Authentication established for the Authorization Request or its Request Object.
 * @property responseEncryption Response-encryption state selected for this request.
 * @property verifierMetadata Typed verifier metadata supplied by the OpenID4VP client, when available.
 * @property responseUri Verifier response URI to which the wallet would submit the presentation or error, when provided.
 * @property state OpenID4VP state value supplied by the verifier, when provided.
 * @property nonce OpenID4VP nonce value supplied by the verifier, when provided. May be null if the missing nonce is the validation error.
 */
public data class MobileWalletPresentationRequestContext(
    val clientId: String,
    val requestAuthentication: MobileWalletRequestAuthentication,
    val responseEncryption: MobileWalletResponseEncryption,
    val verifierMetadata: MobileWalletVerifierMetadata? = null,
    val responseUri: String? = null,
    val state: String? = null,
    val nonce: String? = null,
) {
    init {
        require(clientId.isNotBlank()) {
            "A reportable presentation request client ID must not be blank."
        }
    }
}

/**
 * A required presentation credential-query combination.
 *
 * Each inner option contains DCQL credential query IDs that must all be selected together.
 * At least one option must be satisfied for this requirement to be complete.
 *
 * @property options Alternative DCQL credential query ID sets that satisfy this requirement.
 */
public class MobileWalletPresentationCredentialRequirement(options: List<List<String>>) {
    public val options: List<List<String>> = buildList { options.forEach { option -> add(buildList { addAll(option) }) } }

    public override fun equals(other: Any?): Boolean = other is MobileWalletPresentationCredentialRequirement && options == other.options
    public override fun hashCode(): Int = options.hashCode()

    init {
        require(this.options.isNotEmpty()) {
            "A presentation credential requirement must contain at least one option."
        }
        require(this.options.all { it.isNotEmpty() }) {
            "Each presentation credential requirement option must contain at least one query ID."
        }
        require(this.options.flatten().all(String::isNotBlank)) {
            "Presentation credential requirement query IDs must not be blank."
        }
    }
}

/**
 * Verifier and transaction metadata extracted from a presentation request.
 *
 * @property clientId Required OpenID4VP `client_id` value identifying the verifier. Digital
 * Credentials API previews use [MobileWalletDigitalCredentialRequestInfo] instead because
 * unsigned requests intentionally have no trusted request-supplied `client_id`.
 * @property requestAuthentication Authentication established for the Authorization Request or its Request Object.
 * @property nonce Required OpenID4VP nonce value supplied by the verifier.
 * @property responseEncryption Response-encryption state selected for this request.
 * @property verifierMetadata Typed verifier metadata supplied by the OpenID4VP client, when available.
 * @property responseUri Verifier response URI to which the wallet will submit the presentation, when provided.
 * @property state OpenID4VP state value supplied by the verifier, when provided.
 * @property responseMode Serialized OpenID4VP response mode requested by the verifier.
 * @property transactionData Decoded transaction data items requested by the verifier.
 */
public data class MobileWalletPresentationRequestInfo(
    val clientId: String,
    val requestAuthentication: MobileWalletRequestAuthentication,
    val nonce: String,
    val responseEncryption: MobileWalletResponseEncryption,
    val verifierMetadata: MobileWalletVerifierMetadata? = null,
    val responseUri: String? = null,
    val state: String? = null,
    val responseMode: String? = null,
    val transactionData: List<MobileWalletTransactionDataItem> = emptyList(),
) {
    init {
        require(clientId.isNotBlank()) { "A presentation request client ID must not be blank." }
        require(nonce.isNotBlank()) { "A presentation request nonce must not be blank." }
    }
}

/** Authentication established for an OpenID4VP Authorization Request or Request Object. */
public sealed interface MobileWalletRequestAuthentication {
    /** The request was not authenticated by a signed Request Object. */
    public data object Unauthenticated : MobileWalletRequestAuthentication

    /** The Request Object was authenticated by the OpenID4VP client-ID layer. */
    public data class Authenticated(
        /** Exact compact Request Object received from the verifier. */
        public val compactRequestObject: String,
        /** JWS algorithm established by request authentication. */
        public val algorithm: String,
        /** Request-object signing key identifier, when supplied. */
        public val keyId: String?,
        /** Client identifier scheme established by the authentication layer. */
        public val clientIdScheme: MobileWalletClientIdScheme,
    ) : MobileWalletRequestAuthentication
}

/** Client identifier scheme established while authenticating an OpenID4VP request. */
public enum class MobileWalletClientIdScheme {
    /** The verifier is identified through pre-registered metadata. */
    PRE_REGISTERED,
    /** The verifier is identified by a redirect URI. */
    REDIRECT_URI,
    /** The verifier is identified by an X.509 SAN DNS name. */
    X509_SAN_DNS,
    /** The verifier is identified by an X.509 certificate hash. */
    X509_HASH,
    /** The verifier is identified by a decentralized identifier. */
    DECENTRALIZED_IDENTIFIER,
    /** The verifier is identified by a verifier attestation. */
    VERIFIER_ATTESTATION,
    /** The verifier is identified through OpenID Federation. */
    OPENID_FEDERATION,
}
