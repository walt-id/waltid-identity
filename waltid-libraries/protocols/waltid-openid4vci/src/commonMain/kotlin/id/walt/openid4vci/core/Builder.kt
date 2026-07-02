package id.walt.openid4vci.core

import id.walt.openid4vci.handlers.credential.SdJwtVcCredentialHandler
import id.walt.openid4vci.handlers.credential.MdocCredentialHandler
import id.walt.openid4vci.handlers.credential.W3cJwtVcCredentialHandler
import id.walt.openid4vci.handlers.granttypes.authorizationcode.AuthorizationCodeAuthorizationEndpoint
import id.walt.openid4vci.handlers.granttypes.authorizationcode.AuthorizationCodeTokenEndpoint
import id.walt.openid4vci.handlers.granttypes.preauthorizedcode.PreAuthorizedCodeTokenEndpoint
import id.walt.openid4vci.handlers.par.PushedAuthorizationRequestEndpointHandler
import id.walt.openid4vci.handlers.granttypes.refreshtoken.RefreshTokenTokenEndpoint
import id.walt.openid4vci.GrantType
import id.walt.openid4vci.CredentialFormat
import id.walt.openid4vci.clientauth.ClientAuthenticationEndpoint
import id.walt.openid4vci.clientauth.ClientAuthenticationMethods
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
    includeRefreshTokenDefaultHandlers: Boolean = true,
    includePushedAuthorizationDefaultHandlers: Boolean = true,
    includeCredentialDefaultHandlers: Boolean = true,
    includeClientAttestationDefaultMethod: Boolean = true,
): OAuth2Provider {
    val resolvedConfig = applyIssuerStateValidator(config)
    registerDefaultGrantTypeHandlers(
        config = resolvedConfig,
        includeAuthorizationCodeDefaultHandlers = includeAuthorizationCodeDefaultHandlers,
        includePreAuthorizedCodeDefaultHandlers = includePreAuthorizedCodeDefaultHandlers,
        includeRefreshTokenDefaultHandlers = includeRefreshTokenDefaultHandlers,
    )
    registerDefaultPushedAuthorizationHandlers(
        config = resolvedConfig,
        includePushedAuthorizationDefaultHandlers = includePushedAuthorizationDefaultHandlers,
    )
    registerDefaultCredentialHandlers(
        config = resolvedConfig,
        includeCredentialDefaultHandlers = includeCredentialDefaultHandlers,
    )
    val clientAuthConfig = registerDefaultClientAuthenticationMethods(
        config = resolvedConfig,
        includeClientAttestationDefaultMethod = includeClientAttestationDefaultMethod,
    )
    return DefaultOAuth2Provider(clientAuthConfig)
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
    includeRefreshTokenDefaultHandlers: Boolean,
) {
    if (includeAuthorizationCodeDefaultHandlers) {
        val authorizationCodeAuthorizationEndpointHandler = AuthorizationCodeAuthorizationEndpoint(
            codeRepository = config.authorizationCodeRepository,
        )
        config.authorizationEndpointHandlers.append(authorizationCodeAuthorizationEndpointHandler)

        val authorizationCodeTokenEndpointHandler = AuthorizationCodeTokenEndpoint(
            codeRepository = config.authorizationCodeRepository,
            accessTokenIssuer = config.accessTokenIssuer,
            refreshTokenRepository = config.refreshTokenRepository,
            refreshTokenIssuer = config.refreshTokenIssuer,
        )

        config.tokenEndpointHandlers.appendForGrant(
            grantType = GrantType.AuthorizationCode,
            handler = authorizationCodeTokenEndpointHandler,
        )
    }

    if (includePreAuthorizedCodeDefaultHandlers) {
        val preAuthorizedTokenHandler = PreAuthorizedCodeTokenEndpoint(
            codeRepository = config.preAuthorizedCodeRepository,
            accessTokenIssuer = config.accessTokenIssuer,
            refreshTokenRepository = config.refreshTokenRepository,
            refreshTokenIssuer = config.refreshTokenIssuer,
        )
        config.tokenEndpointHandlers.appendForGrant(
            grantType = GrantType.PreAuthorizedCode,
            handler = preAuthorizedTokenHandler,
        )
    }

    if (includeRefreshTokenDefaultHandlers) {
        val refreshTokenHandler = RefreshTokenTokenEndpoint(
            refreshTokenRepository = config.refreshTokenRepository,
            accessTokenIssuer = config.accessTokenIssuer,
            refreshTokenIssuer = config.refreshTokenIssuer,
            refreshTokenVerifier = config.refreshTokenVerifier,
        )
        config.tokenEndpointHandlers.appendForGrant(
            grantType = GrantType.RefreshToken,
            handler = refreshTokenHandler,
        )
    }
}

private fun registerDefaultPushedAuthorizationHandlers(
    config: OAuth2ProviderConfig,
    includePushedAuthorizationDefaultHandlers: Boolean,
) {
    val pushedAuthorizationConfig = config.pushedAuthorizationConfig
    check(pushedAuthorizationConfig != null || config.pushedAuthorizationEndpointHandlers.count() == 0) {
        "PAR endpoint handlers require pushedAuthorizationConfig"
    }

    if (pushedAuthorizationConfig == null) return

    if (includePushedAuthorizationDefaultHandlers) {
        config.pushedAuthorizationEndpointHandlers.append(
            PushedAuthorizationRequestEndpointHandler(
                parRepository = pushedAuthorizationConfig.repository,
                requestUriPrefix = pushedAuthorizationConfig.requestUriPrefix,
                requestLifetimeSeconds = pushedAuthorizationConfig.lifetimeSeconds,
            )
        )
    }

    check(config.pushedAuthorizationEndpointHandlers.count() > 0) {
        "PAR is configured but no pushed authorization endpoint handler is registered"
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

    val jwtVcJsonFormat = CredentialFormat.JWT_VC_JSON
    if (config.credentialEndpointHandlers.get(jwtVcJsonFormat) == null) {
        config.credentialEndpointHandlers.register(jwtVcJsonFormat, W3cJwtVcCredentialHandler())
    }

    val jwtVcFormat = CredentialFormat.JWT_VC
    if (config.credentialEndpointHandlers.get(jwtVcFormat) == null) {
        config.credentialEndpointHandlers.register(jwtVcFormat, W3cJwtVcCredentialHandler())
    }

    val mdocFormat = CredentialFormat.MSO_MDOC
    if (config.credentialEndpointHandlers.get(mdocFormat) == null) {
        config.credentialEndpointHandlers.register(mdocFormat, MdocCredentialHandler())
    }
}

private fun registerDefaultClientAuthenticationMethods(
    config: OAuth2ProviderConfig,
    includeClientAttestationDefaultMethod: Boolean,
): OAuth2ProviderConfig {
    val clientAttestationConfig = config.clientAttestationConfig
    if (!includeClientAttestationDefaultMethod || clientAttestationConfig == null) {
        return config
    }

    val serviceConfig = config.clientAuthenticationServiceConfig
    val hasAttestationMethod = serviceConfig.methods
        .any { it.name == ClientAuthenticationMethods.ATTEST_JWT_CLIENT_AUTH }
    val serviceConfigWithMethod =
        if (hasAttestationMethod) {
            serviceConfig
        } else {
            serviceConfig.withMethod(clientAttestationConfig.toAuthenticationMethod())
        }

    val supportedMethods = serviceConfigWithMethod.methods.map { it.name }.toSet()
    val serviceConfigWithEndpointDefaults = serviceConfigWithMethod.withDefaultMethodsByEndpoint(
        mapOf(
            ClientAuthenticationEndpoint.PUSHED_AUTHORIZATION to supportedMethods,
            ClientAuthenticationEndpoint.TOKEN to supportedMethods,
        ),
    )

    return config.copy(
        clientAuthenticationServiceConfig = serviceConfigWithEndpointDefaults,
    )
}
