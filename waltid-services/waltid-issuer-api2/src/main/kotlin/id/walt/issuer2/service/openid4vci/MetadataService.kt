package id.walt.issuer2.service.openid4vci

import id.walt.issuer2.config.Issuer2MetadataConfig
import id.walt.issuer2.config.Issuer2ServiceConfig
import id.walt.issuer2.service.IssuerKeyResolver
import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import id.walt.openid4vci.metadata.issuer.CredentialIssuerMetadata
import id.walt.openid4vci.metadata.issuer.IssuerDisplay
import id.walt.openid4vci.metadata.oauth.AuthorizationServerMetadata
import id.walt.sdjwt.metadata.issuer.JWTVCIssuerMetadata
import id.walt.sdjwt.metadata.type.SdJwtVcTypeMetadataDraft04
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

class MetadataService(
    serviceConfig: Issuer2ServiceConfig,
    metadataConfig: Issuer2MetadataConfig,
    private val issuerKeyResolver: IssuerKeyResolver,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    private val baseUrl = serviceConfig.baseUrl.trimEnd('/') + "/openid4vci"
    private val vctAuthorityBaseUrl = serviceConfig.baseUrl.trimEnd('/')

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
        } ?: throw IllegalArgumentException("Invalid type value: $credentialType. The $credentialType type is not supported")

        return SdJwtVcTypeMetadataDraft04(
            vct = expectedVct,
            name = credentialType,
            description = "$credentialType Verifiable Credential",
        )
    }

    fun issuerBaseUrl(): String = baseUrl

    suspend fun listJwks(): JsonObject = issuerKeyResolver.listPublicJwks()

    private fun CredentialConfiguration.withResolvedVct(credentialType: String): CredentialConfiguration =
        if (vct == INTERNAL_VCT_BASE_URL) copy(vct = selfHostedVct(credentialType)) else this

    private fun selfHostedVct(credentialType: String): String =
        "$vctAuthorityBaseUrl/$credentialType"

    companion object {
        private const val INTERNAL_VCT_BASE_URL = "vctBaseUrl"
    }
}
