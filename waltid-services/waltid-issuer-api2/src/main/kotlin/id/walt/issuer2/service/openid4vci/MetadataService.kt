package id.walt.issuer2.service.openid4vci

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyManager
import id.walt.issuer2.config.Issuer2MetadataConfig
import id.walt.issuer2.config.Issuer2ServiceConfig
import id.walt.issuer2.service.CredentialProfileService
import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import id.walt.openid4vci.metadata.issuer.CredentialIssuerMetadata
import id.walt.openid4vci.metadata.issuer.IssuerDisplay
import id.walt.openid4vci.metadata.oauth.AuthorizationServerMetadata
import id.walt.sdjwt.metadata.issuer.JWTVCIssuerMetadata
import id.walt.sdjwt.metadata.type.SdJwtVcTypeMetadataDraft04
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

class MetadataService(
    serviceConfig: Issuer2ServiceConfig,
    metadataConfig: Issuer2MetadataConfig,
    private val profileService: CredentialProfileService,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    private val baseUrl = serviceConfig.baseUrl.trimEnd('/') + "/openid4vci"
    private val tokenSigningKeyConfig = serviceConfig.ciTokenKey

    private val issuerDisplay: List<IssuerDisplay>? =
        metadataConfig.issuerDisplay
            ?.map { json.decodeFromJsonElement(IssuerDisplay.serializer(), it) }
            ?.takeIf { it.isNotEmpty() }

    private val credentialConfigurations: Map<String, CredentialConfiguration> =
        metadataConfig.credentialConfigurations.mapValues { (configurationId, value) ->
            json.decodeFromJsonElement(CredentialConfiguration.serializer(), value)
                .withResolvedVct(configurationId)
        }

    fun getCredentialIssuerMetadata(): CredentialIssuerMetadata =
        CredentialIssuerMetadata.fromBaseUrl(
            baseUrl = baseUrl,
            credentialConfigurationsSupported = credentialConfigurations,
            display = issuerDisplay,
        )

    fun getAuthorizationServerMetadata(): AuthorizationServerMetadata =
        AuthorizationServerMetadata.fromBaseUrl(
            baseUrl = baseUrl,
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

    fun getVctTypeMetadata(credentialType: String): SdJwtVcTypeMetadataDraft04 {
        val expectedVct = selfHostedVct(credentialType)
        credentialConfigurations.entries.firstOrNull { (_, configuration) ->
            configuration.vct == expectedVct
        }
            ?: throw IllegalArgumentException("Invalid type value: $credentialType. The $credentialType type is not supported")

        return SdJwtVcTypeMetadataDraft04(
            vct = expectedVct,
            name = credentialType,
            description = "$credentialType Verifiable Credential",
        )
    }

    fun issuerBaseUrl(): String = baseUrl

    suspend fun listJwks(): JsonObject {
        val tokenSigningKey = KeyManager.resolveSerializedKey(tokenSigningKeyConfig)
        val profileIssuerKeys = profileService.listProfiles()
            .map { profile -> KeyManager.resolveSerializedKey(profile.issuerKey) }

        return buildJsonObject {
            put("keys", buildJsonArray {
                (listOf(tokenSigningKey) + profileIssuerKeys)
                    .map { key -> key.getPublicJwkWithKid() }
                    .deduplicated()
                    .forEach { add(it) }
            })
        }
    }

    private suspend fun Key.getPublicJwkWithKid(): JsonObject {
        val publicJwk = getPublicKey().exportJWKObject()
        return JsonObject(publicJwk.toMutableMap().apply {
            putIfAbsent("kid", JsonPrimitive(getKeyId()))
        })
    }

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

    companion object {
        private const val INTERNAL_VCT_BASE_URL = "vctBaseUrl"
    }
}