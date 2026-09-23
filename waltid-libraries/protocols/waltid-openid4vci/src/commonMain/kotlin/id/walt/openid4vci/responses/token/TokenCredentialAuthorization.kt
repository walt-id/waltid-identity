package id.walt.openid4vci.responses.token

import id.walt.openid4vci.requests.authorization.AuthorizationDetail
import id.walt.openid4vci.requests.authorization.OPENID_CREDENTIAL_AUTHORIZATION_DETAIL_TYPE
import id.walt.openid4vci.requests.credential.CredentialAuthorization
import id.walt.openid4vci.requests.credential.toAuthorizationDetails
import id.walt.openid4vci.requests.token.AccessTokenRequest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

/** Immutable permissions for one access token, separate from its refresh grant. */
data class TokenCredentialAuthorization(
    val authorizationDetails: List<AuthorizationDetail>,
    val includeInResponse: Boolean,
) {
    val credentialIdentifiers: List<String>
        get() = authorizationDetails.flatMap { it.credentialIdentifiers.orEmpty() }

    fun tokenClaims(): Map<String, JsonElement> =
        mapOf("authorization_details" to Json.encodeToJsonElement(authorizationDetails))
}

class InvalidTokenCredentialAuthorization(val request: AccessTokenRequest, message: String) : IllegalArgumentException(message)

/** Apply a credential grant without treating a missing grant as an unrestricted grant. */
fun Collection<CredentialAuthorization>.withinAuthorizationDetails(
    details: List<AuthorizationDetail>,
): List<CredentialAuthorization> = filter { candidate ->
    details.any { detail ->
        detail.type == OPENID_CREDENTIAL_AUTHORIZATION_DETAIL_TYPE &&
            detail.credentialConfigurationId == candidate.credentialConfigurationId &&
            (detail.credentialIdentifiers == null || candidate.credentialIdentifier in detail.credentialIdentifiers)
    }
}

/** Only call after the provider has verified the access token, including its validity and binding. */
fun Collection<CredentialAuthorization>.authorizedByVerifiedToken(
    claims: JsonObject,
    establishedSelection: List<String>?,
    legacyScopeConfigurationIds: Set<String>,
): List<CredentialAuthorization> {
    val selected = filter { establishedSelection == null || it.credentialIdentifier in establishedSelection }
    val details = claims["authorization_details"]
    if (details != null) return selected.withinAuthorizationDetails(Json.decodeFromJsonElement(details))
    // Pre-batch pre-authorized tokens could omit scopes. Their correlated saved session
    // contained exactly one request. Empty scope is never a wildcard for a multi-offer.
    return if (legacyScopeConfigurationIds.isNotEmpty()) selected.filter { it.credentialConfigurationId in legacyScopeConfigurationIds }
    else selected.takeIf { size == 1 }.orEmpty()
}

/** The embedding issuer resolves scope names and supplies only configurations backed by the validated grant. */
fun resolveTokenCredentialAuthorization(
    request: AccessTokenRequest,
    candidates: List<CredentialAuthorization>,
    grantConfigurationIds: Set<String>,
    tokenScopeConfigurationIds: Set<String>,
    establishedSelection: List<String>?,
    refreshGrant: List<AuthorizationDetail>?,
    includeInResponse: Boolean,
): TokenCredentialAuthorization {
    var permitted = candidates.filter { it.credentialConfigurationId in grantConfigurationIds }
    establishedSelection?.let { selected -> permitted = permitted.filter { it.credentialIdentifier in selected } }
    refreshGrant?.let { permitted = permitted.withinAuthorizationDetails(it) }
    val requestedConfigurations = request.authorizationDetails
        .filter { it.type == OPENID_CREDENTIAL_AUTHORIZATION_DETAIL_TYPE }
        .map { it.credentialConfigurationId }.toSet()
    val selection = requestedConfigurations.ifEmpty { tokenScopeConfigurationIds }
    val explicitScope = "scope" in request.requestForm || request.requestedScopes.isNotEmpty()
    // Inherited OAuth scopes may cover more than the saved credential grant.
    // Only an explicit selector outside that grant is an invalid request.
    if ((requestedConfigurations.isNotEmpty() || explicitScope) &&
        selection.any { requested -> permitted.none { it.credentialConfigurationId == requested } }) {
        throw InvalidTokenCredentialAuthorization(request, "Requested credentials exceed the authorized grant")
    }
    // No mapped credential scopes is not the same as no selector: an explicit
    // scope=openid (or another non-credential scope) must not restore the grant.
    val effective = if (selection.isEmpty() && !explicitScope) permitted
        else permitted.filter { it.credentialConfigurationId in selection }
    if (effective.isEmpty()) throw InvalidTokenCredentialAuthorization(request, "Token request selects no credentials")
    return TokenCredentialAuthorization(effective.toAuthorizationDetails(), includeInResponse)
}
