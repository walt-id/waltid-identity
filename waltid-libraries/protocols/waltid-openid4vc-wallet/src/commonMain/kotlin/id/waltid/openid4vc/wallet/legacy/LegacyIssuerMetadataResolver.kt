package id.waltid.openid4vc.wallet.legacy

import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.oid4vc.data.CredentialFormat as LegacyCredentialFormat
import id.walt.oid4vc.data.CredentialSupported
import id.walt.oid4vc.data.CredentialDefinition as LegacyCredentialDefinition
import id.walt.oid4vc.data.CredSignAlgValues
import id.walt.oid4vc.data.DisplayProperties
import id.walt.oid4vc.data.GrantType as LegacyGrantType
import id.walt.oid4vc.data.LogoProperties
import id.walt.oid4vc.data.OpenIDProviderMetadata
import id.walt.oid4vc.data.ProofType as LegacyProofType
import id.walt.oid4vc.data.ProofTypeMetadata as LegacyProofTypeMetadata
import id.walt.oid4vc.data.ResponseMode as LegacyResponseMode
import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import id.walt.openid4vci.metadata.issuer.CredentialIssuerMetadata
import id.walt.openid4vci.metadata.issuer.CredentialDisplay
import id.walt.openid4vci.metadata.issuer.IssuerDisplay
import id.walt.openid4vci.metadata.issuer.SigningAlgId
import id.walt.openid4vci.metadata.oauth.AuthorizationServerMetadata
import id.waltid.openid4vci.wallet.metadata.CredentialIssuerMetadataTrustResolver
import id.waltid.openid4vci.wallet.metadata.IssuerMetadataResolver
import id.waltid.openid4vci.wallet.metadata.MetadataSigner
import id.waltid.openid4vci.wallet.metadata.MetadataSignerTrustType
import io.ktor.client.HttpClient
import kotlinx.serialization.json.jsonPrimitive

/**
 * A verification key trusted for one credential issuer's signed metadata.
 * The key is supplied by the embedding service, never by the metadata JWT.
 */
data class TrustedIssuerMetadataSigner(
    val issuer: String,
    val publicJwk: String,
    val keyId: String? = null,
    val algorithm: String? = null,
    val trustType: MetadataSignerTrustType = MetadataSignerTrustType.TRUSTED_ISSUER,
)

/**
 * Trust resolver backed by an explicit issuer-to-public-key allow-list.
 *
 * The resolver verifies with the configured public key and optionally binds
 * both the JWS `kid` and `alg` headers to the configured entry.
 */
class ConfiguredIssuerMetadataTrustResolver(
    private val trustedSigners: List<TrustedIssuerMetadataSigner>,
) : CredentialIssuerMetadataTrustResolver {
    override suspend fun verify(compactJwt: String, expectedCredentialIssuer: String): MetadataSigner {
        val decoded = compactJwt.decodeJws()
        val algorithm = decoded.header["alg"]?.jsonPrimitive?.takeIf { it.isString }?.content
            ?: error("Signed issuer metadata is missing a string alg header")
        val headerKeyId = decoded.header["kid"]?.jsonPrimitive?.let { primitive ->
            primitive.takeIf { primitive.isString }?.content
                ?: error("Signed issuer metadata kid must be a string")
        }
        val signer = trustedSigners.singleOrNull { candidate ->
            candidate.issuer == expectedCredentialIssuer &&
                    (headerKeyId == null || candidate.keyId == null || candidate.keyId == headerKeyId)
        } ?: error("No trusted metadata signer configured for issuer '$expectedCredentialIssuer' and kid '$headerKeyId'")

        require(signer.algorithm == null || signer.algorithm == algorithm) {
            "Configured metadata signer algorithm does not match JWS alg"
        }
        require(signer.keyId == null || headerKeyId == null || signer.keyId == headerKeyId) {
            "Configured metadata signer key ID does not match JWS kid"
        }

        val verificationKey = JWKKey.importJWK(signer.publicJwk).getOrThrow()
        verificationKey.verifyJws(compactJwt).getOrElse {
            error("Signed issuer metadata signature verification failed")
        }

        return MetadataSigner(
            keyId = signer.keyId ?: verificationKey.getKeyId().takeIf { it.isNotBlank() },
            algorithm = algorithm,
            trustType = signer.trustType,
        )
    }
}

/**
 * Resolves modern Credential Issuer Metadata once and projects it to the
 * legacy [OpenIDProviderMetadata] model used by wallet1 issuance flows.
 * Authorization Server metadata is fetched separately when required by the
 * legacy token client; the issuer metadata endpoint itself is never re-read.
 */
class LegacyIssuerMetadataResolver(
    httpClient: HttpClient,
    metadataTrustResolver: CredentialIssuerMetadataTrustResolver? = null,
) {
    private val resolver = IssuerMetadataResolver(httpClient, metadataTrustResolver)

    suspend fun resolve(credentialIssuer: String): OpenIDProviderMetadata.Draft13 {
        val metadata = resolver.resolveCredentialIssuerMetadata(credentialIssuer).metadata
        val authorizationServer = resolver.resolveAuthorizationServerMetadataWithFallback(metadata)
        return metadata.toLegacyMetadata(authorizationServer)
    }
}

private fun CredentialIssuerMetadata.toLegacyMetadata(
    authorizationServer: AuthorizationServerMetadata,
): OpenIDProviderMetadata.Draft13 = OpenIDProviderMetadata.Draft13(
    issuer = authorizationServer.issuer,
    authorizationEndpoint = authorizationServer.authorizationEndpoint,
    tokenEndpoint = authorizationServer.tokenEndpoint,
    jwksUri = authorizationServer.jwksUri,
    registrationEndpoint = authorizationServer.registrationEndpoint,
    scopesSupported = authorizationServer.scopesSupported ?: setOf("openid"),
    responseTypesSupported = authorizationServer.responseTypesSupported,
    responseModesSupported = authorizationServer.responseModesSupported
        ?.mapNotNull { runCatching { LegacyResponseMode.fromString(it) }.getOrNull() }
        ?.toSet()
        ?: setOf(LegacyResponseMode.query, LegacyResponseMode.fragment),
    grantTypesSupported = authorizationServer.grantTypesSupported
        ?.mapNotNull(LegacyGrantType::fromValue)
        ?.toSet()
        ?: setOf(LegacyGrantType.authorization_code, LegacyGrantType.pre_authorized_code),
    tokenEndpointAuthMethodsSupported = authorizationServer.tokenEndpointAuthMethodsSupported,
    tokenEndpointAuthSigningAlgValuesSupported = authorizationServer.tokenEndpointAuthSigningAlgValuesSupported,
    uiLocalesSupported = authorizationServer.uiLocalesSupported,
    serviceDocumentation = authorizationServer.serviceDocumentation,
    opPolicyUri = authorizationServer.opPolicyUri,
    opTosUri = authorizationServer.opTosUri,
    codeChallengeMethodsSupported = authorizationServer.codeChallengeMethodsSupported,
    requirePushedAuthorizationRequests = authorizationServer.requirePushedAuthorizationRequests,
    credentialIssuer = credentialIssuer,
    credentialEndpoint = credentialEndpoint,
    deferredCredentialEndpoint = deferredCredentialEndpoint,
    display = display?.mapNotNull { it.toLegacyDisplay() },
    credentialConfigurationsSupported = credentialConfigurationsSupported.mapValues { (_, configuration) ->
        configuration.toLegacyCredentialSupported()
    },
    authorizationServers = authorizationServerIssuers().toSet(),
)

private fun CredentialConfiguration.toLegacyCredentialSupported(): CredentialSupported {
    val format = LegacyCredentialFormat.fromValue(format.value)
        ?: error("Unsupported legacy credential format: ${format.value}")
    return CredentialSupported(
        format = format,
        scope = scope,
        vct = vct,
        cryptographicBindingMethodsSupported = cryptographicBindingMethodsSupported
            ?.map { it.value }
            ?.toSet(),
        credentialSigningAlgValuesSupported = credentialSigningAlgValuesSupported
            ?.mapNotNull { it.toLegacySigningAlgorithm() }
            ?.toSet(),
        proofTypesSupported = proofTypesSupported?.mapNotNull { (name, proof) ->
            runCatching { LegacyProofType.valueOf(name) }
                .getOrNull()
                ?.let { it to LegacyProofTypeMetadata(proof.proofSigningAlgValuesSupported) }
        }?.toMap(),
        display = credentialMetadata?.display?.mapNotNull { it.toLegacyDisplay() },
        credentialDefinition = credentialDefinition?.let {
            LegacyCredentialDefinition(type = it.type)
        },
        docType = doctype,
        customParameters = customParameters,
    )
}

private fun SigningAlgId.toLegacySigningAlgorithm(): CredSignAlgValues? = when (this) {
    is SigningAlgId.Jose -> CredSignAlgValues.Named(value)
    is SigningAlgId.LdSuite -> CredSignAlgValues.Named(value)
    is SigningAlgId.CoseName -> CredSignAlgValues.Named(value)
    is SigningAlgId.CoseValue -> CredSignAlgValues.Numeric(value)
}

private fun IssuerDisplay.toLegacyDisplay(): DisplayProperties? =
    name?.takeIf { it.isNotBlank() }?.let { displayName ->
        DisplayProperties(
            name = displayName,
            locale = locale,
            logo = logo?.let { LogoProperties(url = it.uri, altText = it.altText) },
        )
    }

private fun CredentialDisplay.toLegacyDisplay(): DisplayProperties? =
    name.takeIf { it.isNotBlank() }?.let { displayName ->
        DisplayProperties(
            name = displayName,
            locale = locale,
            logo = logo?.let { LogoProperties(url = it.uri, altText = it.altText) },
            description = description,
            backgroundColor = backgroundColor,
            backgroundImage = backgroundImage?.let { LogoProperties(url = it.uri) },
            textColor = textColor,
        )
    }
