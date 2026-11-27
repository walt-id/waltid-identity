@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.wallet2.handlers

import id.walt.crypto.keys.DirectSerializedKey
import id.walt.openid4vci.proofs.Proofs
import id.walt.openid4vci.requests.authorization.AuthorizationDetail
import io.ktor.http.Url
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable
data class RequestTokenRequest(
    val tokenEndpoint: Url,
    val preAuthorizedCode: String,
    val credentialIssuer: String? = null,
    val txCode: String? = null,
    val clientId: String = Wallet2DefaultClientId,
    val redirectUri: Url = Url("openid://"),
    /** Manual client authentication; see [ReceiveCredentialRequest.tokenRequestHeaders]. */
    val tokenRequestHeaders: Map<String, String> = emptyMap(),
    val anonymousPreAuthorizedCode: Boolean = false,
    /** Select configurations and let metadata determine the authorization parameters. */
    val credentialConfigurationIds: List<String>? = null,
    val authorizationDetails: List<AuthorizationDetail>? = null,
    val scope: String? = null,
) {
    /** Retains the released constructor contract. */
    constructor(
        tokenEndpoint: Url,
        preAuthorizedCode: String,
        credentialIssuer: String? = null,
        txCode: String? = null,
        clientId: String = Wallet2DefaultClientId,
        redirectUri: Url = Url("openid://"),
        tokenRequestHeaders: Map<String, String> = emptyMap(),
        anonymousPreAuthorizedCode: Boolean = false,
    ) : this(
        tokenEndpoint, preAuthorizedCode, credentialIssuer, txCode, clientId, redirectUri, tokenRequestHeaders,
        anonymousPreAuthorizedCode, credentialConfigurationIds = null, authorizationDetails = null, scope = null,
    )

    /** Retains the released copy contract and preserves the added fields. */
    fun copy(
        tokenEndpoint: Url = this.tokenEndpoint,
        preAuthorizedCode: String = this.preAuthorizedCode,
        credentialIssuer: String? = this.credentialIssuer,
        txCode: String? = this.txCode,
        clientId: String = this.clientId,
        redirectUri: Url = this.redirectUri,
        tokenRequestHeaders: Map<String, String> = this.tokenRequestHeaders,
        anonymousPreAuthorizedCode: Boolean = this.anonymousPreAuthorizedCode,
    ): RequestTokenRequest = RequestTokenRequest(
        tokenEndpoint, preAuthorizedCode, credentialIssuer, txCode, clientId, redirectUri, tokenRequestHeaders,
        anonymousPreAuthorizedCode, credentialConfigurationIds = this.credentialConfigurationIds,
        authorizationDetails = this.authorizationDetails, scope = this.scope,
    )

    init {
        require(authorizationDetails == null || scope == null) { "Use authorizationDetails or scope, not both" }
        if (credentialConfigurationIds != null) {
            require(credentialConfigurationIds.isNotEmpty() && !credentialIssuer.isNullOrBlank()) {
                "Automatic token authorization requires credentialIssuer and selected configurations"
            }
            require(authorizationDetails == null && scope == null) {
                "Do not combine selected configurations with explicit authorization parameters"
            }
        }
    }
}

/** Released token result; its successful serialized shape is fixed. */
@Serializable
data class RequestTokenResult(
    val accessToken: String,
    val expiresIn: Long? = null,
    /** OAuth token_type. Preserve it when presenting the token (RFC 9449 Section 7.1). */
    val tokenType: String? = null,
) {
    override fun toString(): String =
        "RequestTokenResult(accessToken=<redacted>, expiresIn=$expiresIn, tokenType=$tokenType)"
}

/** Token plus the authorization server's granted configurations, datasets and scopes. */
@Serializable
data class RequestTokenDetailedResult(
    val accessToken: String,
    val expiresIn: Long? = null,
    val tokenType: String? = null,
    val authorizationDetails: List<AuthorizationDetail>? = null,
    val scope: String? = null,
) {
    internal fun toReleasedResult(): RequestTokenResult = RequestTokenResult(accessToken, expiresIn, tokenType)

    override fun toString(): String =
        "RequestTokenDetailedResult(accessToken=<redacted>, expiresIn=$expiresIn, tokenType=$tokenType)"
}

@Serializable
data class RequestNonceRequest(
    val credentialIssuer: Url,
)

@Serializable
data class RequestNonceResult(
    val nonce: String?,
) {
    override fun toString(): String = "RequestNonceResult(nonce=<redacted>)"
}

@Serializable
data class SignProofRequest(
    val issuerUrl: Url,
    /**
     * Credential configuration id whose `proof_types_supported` constrains the proof algorithm.
     * Resolved against the issuer metadata at [issuerUrl].
     */
    val credentialConfigurationId: String,
    val nonce: String? = null,
    /** Inline key to sign the proof with; takes precedence over [keyId]. */
    val key: DirectSerializedKey? = null,
    val keyId: String? = null,
    val did: String? = null,
    /**
     * OAuth `client_id` written as the proof `iss` claim for client-bound token requests.
     * Leave unset for anonymous pre-authorized access (OpenID4VCI 1.0 Appendix F.1).
     */
    val clientId: String? = null,
) {
    init {
        require(credentialConfigurationId.isNotBlank()) {
            "credentialConfigurationId must not be blank"
        }
        require(clientId == null || clientId.isNotBlank()) { "clientId cannot be blank" }
    }
}

@Serializable
data class SignProofResult(
    val proofJwt: String
)

/** Input for signing one proof for each accepted holder. */
@Serializable
data class SignProofsRequest(
    val holderBindings: List<CredentialHolderBinding> = listOf(CredentialHolderBinding()),
    val issuerUrl: Url,
    /**
     * Credential configuration id whose `proof_types_supported` constrains the proof algorithm.
     * Resolved against the issuer metadata at [issuerUrl].
     */
    val credentialConfigurationId: String,
    val nonce: String? = null,
    /** Inline key to sign the proof with; takes precedence over [keyId]. */
    val key: DirectSerializedKey? = null,
    val keyId: String? = null,
    val did: String? = null,
    /**
     * OAuth `client_id` written as the proof `iss` claim for client-bound token requests.
     * Leave unset for anonymous pre-authorized access (OpenID4VCI 1.0 Appendix F.1).
     */
    val clientId: String? = null,
) {
    init {
        require(credentialConfigurationId.isNotBlank()) {
            "credentialConfigurationId must not be blank"
        }
        require(clientId == null || clientId.isNotBlank()) { "clientId cannot be blank" }
    }
}

@Serializable(with = SignProofsResult.Companion::class)
data class SignProofsResult(val proofs: Proofs) {
    init {
        val jwt = proofs.jwt
        require(!jwt.isNullOrEmpty() && jwt.all { it.isNotBlank() } &&
                proofs.diVp == null && proofs.attestation == null) { "Expected a non-empty collection of JWT proofs" }
    }

    /** Keeps the released single-proof wire shape without adding a second internal proof representation. */
    companion object : KSerializer<SignProofsResult> {
        override val descriptor = SignProofsWire.serializer().descriptor

        override fun serialize(encoder: Encoder, value: SignProofsResult) {
            val single = value.proofs.jwt?.singleOrNull()
            val wire = if (single != null) SignProofsWire(proofJwt = single) else SignProofsWire(proofs = value.proofs)
            encoder.encodeSerializableValue(SignProofsWire.serializer(), wire)
        }

        override fun deserialize(decoder: Decoder): SignProofsResult {
            val wire = decoder.decodeSerializableValue(SignProofsWire.serializer())
            require((wire.proofJwt == null) != (wire.proofs == null)) { "Expected proofJwt or proofs, not both" }
            return SignProofsResult(wire.proofs ?: Proofs(jwt = listOf(requireNotNull(wire.proofJwt))))
        }
    }
}

@Serializable
@SerialName("SignProofsResult")
private data class SignProofsWire(
    @EncodeDefault(EncodeDefault.Mode.NEVER) val proofJwt: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val proofs: Proofs? = null,
)
