@file:OptIn(ExperimentalTime::class)

package id.walt.webwallet.service.oidc4vc

import id.walt.oid4vc.providers.SIOPSession
import id.walt.oid4vc.requests.AuthorizationRequest
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

data class VPresentationSession(
    override val id: String,
    override val authorizationRequest: AuthorizationRequest?,
    override val expirationTimestamp: Instant,
    val selectedCredentialIds: Set<String>,
) : SIOPSession(id, authorizationRequest, expirationTimestamp)
