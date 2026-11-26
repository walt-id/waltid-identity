package id.walt.wallet2.handlers

import id.walt.openid4vci.metadata.issuer.CredentialIssuerMetadata

internal fun ReceiveCredentialRequest.toResolveOfferRequest(): ResolveOfferRequest =
    ResolveOfferRequest(offerUrl = offerUrl, offerJson = offerJson)

internal fun ReceiveCredentialFromPreviewRequest.toReceiveCredentialRequest(
    source: ResolveOfferRequest,
): ReceiveCredentialRequest = ReceiveCredentialRequest(
    credentials = credentials,
    offerUrl = source.offerUrl,
    offerJson = source.offerJson,
    key = key,
    keyId = keyId,
    did = did,
    txCode = txCode,
    clientId = clientId,
    redirectUri = redirectUri,
    tokenRequestHeaders = tokenRequestHeaders,
)

/**
 * `locations` to put in each `openid_credential` authorization detail, or `null` when it may be
 * omitted.
 *
 * OID4VCI 1.0 Section 5.1.1 makes it mandatory once the Credential Issuer advertises
 * `authorization_servers`, because a single authorization server can serve several issuers and the
 * grant would otherwise be ambiguous. The value is the Credential Issuer Identifier itself.
 */
internal fun CredentialIssuerMetadata.authorizationDetailLocations(): List<String>? =
    authorizationServers?.takeIf { it.isNotEmpty() }?.let { listOf(credentialIssuer) }

internal fun CredentialIssuerMetadata.requireCredentialScope(credentialConfigurationId: String): String =
    requireNotNull(credentialConfigurationsSupported[credentialConfigurationId]?.scope) {
        "Credential configuration '$credentialConfigurationId' publishes no scope, so this " +
                "credential cannot be requested by scope (OID4VCI 1.0 Section 5.1.2)"
    }
