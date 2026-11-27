package id.walt.wallet2.handlers

import id.walt.openid4vci.GrantType
import id.walt.openid4vci.clientauth.ClientAuthenticationMethods
import id.walt.openid4vci.metadata.oauth.AuthorizationServerMetadata
import id.walt.openid4vci.offers.CredentialOffer
import id.walt.wallet2.handlers.WalletIssuanceSessionService.ActiveSession
import id.waltid.openid4vci.wallet.metadata.CredentialIssuerMetadataTrustResolver
import id.waltid.openid4vci.wallet.metadata.IssuerMetadataResolver
import id.waltid.openid4vci.wallet.metadata.LocalizedMetadata
import id.waltid.openid4vci.wallet.metadata.OfferedCredentialResolver
import id.waltid.openid4vci.wallet.metadata.ResolvedCredentialIssuerMetadata
import id.waltid.openid4vci.wallet.offer.CredentialOfferParser
import id.waltid.openid4vci.wallet.offer.CredentialOfferResolver
import io.ktor.client.HttpClient
import io.ktor.http.Headers
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement

internal class ResolvedSessionOffer(
    val offer: CredentialOffer?,
    val issuerMetadata: ResolvedCredentialIssuerMetadata,
    val authorizationServerMetadata: AuthorizationServerMetadata,
    val offeredCredentials: List<OfferedCredentialResolver.ResolvedCredentialOffer>,
    val grant: WalletIssuanceGrant,
    val discoveryHeaders: Headers = Headers.Empty,
)

internal suspend fun WalletIssuanceOfferRequest.resolveSessionOffer(
    httpClient: HttpClient,
    metadataTrustResolver: CredentialIssuerMetadataTrustResolver?,
    preferredLocales: List<String>,
): ResolvedSessionOffer {
    val offer = if (credentialIssuer != null) null else if (offerJson != null) {
        val inline = offerJsonFormat.decodeFromJsonElement<CredentialOffer>(offerJson)
        CredentialOfferResolver(httpClient).resolveCredentialOffer(inline, null)
    } else {
        val parsed = CredentialOfferParser.parseCredentialOfferUrl(getEffectiveOfferString())
        CredentialOfferResolver(httpClient).resolveCredentialOffer(parsed.credentialOffer, parsed.credentialOfferUri)
    }
    val resolver = IssuerMetadataResolver(httpClient, metadataTrustResolver)
    // Operational metadata is always resolved from issuer-derived well-known endpoints.
    // Unrecognized offer parameters are caller-controlled and must not select network
    // endpoints for issuance.
    val issuer = credentialIssuer ?: requireNotNull(offer).credentialIssuer
    val issuerMetadata = resolver.resolveCredentialIssuerMetadata(
        credentialIssuerUrl = issuer,
        preferredLocales = preferredLocales,
    )
    require(issuerMetadata.metadata.credentialIssuer == issuer) {
        "Credential issuer metadata identifier does not match the offer"
    }
    val (grant, asResolution) = resolver.resolveIssuanceGrant(offer, issuerMetadata.metadata)
    val authorizationServerMetadata = asResolution.metadata

    val offered = if (offer != null) OfferedCredentialResolver.resolveOfferedCredentials(offer, issuerMetadata.metadata)
        else requireNotNull(credentialConfigurationIds).map {
            OfferedCredentialResolver.ResolvedCredentialOffer(it, issuerMetadata.metadata.credentialConfigurationsSupported.getValue(it))
        }
    require(offered.isNotEmpty()) { "Credential offer resolved no supported credentials" }
    return ResolvedSessionOffer(offer, issuerMetadata, authorizationServerMetadata, offered, grant, asResolution.headers)
}

internal fun PersistedActiveSession.restoreOffer(json: Json): ResolvedSessionOffer {
    val offer = this.offer?.let { json.decodeFromString<CredentialOffer>(it) }
    // The store is an integrity-protected persistence boundary. Restore the resolution snapshot
    // established when the session started, then revalidate its protocol bindings below.
    val issuerMetadata = json.decodeFromString<ResolvedCredentialIssuerMetadata>(this.issuerMetadata)
    val authorizationServerMetadata =
        json.decodeFromString<AuthorizationServerMetadata>(this.authorizationServerMetadata)
    val offeredCredentials = if (offer != null) OfferedCredentialResolver.resolveOfferedCredentials(offer, issuerMetadata.metadata)
        else public.offer.credentials.map { OfferedCredentialResolver.ResolvedCredentialOffer(it.configurationId,
            issuerMetadata.metadata.credentialConfigurationsSupported.getValue(it.configurationId)) }
    return ResolvedSessionOffer(offer, issuerMetadata, authorizationServerMetadata, offeredCredentials, public.offer.grant).also { it.validateRestoredSession(public) }
}

internal fun ResolvedSessionOffer.toPreview(preferredLocales: List<String>): WalletIssuanceOfferPreview {
    val issuerDisplay = LocalizedMetadata.select(issuerMetadata.metadata.display, preferredLocales) { it.locale }
    val txCode = offer?.grants?.preAuthorizedCode?.txCode?.takeIf { grant == WalletIssuanceGrant.PRE_AUTHORIZED_CODE }
    return WalletIssuanceOfferPreview(
        grant = grant,
        issuer = WalletIssuanceIssuerPreview(
            identifier = issuerMetadata.metadata.credentialIssuer,
            name = issuerDisplay?.name,
            locale = issuerDisplay?.locale,
            logoUri = issuerDisplay?.logo?.uri,
            logoAltText = issuerDisplay?.logo?.altText,
            metadataProvenance = issuerMetadata.toPreviewProvenance(),
        ),
        credentials = offeredCredentials.map { offered ->
            val display = LocalizedMetadata.select(
                offered.configuration.credentialMetadata?.display,
                preferredLocales,
            ) { it.locale }
            WalletIssuanceCredentialPreview(
                configurationId = offered.credentialConfigurationId,
                format = offered.configuration.format.value,
                name = display?.name,
                descriptionText = display?.description,
                logoUri = display?.logo?.uri,
                logoAltText = display?.logo?.altText,
                backgroundColor = display?.backgroundColor,
                backgroundImageUri = display?.backgroundImage?.uri,
                textColor = display?.textColor,
                vct = offered.configuration.vct,
                doctype = offered.configuration.doctype,
            )
        },
        transactionCode = txCode?.let {
            WalletIssuanceTransactionCode(it.inputMode, it.length, it.description)
        },
    )
}

internal fun ActiveSession.toOfferReview(hasAttestation: Boolean) = WalletIssuanceOfferReview(
    toBatchSession(), resolved.authorizationServerMetadata.issuer, clientIdentification(hasAttestation),
)

internal fun ActiveSession.clientIdentification(hasAttestation: Boolean): IssuanceClientIdentification {
    val metadata = resolved.authorizationServerMetadata
    val identified = resolved.grant == WalletIssuanceGrant.AUTHORIZATION_CODE ||
        metadata.preAuthorizedGrantAnonymousAccessSupported != true || request.tokenRequestHeaders.isNotEmpty() ||
        (hasAttestation && metadata.tokenEndpointAuthMethodsSupported
            ?.contains(ClientAuthenticationMethods.ATTEST_JWT_CLIENT_AUTH) == true)
    return if (identified) IssuanceClientIdentification.CLIENT_ID_REQUIRED else IssuanceClientIdentification.ANONYMOUS_ALLOWED
}

internal fun ActiveSession.toBatchSession(): WalletIssuanceBatchSession = WalletIssuanceBatchSession(
    public.id, public.offer, resolved.issuerMetadata.metadata.batchCredentialIssuance?.batchSize,
    credentialDisplayMetadata = resolved.offeredCredentials.mapNotNull { credential ->
        storedCredentialDisplayMetadata(resolved.issuerMetadata.metadata, credential.credentialConfigurationId)
            ?.let { credential.credentialConfigurationId to it.toString() }
    }.toMap(),
)

private val offerJsonFormat = Json { ignoreUnknownKeys = true }

private fun ResolvedSessionOffer.validateRestoredSession(session: WalletIssuanceSession) {
    require(issuerMetadata.metadata.credentialIssuer == session.offer.issuer.identifier) {
        "Stored issuance session issuer binding is invalid"
    }
    require(offer == null || issuerMetadata.metadata.credentialIssuer == offer.credentialIssuer) {
        "Stored credential issuer metadata binding is invalid"
    }
    require(authorizationServerMetadata.issuer in issuerMetadata.metadata.authorizationServerIssuers()) {
        "Stored authorization server binding is invalid"
    }
    require(
        offeredCredentials.map { it.credentialConfigurationId } ==
                session.offer.credentials.map { it.configurationId }
    ) { "Stored offered credential binding is invalid" }
    // The released record already stores the selected grant and its resolved server.
    val offered = offer?.getGrantTypes().orEmpty()
    val supported = if (grant == WalletIssuanceGrant.AUTHORIZATION_CODE) GrantType.AuthorizationCode else GrantType.PreAuthorizedCode
    require(if (offered.isEmpty()) grant == WalletIssuanceGrant.AUTHORIZATION_CODE else supported in offered) {
        "Stored issuance grant binding is invalid"
    }
    require(authorizationServerMetadata.issuer in grantServers(offer, grant, issuerMetadata.metadata)) {
        "Stored grant authorization server binding is invalid"
    }
}

private fun ResolvedCredentialIssuerMetadata.toPreviewProvenance(): WalletIssuanceMetadataProvenance = when (this) {
    is ResolvedCredentialIssuerMetadata.Unsigned -> WalletIssuanceMetadataProvenance.Unsigned
    is ResolvedCredentialIssuerMetadata.Signed -> WalletIssuanceMetadataProvenance.Signed(
        compactJwt = compactJwt,
        algorithm = signer.algorithm,
        keyId = signer.keyId,
        trustType = signer.trustType,
    )
}
