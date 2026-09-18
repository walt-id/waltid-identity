package id.waltid.openid4vp.wallet.request

import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.verifier.openid.models.authorization.ClientMetadata

/**
 * Client identity facts established while resolving an Authorization Request.
 *
 * Later stages (encryption, format negotiation, error responses) consume these facts
 * instead of reconstructing trust from wire fields or the coarse Plain/signed split.
 */
data class AuthenticatedClientFacts(
    /** Registered or authenticated in-band metadata. Null when none was established. */
    val effectiveClientMetadata: ClientMetadata? = null,
    /** True when the response destination was bound by `redirect_uri:` or registered `redirect_uris`. */
    val responseDestinationAuthenticated: Boolean = false,
    /** Bound response destination, when [responseDestinationAuthenticated] is true. */
    val boundResponseDestination: String? = null,
) {
    companion object {
        val None = AuthenticatedClientFacts()

        fun redirectUriBound(request: AuthorizationRequest): AuthenticatedClientFacts {
            val destination = request.responseUri ?: request.redirectUri
            return AuthenticatedClientFacts(
                effectiveClientMetadata = request.clientMetadata,
                responseDestinationAuthenticated = destination != null,
                boundResponseDestination = destination,
            )
        }

        fun registered(metadata: ClientMetadata, request: AuthorizationRequest): AuthenticatedClientFacts {
            val destination = request.responseUri ?: request.redirectUri
            return AuthenticatedClientFacts(
                effectiveClientMetadata = metadata,
                responseDestinationAuthenticated = destination != null,
                boundResponseDestination = destination,
            )
        }

        fun signed(metadata: ClientMetadata?, request: AuthorizationRequest): AuthenticatedClientFacts {
            val destination = request.responseUri ?: request.redirectUri
            return AuthenticatedClientFacts(
                effectiveClientMetadata = metadata ?: request.clientMetadata,
                responseDestinationAuthenticated = true,
                boundResponseDestination = destination,
            )
        }

        fun unsignedRequestObject(
            metadata: ClientMetadata?,
            request: AuthorizationRequest,
        ): AuthenticatedClientFacts {
            val destination = request.responseUri ?: request.redirectUri
            return AuthenticatedClientFacts(
                effectiveClientMetadata = metadata ?: request.clientMetadata,
                responseDestinationAuthenticated = destination != null,
                boundResponseDestination = destination,
            )
        }
    }
}

sealed class ResolvedAuthorizationRequest {
    abstract val authorizationRequest: AuthorizationRequest
    abstract val client: AuthenticatedClientFacts

    /** Effective metadata for encryption and format negotiation. Never re-emitted as in-band `client_metadata`. */
    val effectiveClientMetadata: ClientMetadata?
        get() = client.effectiveClientMetadata ?: authorizationRequest.clientMetadata

    data class Plain(
        override val authorizationRequest: AuthorizationRequest,
        override val client: AuthenticatedClientFacts = AuthenticatedClientFacts.None,
    ) : ResolvedAuthorizationRequest()

    data class UnsignedRequestObject(
        override val authorizationRequest: AuthorizationRequest,
        val requestObject: String,
        override val client: AuthenticatedClientFacts = AuthenticatedClientFacts.unsignedRequestObject(
            metadata = null,
            request = authorizationRequest,
        ),
    ) : ResolvedAuthorizationRequest()

    data class AuthenticatedRequestObject(
        override val authorizationRequest: AuthorizationRequest,
        val requestObject: String,
        /** Authentication facts established by [AuthorizationRequestResolver]. */
        val authentication: RequestObjectAuthentication,
        override val client: AuthenticatedClientFacts = AuthenticatedClientFacts.signed(
            metadata = null,
            request = authorizationRequest,
        ),
    ) : ResolvedAuthorizationRequest()
}
