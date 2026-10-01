package id.walt.issuer2.config

import id.walt.openid4vci.core.OpenId4VciEndpointPaths

/** Protocol paths relative to `/openid4vci`, also reserved for self-hosted VCT URLs. */
internal object Issuer2EndpointPaths {
    const val JWKS = "jwks"
    const val CREDENTIAL_OFFER = "credential-offer"
    const val PAR = "par"
    const val AUTHORIZE = "authorize"
    const val EXTERNAL_LOGIN = "external_login/{internalAuthReq}"
    const val EXTERNAL_CALLBACK = "external/oauth/callback"
    const val TOKEN = "token"
    const val NONCE = "nonce"
    const val CREDENTIAL = "credential"
    const val NOTIFICATION = OpenId4VciEndpointPaths.NOTIFICATION

    // Reserve the entire protocol namespace, including groups a deployment does not expose.
    val reservedVctNames: Set<String> = setOf(
        JWKS, CREDENTIAL_OFFER, PAR, AUTHORIZE, EXTERNAL_LOGIN, EXTERNAL_CALLBACK, TOKEN, NONCE, CREDENTIAL, NOTIFICATION,
    ).map { it.substringBefore('/') }.toSet()
}
