package id.walt.openid4vci.core

import id.walt.openid4vci.handlers.credential.SdJwtVcCredentialHandler
import id.walt.openid4vci.handlers.granttypes.authorizationcode.AuthorizationCodeAuthorizationEndpoint
import id.walt.openid4vci.handlers.granttypes.authorizationcode.AuthorizationCodeTokenEndpoint
import id.walt.openid4vci.handlers.granttypes.preauthorizedcode.PreAuthorizedCodeTokenEndpoint
import id.walt.openid4vci.GrantType
import id.walt.openid4vci.CredentialFormat
import id.walt.openid4vci.validation.DefaultAuthorizationRequestValidator

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
 *   generation, etc.) without changing call sites, just extend the parameters of this function.
 * - Factory ecosystem: handler factories can be introduced later, allowing modules to register
 *   one or more handlers. Keeping callers behind `buildOAuth2Provider` makes that evolution backwards compatible.?!?
 *
 * For now, it stays intentionally simple, callers can supply extra handlers for customization,
 * while the function guarantees deduplication and returns the OAuthProvider used by the HTTP
 * layer. The defaults currently generate codes and tokens. Once proper strategies exist
 * we will lift that logic out into injectable services. Because extensions enter through this
 * single hook, we can later switch to handler lists for factory registration or split registries
 * (authorize/token endpoint).
 *
 * - `OAuth2ProviderConfig` remains the canonical place to stash shared collaborators (e.g. validators, token service,
 *   handler registries). Pass it around, and the provider reads from it each request.
 *   Without this indirection, callers would have to thread every dependency through their own
 *   builders, losing a central “state of the world.”
 * - `extra*Handlers` are for customization. We always register the defaults,
 *   then optionally append caller-supplied handlers flows. When we later adopt
 *   factory/strategy bundles, these arguments can become some kind of wrappers.
 * - `includeAuthorizationCodeDefaultHandlers` / `includePreAuthorizedCodeDefaultHandlers` give flags for
 *   composing providers (handy for tests) until the handler factory story evolves, needs revisited.
 * - Credential handlers are registered with defaults for SD-JWT VC formats unless disabled. In a future major
 *   release we may drop these defaults and require explicit handler registration for credential formats.
 */
fun buildOAuth2Provider(
    config: OAuth2ProviderConfig,
    includeAuthorizationCodeDefaultHandlers: Boolean = true,
    includePreAuthorizedCodeDefaultHandlers: Boolean = true,
    includeCredentialDefaultHandlers: Boolean = true,
): OAuth2Provider {
    val resolvedConfig = applyIssuerStateValidator(config)
    registerDefaultGrantTypeHandlers(
        config = resolvedConfig,
        includeAuthorizationCodeDefaultHandlers = includeAuthorizationCodeDefaultHandlers,
        includePreAuthorizedCodeDefaultHandlers = includePreAuthorizedCodeDefaultHandlers,
    )
    registerDefaultCredentialHandlers(
        config = resolvedConfig,
        includeCredentialDefaultHandlers = includeCredentialDefaultHandlers,
    )
    return DefaultOAuth2Provider(resolvedConfig)
}

private fun applyIssuerStateValidator(config: OAuth2ProviderConfig): OAuth2ProviderConfig =
    if (
        config.issuerStateValidator != null &&
        config.authorizationRequestValidator is DefaultAuthorizationRequestValidator
    ) {
        config.copy(
            authorizationRequestValidator = DefaultAuthorizationRequestValidator(
                issuerStateValidator = config.issuerStateValidator,
            ),
        )
    } else {
        config
    }

private fun registerDefaultGrantTypeHandlers(
    config: OAuth2ProviderConfig,
    includeAuthorizationCodeDefaultHandlers: Boolean,
    includePreAuthorizedCodeDefaultHandlers: Boolean,
) {
    if (includeAuthorizationCodeDefaultHandlers) {
        val authorizationCodeAuthorizationEndpointHandler = AuthorizationCodeAuthorizationEndpoint(
            codeRepository = config.authorizationCodeRepository,
        )
        config.authorizationEndpointHandlers.append(authorizationCodeAuthorizationEndpointHandler)

        val authorizationCodeTokenEndpointHandler = AuthorizationCodeTokenEndpoint(
            codeRepository = config.authorizationCodeRepository,
            tokenService = config.accessTokenService,
        )

        config.tokenEndpointHandlers.appendForGrant(
            grantType = GrantType.AuthorizationCode,
            handler = authorizationCodeTokenEndpointHandler,
        )
    }

    if (includePreAuthorizedCodeDefaultHandlers) {
        val preAuthorizedTokenHandler = PreAuthorizedCodeTokenEndpoint(
            codeRepository = config.preAuthorizedCodeRepository,
            tokenService = config.accessTokenService,
        )
        config.tokenEndpointHandlers.appendForGrant(
            grantType = GrantType.PreAuthorizedCode,
            handler = preAuthorizedTokenHandler,
        )
    }
}

private fun registerDefaultCredentialHandlers(
    config: OAuth2ProviderConfig,
    includeCredentialDefaultHandlers: Boolean,
) {
    if (!includeCredentialDefaultHandlers) return
    val sdJwtVcFormat = CredentialFormat.SD_JWT_VC
    if (config.credentialEndpointHandlers.get(sdJwtVcFormat) == null) {
        config.credentialEndpointHandlers.register(sdJwtVcFormat, SdJwtVcCredentialHandler())
    }
}
