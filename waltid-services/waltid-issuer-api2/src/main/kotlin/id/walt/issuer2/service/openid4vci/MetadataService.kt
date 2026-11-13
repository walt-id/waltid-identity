package id.walt.issuer2.service.openid4vci

import id.walt.openid4vci.metadata.issuer.signing.MetadataSigningKeyReferenceResolver
import id.walt.openid4vci.metadata.issuer.signing.MetadataSigningCertificateReferenceResolver

import id.walt.openid4vci.metadata.issuer.signing.MetadataJwtSigner

import id.walt.openid4vci.proofs.attestation.validateKeyAttestationConfiguration
import id.walt.crypto2.jose.Jwk
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.StoredKey
import id.walt.crypto2.keys.publicOnly
import id.walt.crypto2.migration.v1.V1PublicKeyReference
import id.walt.crypto2.migration.v1.v1PublicKeyReference
import id.walt.crypto2.serialization.BinaryData
import id.walt.crypto2.serialization.StoredKeyCodec
import id.walt.issuer2.config.CredentialEncryptionKeyConfig
import id.walt.issuer2.config.Issuer2EndpointPaths
import id.walt.issuer2.config.Issuer2MetadataConfig
import id.walt.issuer2.config.Issuer2ServiceConfig
import id.walt.issuer2.service.CredentialProfileService
import id.walt.issuer2.service.IssuanceSessionService
import id.walt.openid4vci.CredentialFormat
import id.walt.openid4vci.clientauth.ClientAuthenticationMethods
import id.walt.openid4vci.clientauth.attestation.ClientAttestationSigningAlgorithms
import id.walt.openid4vci.metadata.issuer.*
import id.walt.openid4vci.metadata.oauth.AuthorizationServerMetadata
import id.walt.openid4vci.pkce.PkceServiceConfig
import id.walt.openid4vci.requests.credential.encryption.CredentialEncryptionProfile
import id.walt.openid4vci.tokens.jwt.Crypto2JwtSigningKey
import id.walt.sdjwt.metadata.issuer.JWTVCIssuerMetadata
import id.walt.sdjwt.metadata.type.*
import kotlinx.serialization.json.*
import kotlinx.coroutines.runBlocking

class MetadataService @JvmOverloads constructor(
    serviceConfig: Issuer2ServiceConfig,
    metadataConfig: Issuer2MetadataConfig,
    private val profileService: CredentialProfileService,
    private val sessionService: IssuanceSessionService,
    private val preAuthorizedGrantAnonymousAccessSupported: Boolean = false,
    /** Metadata signing key when no dedicated signing strategy is configured. */
    private val crypto2TokenSigningKey: Crypto2JwtSigningKey? = null,
    metadataSigningKeyResolver: MetadataSigningKeyReferenceResolver? = null,
    metadataSigningCertificateResolver: MetadataSigningCertificateReferenceResolver? = null,
    private val pkceServiceConfig: PkceServiceConfig = PkceServiceConfig(),
    private val typeMetadata: IssuerTypeMetadataCatalogue = IssuerTypeMetadataCatalogue(serviceConfig, metadataConfig),
) {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    private val issuerHttpBaseUrl = serviceConfig.baseUrl.trimEnd('/')
    private val baseUrl = serviceConfig.openId4VciBaseUrl()
    private val tokenSigningKeyConfig = serviceConfig.ciTokenKey
    private val credentialEncryptionKeyConfig = serviceConfig.credentialEncryptionKey
    private val batchCredentialIssuance = serviceConfig.batchCredentialIssuance
    private val enforcePushedAuthorizationRequests = serviceConfig.enforcePushedAuthorizationRequests
    private val supportsClientAttestation = serviceConfig.clientAttestationConfig() != null
    private val supportsAttestationChallenges = serviceConfig.attestationChallengeConfig() != null

    // Eager loading makes invalid dedicated configuration fail initialization, without token-key fallback.
    private val dedicatedMetadataSigner = runBlocking {
        MetadataJwtSigner.dedicatedSigner(
            serviceConfig.signedMetadata,
            metadataSigningKeyResolver,
            metadataSigningCertificateResolver
        )
    }

    private val issuerDisplay: List<IssuerDisplay>? =
        metadataConfig.issuerDisplay
            ?.map { DisplayUriResolver.resolve(it, issuerHttpBaseUrl) }
            ?.map { json.decodeFromJsonElement(IssuerDisplay.serializer(), it) }
            ?.takeIf { it.isNotEmpty() }

    private val credentialConfigurations = typeMetadata.credentialConfigurations

    init {
        validateKeyAttestationConfiguration(
            credentialConfigurations.values,
            serviceConfig.keyAttestationConfig,
            nonceValidationConfigured = true
        )
    }

    suspend fun getPublishedTypeMetadata(credentialType: String): PublishedTypeMetadata =
        typeMetadata.publication(credentialType)

    fun getCredentialIssuerMetadata(): CredentialIssuerMetadata =
        resolveCredentialRequestEncryptionMetadata().let { credentialRequestEncryption ->
            CredentialIssuerMetadata.fromBaseUrl(
                baseUrl = baseUrl,
                deferredCredentialEndpointPath = "/deferred_credential",
                credentialConfigurationsSupported = credentialConfigurations,
                credentialRequestEncryption = credentialRequestEncryption,
                batchCredentialIssuance = batchCredentialIssuance,
                display = issuerDisplay,
            )
        }

    suspend fun getSignedCredentialIssuerMetadata(): String {
        dedicatedMetadataSigner?.let { return it.sign(getCredentialIssuerMetadata()) }
        val signingKey = requireNotNull(crypto2TokenSigningKey) {
            "Signed Credential Issuer Metadata requires a crypto2-capable token signing key"
        }
        return getCredentialIssuerMetadata().toSignedJwt(
            signingKey = signingKey.key,
            algorithm = signingKey.algorithm,
            // The kid the issuer publishes, which Crypto2JwtSigningKey already carries; do not let it default to the
            // crypto2 record ID.
            keyId = signingKey.keyId,
        )
    }

    fun getAuthorizationServerMetadata(): AuthorizationServerMetadata =
        AuthorizationServerMetadata.fromBaseUrl(
            baseUrl = baseUrl,
            codeChallengeMethodsSupported = pkceServiceConfig.supportedCodeChallengeMethods,
            pushedAuthorizationRequestEndpointPath = "/par",
            challengeEndpointPath = if (supportsAttestationChallenges) "/${Issuer2EndpointPaths.CHALLENGE}" else null,
            requirePushedAuthorizationRequests = enforcePushedAuthorizationRequests,
            tokenEndpointAuthMethodsSupported =
                if (supportsClientAttestation) setOf(ClientAuthenticationMethods.ATTEST_JWT_CLIENT_AUTH) else null,
            clientAttestationSigningAlgValuesSupported =
                if (supportsClientAttestation) ClientAttestationSigningAlgorithms.SUPPORTED_JWS_ALGORITHMS else null,
            clientAttestationPopSigningAlgValuesSupported =
                if (supportsClientAttestation) ClientAttestationSigningAlgorithms.SUPPORTED_JWS_ALGORITHMS else null,
            preAuthorizedGrantAnonymousAccessSupported = preAuthorizedGrantAnonymousAccessSupported,
            authorizationResponseIssParameterSupported = true,
        ).copy(authorizationDetailsTypesSupported = setOf("openid_credential"))

    suspend fun getAuthorizationServerDiscoveryMetadata(): AuthorizationServerMetadata {
        val metadata = getAuthorizationServerMetadata()
        val signer = dedicatedMetadataSigner ?: return metadata
        return metadata.copy(signedMetadata = signer.sign(metadata))
    }

    fun getJwtVcIssuerMetadata(): JWTVCIssuerMetadata =
        getAuthorizationServerMetadata().let { metadata ->
            JWTVCIssuerMetadata(
                issuer = metadata.issuer,
                jwksUri = metadata.jwksUri,
            )
        }

    fun getCredentialConfiguration(credentialConfigurationId: String): CredentialConfiguration? =
        credentialConfigurations[credentialConfigurationId]

    fun credentialConfigurationIdsForScopes(scopes: Set<String>): Set<String> =
        credentialConfigurations
            .filter { (configurationId, configuration) ->
                configurationId in scopes || configuration.scope?.let { it in scopes } == true
            }
            .keys

    fun issuerBaseUrl(): String = baseUrl

    /**
     * Publishes token and credential signing public keys, per RFC 8414 `jwks_uri`.
     * Dedicated metadata signers carry their public key in the JWT's `jwk` or `x5c` header instead.
     *
     * Public material only: a JWKS never needs an operational key, so this reads the published public JWK straight out
     * of the configured records instead of resolving a provider. That matters for remote-KMS keys (`tse`, `aws-rest-api`,
     * ...), whose public half is cached in the record but which cannot be restored offline.
     */
    suspend fun listJwks(): JsonObject {
        val configuredKeys = listOf(tokenSigningKeyConfig) +
                profileService.listProfiles().map { profile -> profile.issuerKey.toString() } +
                sessionService.listSessions().flatMap { session ->
                    session.issuanceRequests.map { it.issuerKey.toString() }
                }

        return buildJsonObject {
            put("keys", buildJsonArray {
                configuredKeys
                    .mapNotNull { serialized -> publicJwkWithKid(serialized) }
                    .deduplicated()
                    .forEach { add(it) }
            })
        }
    }

    /**
     * The `kid` must keep matching what was published before: v1 used `_keyId` when set and otherwise the RFC 7638
     * thumbprint, so [V1_LEGACY_KEY_ID_METADATA_KEY]-style precedence is preserved here. A record with no public
     * material at all is skipped rather than failing the whole endpoint, since one unusable profile key must not take
     * the issuer's JWKS offline.
     */
    private suspend fun publicJwkWithKid(serializedKey: String): JsonObject? {
        val reference = if (serializedKey.isStoredKey()) {
            StoredKeyCodec.decodeFromString(serializedKey).toPublicKeyReference() ?: return null
        } else {
            v1PublicKeyReference(serializedKey) ?: return null
        }
        val encoded = EncodedKey.Jwk(
            BinaryData(reference.publicJwk.toString().encodeToByteArray()),
            privateMaterial = false,
        )
        val keyId = reference.keyId ?: Jwk.sha256Thumbprint(encoded)
        return JsonObject(reference.publicJwk.toMutableMap().apply {
            putIfAbsent("kid", JsonPrimitive(keyId))
        })
    }

    private fun StoredKey.toPublicKeyReference(): V1PublicKeyReference? {
        val jwk = when (this) {
            is StoredKey.Software -> (material as? EncodedKey.Jwk)?.publicOnly()
            is StoredKey.Managed -> publicKey as? EncodedKey.Jwk
        } ?: return null
        val parsed = Json.parseToJsonElement(jwk.data.toByteArray().decodeToString()) as? JsonObject ?: return null
        // Deliberately id.value and not legacyKeyId(): Crypto2JwtSigningKey defaults its kid header to id.value, so
        // that is the kid actually appearing in issued tokens. Advertising anything else here would publish a kid the
        // issuer never emits. For keys migrated from v1 the two coincide, because the migration uses the v1 published
        // key ID as the record ID.
        return V1PublicKeyReference(parsed, id.value.takeIf { it.isNotBlank() })
    }

    private fun String.isStoredKey(): Boolean =
        (Json.parseToJsonElement(this) as? JsonObject)?.containsKey("version") == true

    private fun List<JsonObject>.deduplicated(): List<JsonObject> {
        val seen = mutableSetOf<String>()
        return filter { jwk ->
            val keys = jwk.deduplicationKeys()
            if (keys.any { it in seen }) {
                false
            } else {
                seen.addAll(keys)
                true
            }
        }
    }

    private fun JsonObject.deduplicationKeys(): Set<String> =
        setOfNotNull(
            this["kid"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let { "kid:$it" },
            JsonObject(filterKeys { it != "kid" }).toString().let { "jwk:$it" },
        )

    private fun resolveCredentialRequestEncryptionMetadata(): CredentialRequestEncryption? {
        val serializedKey = credentialEncryptionKeyConfig ?: return null
        val jwk = CredentialEncryptionKeyConfig.publicMetadataJwk(serializedKey)

        return CredentialRequestEncryption(
            jwks = buildJsonObject {
                put("keys", buildJsonArray { add(jwk) })
            },
            encValuesSupported = CredentialEncryptionProfile.encValuesSupported,
            encryptionRequired = false,
        )
    }

}
