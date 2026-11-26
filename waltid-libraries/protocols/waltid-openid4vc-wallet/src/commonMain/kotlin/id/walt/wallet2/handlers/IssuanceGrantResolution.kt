package id.walt.wallet2.handlers

import id.walt.openid4vci.GrantType
import id.walt.openid4vci.metadata.issuer.CredentialIssuerMetadata
import id.walt.openid4vci.offers.CredentialOffer
import id.waltid.openid4vci.wallet.metadata.IssuerMetadataResolver
import id.waltid.openid4vci.wallet.metadata.ResolvedAuthorizationServerMetadata

internal data class ResolvedIssuanceGrant(
    val grant: WalletIssuanceGrant,
    val server: ResolvedAuthorizationServerMetadata,
)

/** Bind the chosen grant to one declared authorization server before presenting or accepting the offer. */
internal suspend fun IssuerMetadataResolver.resolveIssuanceGrant(
    offer: CredentialOffer?,
    issuer: CredentialIssuerMetadata,
    preferred: WalletIssuanceGrant = WalletIssuanceGrant.AUTHORIZATION_CODE,
    required: WalletIssuanceGrant? = null,
): ResolvedIssuanceGrant {
    val offered = offer?.getGrantTypes().orEmpty()
    val candidates = buildList {
        if (offered.isEmpty() || GrantType.AuthorizationCode in offered) add(WalletIssuanceGrant.AUTHORIZATION_CODE)
        if (GrantType.PreAuthorizedCode in offered) add(WalletIssuanceGrant.PRE_AUTHORIZED_CODE)
    }.sortedBy { it != preferred }
    val servers = mutableMapOf<String, ResolvedAuthorizationServerMetadata>()
    for (grant in candidates) {
        if (required != null && required != grant) continue
        val browser = grant == WalletIssuanceGrant.AUTHORIZATION_CODE
        val wireGrant = if (browser) GrantType.AuthorizationCode.value else GrantType.PreAuthorizedCode.value
        for (server in grantServers(offer, grant, issuer)) {
            val resolution = servers.getOrPut(server) { resolveDeclaredServer(server) }
            val metadata = resolution.metadata
            if (metadata.tokenEndpoint.isNullOrBlank() || (browser && metadata.authorizationEndpoint.isNullOrBlank())) continue
            // Explicit offers supply the pre-authorized code even when the server omits its optional grant list.
            if (metadata.grantTypesSupported?.contains(wireGrant) == false) continue
            return ResolvedIssuanceGrant(grant, resolution)
        }
    }
    error("No usable issuance grant is supported by the advertised authorization servers")
}

internal fun grantServers(
    offer: CredentialOffer?, grant: WalletIssuanceGrant, issuer: CredentialIssuerMetadata,
): List<String> {
    val declared = issuer.authorizationServerIssuers()
    val server = when (grant) {
        WalletIssuanceGrant.AUTHORIZATION_CODE -> offer?.grants?.authorizationCode?.authorizationServer
        WalletIssuanceGrant.PRE_AUTHORIZED_CODE -> offer?.grants?.preAuthorizedCode?.authorizationServer
    } ?: return declared
    require(declared.size > 1 && server in declared) {
        "Offer authorization_server must identify one of multiple declared authorization servers"
    }
    return listOf(server)
}

internal suspend fun IssuerMetadataResolver.resolveDeclaredServer(server: String): ResolvedAuthorizationServerMetadata =
    resolveAuthorizationServerMetadataDetailed(server).also {
        require(it.metadata.issuer == server) { "Authorization server metadata issuer does not match the selected server" }
    }

/** Identifies the declared server that owns a token endpoint supplied to the stateless API. */
internal suspend fun IssuerMetadataResolver.resolveTokenServer(
    issuer: CredentialIssuerMetadata,
    tokenEndpoint: String,
): ResolvedAuthorizationServerMetadata {
    for (server in issuer.authorizationServerIssuers()) {
        val resolution = resolveDeclaredServer(server)
        if (resolution.metadata.tokenEndpoint == tokenEndpoint) return resolution
    }
    error("Token endpoint does not match any declared authorization server")
}
