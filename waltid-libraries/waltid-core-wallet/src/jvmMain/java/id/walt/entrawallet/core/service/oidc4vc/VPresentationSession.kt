@file:OptIn(ExperimentalTime::class)

package id.walt.entrawallet.core.service.oidc4vc

import id.walt.crypto.keys.Key
import id.walt.entrawallet.core.service.exchange.CredentialDataResult
import id.walt.oid4vc.providers.SIOPSession
import id.walt.oid4vc.requests.AuthorizationRequest
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

data class VPresentationSession(
    override val id: String,
    override val authorizationRequest: AuthorizationRequest?,
    override val expirationTimestamp: Instant,
    val selectedCredentials: Set<CredentialDataResult>,
    val key: Key
) : SIOPSession(id, authorizationRequest, expirationTimestamp)
