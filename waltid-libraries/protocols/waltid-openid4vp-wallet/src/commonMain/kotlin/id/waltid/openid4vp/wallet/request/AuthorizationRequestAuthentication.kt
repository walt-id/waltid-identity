package id.waltid.openid4vp.wallet.request

import id.walt.openid4vp.clientidprefix.prefixes.ClientId

/** Authentication result established while resolving an OpenID4VP Request Object. */
data class RequestObjectAuthentication(
    /** Parsed client identifier, including the pre-registered representation when applicable. */
    val clientId: ClientId,
    /** JOSE algorithm from the authenticated Request Object. */
    val algorithm: String,
    /** JOSE key identifier from the authenticated Request Object, when supplied. */
    val keyId: String?,
)
