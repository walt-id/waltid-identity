package id.walt.openid4vci.core

import id.walt.openid4vci.granttypehandlers.AuthorizationCodeAuthorizeHandler
import id.walt.openid4vci.granttypehandlers.AuthorizationCodeTokenHandler
import id.walt.openid4vci.granttypehandlers.PreAuthorizedCodeTokenHandler
import id.walt.openid4vci.tokens.TokenService

/**
 * Entry point for consumers to obtain the OAuth provider.
 *
 * This function wires the configuration's handler registry, adds the library's default
 * token and authorize endpoint handler, and returns a ready-to-use [OAuth2Provider].
 *
 * Why keep this indirection instead of calling [DefaultOAuth2Provider] directly?
 * - Central composition: as more handlers (for grant types)
 *   implement, they can be registered here so applications get defaults implementations.
 * - Strategy injection: future refactors can accept a strategy bundle (e.g. credential formats, signature algorithms, code/access token
 *   generation, etc) without changing call sites, just extend the parameters of this function.
 * - Factory ecosystem: handler factories can be introduced later, allowing modules to register
 *   one or more handlers. Keeping callers behind `buildProvider` makes that evolution backwards compatible.?!?
 *
 * For now it stays intentionally simple, callers can supply extra handlers for customization,
 * while the function guarantees deduplication and returns the OAuthProvider used by the HTTP
 * layer. The defaults currently generate codes and tokens. Once proper strategies exist
 * we will lift that logic out into injectable services. Because extensions enter through this
 * single hook, we can later switch to handler lists for factory registration or split registries
 * (authorize/token endpoint).
 *
 * - `Config` remains the canonical place to stash shared collaborators (e.g. validators, token service,
 *   handler registries). Pass it around, and the provider reads from it each request.
 *   Without this indirection, callers would have to thread every dependency through their own
 *   builders, losing a central “state of the world.”
 * - `extra*Handlers` are for customization. We always register the defaults,
 *   then optionally append caller-supplied handlers flows. When we later adopt
 *   factory/strategy bundles, these arguments can become somekind of wrappers.
 * - `includeAuthorizationCodeDefaultHandlers` / `includePreAuthorizedCodeDefaultHandlers` give flags for
 *   composing providers (handy for tests) until the handler factory story evolves, needs revisited.
 */
fun buildProvider(
    config: Config,
//    extraTokenEndpointHandlers: List<TokenEndpointHandler> = emptyList(),
//    extraAuthorizeHandlers: List<AuthorizeEndpointHandler> = emptyList(),
    includeAuthorizationCodeDefaultHandlers: Boolean = true,
    includePreAuthorizedCodeDefaultHandlers: Boolean = true,
): OAuth2Provider {
    registerDefaultHandlers(
        config = config,
        includeAuthorizationCodeDefaultHandlers = includeAuthorizationCodeDefaultHandlers,
        includePreAuthorizedCodeDefaultHandlers = includePreAuthorizedCodeDefaultHandlers,
    )
//    extraTokenEndpointHandlers.forEach { config.tokenEndpointHandlers.append(it) }
//    extraAuthorizeHandlers.forEach { config.authorizeEndpointHandlers.append(it) }
    return DefaultOAuth2Provider(config)
}

private fun registerDefaultHandlers(
    config: Config,
    includeAuthorizationCodeDefaultHandlers: Boolean,
    includePreAuthorizedCodeDefaultHandlers: Boolean,
) {
    if (includeAuthorizationCodeDefaultHandlers) {
        val authorizeEndpointHandler = AuthorizationCodeAuthorizeHandler(
            codeRepository = config.authorizationCodeRepository,
        )
        config.authorizeEndpointHandlers.append(authorizeEndpointHandler)

        val authorizeTokenHandler = AuthorizationCodeTokenHandler(
            codeRepository = config.authorizationCodeRepository,
            tokenService = TokenService(),
        )
        config.tokenEndpointHandlers.append(authorizeTokenHandler)
    }

    if (includePreAuthorizedCodeDefaultHandlers) {
        val preAuthorizedTokenHandler = PreAuthorizedCodeTokenHandler(
            codeRepository = config.preAuthorizedCodeRepository,
            tokenService = TokenService(),
        )
        config.tokenEndpointHandlers.append(preAuthorizedTokenHandler)
    }
}
