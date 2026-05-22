package id.walt.issuer2.service.openid4vci

import id.walt.issuer2.config.Issuer2MetadataConfig
import id.walt.issuer2.config.Issuer2ServiceConfig
import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import id.walt.openid4vci.metadata.issuer.CredentialIssuerMetadata
import id.walt.openid4vci.metadata.issuer.IssuerDisplay
import id.walt.openid4vci.metadata.oauth.AuthorizationServerMetadata
import id.walt.openid4vci.metadata.oidc.OpenIDProviderMetadata
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

class MetadataService(
    private val serviceConfig: Issuer2ServiceConfig,
    private val metadataConfig: Issuer2MetadataConfig,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    fun getCredentialIssuerMetadata(): CredentialIssuerMetadata =
        CredentialIssuerMetadata.fromBaseUrl(
            baseUrl = issuerBaseUrl(),
            credentialConfigurationsSupported = credentialConfigurations(),
            display = metadataConfig.issuerDisplay
                ?.map { json.decodeFromJsonElement(IssuerDisplay.serializer(), it) }
                ?.takeIf { it.isNotEmpty() },
        )

    fun getAuthorizationServerMetadata(): AuthorizationServerMetadata =
        AuthorizationServerMetadata.fromBaseUrl(
            baseUrl = issuerBaseUrl(),
            codeChallengeMethodsSupported = listOf("S256"),
            tokenEndpointAuthMethodsSupported = null,
            clientAttestationSigningAlgValuesSupported = null,
            clientAttestationPopSigningAlgValuesSupported = null,
        )

    fun getOpenIdProviderMetadata(): OpenIDProviderMetadata =
        OpenIDProviderMetadata.fromBaseUrl(baseUrl = issuerBaseUrl())

    fun listJwks(): JsonObject = buildJsonObject {
        put("keys", buildJsonArray { })
    }

    fun credentialConfigurations(): Map<String, CredentialConfiguration> =
        metadataConfig.credentialConfigurations.mapValues { (_, value) ->
            json.decodeFromJsonElement(CredentialConfiguration.serializer(), value)
        }

    fun issuerBaseUrl(): String = serviceConfig.baseUrl.trimEnd('/') + "/openid4vci"
}
