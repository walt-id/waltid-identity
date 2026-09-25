package id.walt.issuer2.service.openid4vci

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
import id.walt.openid4vci.requests.credential.encryption.CredentialEncryptionProfile
import id.walt.openid4vci.tokens.jwt.Crypto2JwtSigningKey
import id.walt.sdjwt.metadata.issuer.JWTVCIssuerMetadata
import id.walt.sdjwt.metadata.type.SdJwtVcTypeMetadataDraft04
import io.ktor.server.plugins.NotFoundException
import kotlinx.serialization.json.*
import java.net.URI

class MetadataService(
    serviceConfig: Issuer2ServiceConfig,
    metadataConfig: Issuer2MetadataConfig,
    private val profileService: CredentialProfileService,
    private val sessionService: IssuanceSessionService,
    private val preAuthorizedGrantAnonymousAccessSupported: Boolean = false,
    /** Crypto2 token signing key used for signed issuer metadata. */
    private val crypto2TokenSigningKey: Crypto2JwtSigningKey? = null,
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

    private val issuerDisplay: List<IssuerDisplay>? =
        metadataConfig.issuerDisplay
            ?.map { DisplayUriResolver.resolve(it, issuerHttpBaseUrl) }
            ?.map { json.decodeFromJsonElement(IssuerDisplay.serializer(), it) }
            ?.takeIf { it.isNotEmpty() }

    private val credentialConfigurations: Map<String, CredentialConfiguration> =
        metadataConfig.credentialConfigurations.mapValues { (configurationId, value) ->
            json.decodeFromJsonElement(
                CredentialConfiguration.serializer(),
                DisplayUriResolver.resolve(value, issuerHttpBaseUrl),
            ).withResolvedVct(configurationId).also { configuration ->
                configuration.vct?.let { validateSelfHostedVct(configurationId, it) }
            }
        }

    private val configuredTypeMetadata: Map<String, SdJwtVcTypeMetadataDraft04> = buildMap {
        metadataConfig.sdJwtVcTypeMetadataConfiguration.forEach { (configurationId, metadata) ->
            val configuration = requireNotNull(credentialConfigurations[configurationId]) {
                "Type metadata references unknown credential configuration '$configurationId'"
            }
            require(configuration.format == CredentialFormat.SD_JWT_VC) {
                "Type metadata requires an SD-JWT credential configuration: '$configurationId'"
            }
            val vct = requireNotNull(configuration.vct) { "SD-JWT configuration '$configurationId' requires a VCT" }
            val suppliedVct = metadata.vct?.let { if (it == INTERNAL_VCT_BASE_URL) selfHostedVct(configurationId) else it }
            require(suppliedVct == null || suppliedVct == vct) {
                "Type metadata VCT does not match credential configuration '$configurationId'"
            }
            val resolved = metadata.copy(vct = vct)
            val previous = put(vct, resolved)
            require(previous == null || previous == resolved) { "Conflicting type metadata for VCT '$vct'" }
        }
    }

    fun getCredentialIssuerMetadata(): CredentialIssuerMetadata =
        resolveCredentialRequestEncryptionMetadata().let { credentialRequestEncryption ->
            CredentialIssuerMetadata.fromBaseUrl(
                baseUrl = baseUrl,
                credentialConfigurationsSupported = credentialConfigurations,
                credentialRequestEncryption = credentialRequestEncryption,
                batchCredentialIssuance = batchCredentialIssuance,
                display = issuerDisplay,
            )
        }

    suspend fun getSignedCredentialIssuerMetadata(): String {
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
            codeChallengeMethodsSupported = listOf("S256"),
            pushedAuthorizationRequestEndpointPath = "/par",
            requirePushedAuthorizationRequests = enforcePushedAuthorizationRequests,
            tokenEndpointAuthMethodsSupported =
                if (supportsClientAttestation) setOf(ClientAuthenticationMethods.ATTEST_JWT_CLIENT_AUTH) else null,
            clientAttestationSigningAlgValuesSupported =
                if (supportsClientAttestation) ClientAttestationSigningAlgorithms.SUPPORTED_JWS_ALGORITHMS else null,
            clientAttestationPopSigningAlgValuesSupported =
                if (supportsClientAttestation) ClientAttestationSigningAlgorithms.SUPPORTED_JWS_ALGORITHMS else null,
            preAuthorizedGrantAnonymousAccessSupported = preAuthorizedGrantAnonymousAccessSupported,
            authorizationResponseIssParameterSupported = true,
        )

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

    fun getVctTypeMetadata(credentialType: String): SdJwtVcTypeMetadataDraft04 {
        if (credentialType in Issuer2EndpointPaths.reservedVctNames) {
            throw NotFoundException("Credential type metadata not found: $credentialType")
        }
        val expectedVct = selfHostedVct(credentialType)
        credentialConfigurations.entries.firstOrNull { (_, configuration) ->
            configuration.vct == expectedVct
        }
            ?: throw NotFoundException("Credential type metadata not found: $credentialType")

        return configuredTypeMetadata[expectedVct] ?: SdJwtVcTypeMetadataDraft04(
            vct = expectedVct,
            name = credentialType,
            description = "$credentialType Verifiable Credential",
        )
    }

    fun issuerBaseUrl(): String = baseUrl

    /**
     * Publishes the public halves of every key this issuer signs with, per RFC 8414 `jwks_uri`.
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

    private fun CredentialConfiguration.withResolvedVct(credentialType: String): CredentialConfiguration =
        if (vct == INTERNAL_VCT_BASE_URL) copy(vct = selfHostedVct(credentialType)) else this

    private fun selfHostedVct(credentialType: String): String =
        "$baseUrl/$credentialType"

    private fun validateSelfHostedVct(configurationId: String, vct: String) {
        // VCTs may be external URLs or non-HTTP identifiers. Only our own protocol paths collide.
        val vctUri = runCatching { URI(vct) }.getOrNull() ?: return
        val issuerUri = URI(baseUrl)
        if (!vctUri.scheme.equals(issuerUri.scheme, ignoreCase = true) ||
            !vctUri.host.equals(issuerUri.host, ignoreCase = true) ||
            vctUri.effectivePort() != issuerUri.effectivePort()
        ) return

        val prefix = issuerUri.normalize().path.trimEnd('/') + "/"
        val path = vctUri.normalize().path ?: return
        if (!path.startsWith(prefix)) return
        val name = path.removePrefix(prefix).substringBefore('/')
        require(name !in Issuer2EndpointPaths.reservedVctNames) {
            "Credential configuration '$configurationId' has self-hosted VCT '$vct' under reserved " +
                    "OpenID4VCI path '$name'. Choose a different VCT path or an externally hosted VCT URL."
        }
    }

    private fun URI.effectivePort(): Int =
        if (port >= 0) port else if (scheme.equals("https", ignoreCase = true)) 443 else 80

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

    companion object {
        private const val INTERNAL_VCT_BASE_URL = "vctBaseUrl"
    }
}
