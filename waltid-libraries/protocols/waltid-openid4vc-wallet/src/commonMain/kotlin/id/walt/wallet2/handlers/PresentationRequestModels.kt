package id.walt.wallet2.handlers

import id.walt.crypto.keys.DirectSerializedKey
import id.walt.dcql.models.DcqlQuery
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.wallet2.data.StoredCredential
import io.ktor.http.Url
import kotlinx.serialization.Serializable

/**
 * Common contract for request types that carry an untrusted OpenID4VP request URL.
 * Resolution and Request Object authentication always happen inside the wallet.
 */
interface VpRequestSource {
    val requestUrl: Url
}

/**
 * Input for the full presentation flow.
 *
 * The request is resolved and authenticated by the wallet before credentials are selected.
 */
@Serializable
data class PresentCredentialRequest(
    /**
     * The OpenID4VP authorization request as a URL.
     * May be an openid4vp:// URL with inline parameters, or an https:// URL
     * whose request_uri parameter points to the actual request object.
     */
    override val requestUrl: Url,

    /** Inline signing key; takes precedence over [keyId]. */
    val key: DirectSerializedKey? = null,
    val keyId: String? = null,
    val did: String? = null,
    val runPolicies: Boolean? = null
) : VpRequestSource

@Serializable
data class PresentCredentialIsolatedRequest(
    override val requestUrl: Url,
    val credentials: List<StoredCredential>,
    /** Inline signing key; takes precedence over [keyId]. */
    val key: DirectSerializedKey? = null,
    val keyId: String? = null,
    val did: String? = null
) : VpRequestSource

@Serializable
data class ResolveVpRequestRequest(
    override val requestUrl: Url,
) : VpRequestSource

@Serializable
data class ResolveVpRequestResult(
    /** Complete authenticated request to use for subsequent manual presentation steps. */
    val authorizationRequest: AuthorizationRequest,
    val nonce: String?,
    val clientId: String?,
    val responseUri: Url?,
    val hasRequestUri: Boolean,
    /** The DCQL query from the authorization request, ready to pass to match-credentials or present. */
    val dcqlQuery: DcqlQuery?,
)

@Serializable
data class MatchCredentialsRequest(
    val dcqlQuery: DcqlQuery,
    val credentials: List<StoredCredential>
)

/**
 * Request for matching a DCQL query against the wallet's own credential stores.
 * No credentials need to be supplied inline — they are loaded from the wallet.
 */
@Serializable
data class MatchCredentialsFromStoreRequest(
    val dcqlQuery: DcqlQuery
)

@Serializable
data class MatchCredentialsResult(
    /** DCQL query IDs for which at least one credential matched. */
    val matchedQueryIds: List<String>,
    /** Total number of credential matches across all query IDs. */
    val matchCount: Int,
    /** For each matched query ID, the wallet-assigned IDs of matching credentials. */
    val matchedCredentialIds: Map<String, List<String>>
)
