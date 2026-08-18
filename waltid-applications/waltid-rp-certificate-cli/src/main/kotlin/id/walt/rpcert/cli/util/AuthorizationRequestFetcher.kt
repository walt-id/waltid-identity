package id.walt.rpcert.cli.util

import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.webdatafetching.WebDataFetcher
import id.waltid.openid4vp.wallet.request.AuthorizationRequestResolver
import io.ktor.http.Url

/**
 * Resolves an `openid4vp://authorize?...` Authorization Request URL into an [AuthorizationRequest],
 * fetching `request_uri` over HTTP (GET/POST per `request_uri_method`) when the request is not inline.
 * Delegates to the walt.id wallet SDK's [AuthorizationRequestResolver], which also authenticates
 * signed request objects against their `client_id` prefix scheme.
 */
object AuthorizationRequestFetcher {

    suspend fun resolve(authorizationRequestUrl: String): AuthorizationRequest {
        val fetcher = WebDataFetcher(id = "waltid-rpcert-cli")
        val resolved = AuthorizationRequestResolver.resolve(
            requestUrl = Url(authorizationRequestUrl),
            unsignedRequestObjectPolicy = AuthorizationRequestResolver.UnsignedRequestObjectPolicy.REQUIRE_SIGNED,
        ) { requestUri, requestUriMethod ->
            AuthorizationRequestResolver.fetchRequestUriWithWebDataFetcher(
                webResolveAuthReq = fetcher,
                requestUri = requestUri,
                requestUriMethod = requestUriMethod,
            )
        }
        return resolved.authorizationRequest
    }
}
