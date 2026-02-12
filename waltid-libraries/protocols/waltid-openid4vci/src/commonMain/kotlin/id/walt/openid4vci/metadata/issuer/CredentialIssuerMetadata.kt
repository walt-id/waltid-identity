package id.walt.openid4vci.metadata.issuer

import io.ktor.http.URLProtocol
import io.ktor.http.Url
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Credential Issuer Metadata (OpenID4VCI 1.0).
 */
@OptIn(ExperimentalSerializationApi::class)
@KeepGeneratedSerializer
@Serializable(with = CredentialIssuerMetadataSerializer::class)
data class CredentialIssuerMetadata(
    @SerialName("credential_issuer")
    val credentialIssuer: String,
    @SerialName("authorization_servers")
    val authorizationServers: List<String>? = null,
    @SerialName("credential_endpoint")
    val credentialEndpoint: String,
    @SerialName("nonce_endpoint")
    val nonceEndpoint: String? = null,
    @SerialName("deferred_credential_endpoint")
    val deferredCredentialEndpoint: String? = null,
    @SerialName("notification_endpoint")
    val notificationEndpoint: String? = null,
    @SerialName("credential_request_encryption")
    val credentialRequestEncryption: CredentialRequestEncryption? = null,
    @SerialName("credential_response_encryption")
    val credentialResponseEncryption: CredentialResponseEncryption? = null,
    @SerialName("batch_credential_issuance")
    val batchCredentialIssuance: BatchCredentialIssuance? = null,
    @SerialName("display")
    val display: List<IssuerDisplay>? = null,
    @SerialName("credential_configurations_supported")
    val credentialConfigurationsSupported: Map<String, CredentialConfiguration>,
    val customParameters: Map<String, JsonElement>? = null,
) {
    init {
        require(credentialIssuer.isNotBlank()) {
            "Credential issuer must not be blank"
        }
        validateIssuerUrl(credentialIssuer)
        validateEndpointUrl("credential_endpoint", credentialEndpoint)
        deferredCredentialEndpoint?.let { validateEndpointUrl("deferred_credential_endpoint", it) }
        notificationEndpoint?.let { validateEndpointUrl("notification_endpoint", it) }
        nonceEndpoint?.let { validateEndpointUrl("nonce_endpoint", it) }

        require(credentialConfigurationsSupported.isNotEmpty()) {
            "credential_configurations_supported must not be empty"
        }
        require(credentialConfigurationsSupported.keys.all { it.isNotBlank() }) {
            "credential_configurations_supported keys must not be blank"
        }
        authorizationServers?.let { servers ->
            require(servers.isNotEmpty()) {
                "authorization_servers must not be empty"
            }
            servers.forEach { validateIssuerUrl(it) }
        }
        display?.let { entries ->
            require(entries.isNotEmpty()) {
                "display must not be empty"
            }
            val locales = entries.mapNotNull { it.locale }
            require(locales.size == locales.distinct().size) {
                "display entries must not duplicate locales"
            }
        }
        customParameters?.let { params ->
            require(params.keys.none { it in CredentialIssuerMetadataSerializer.knownKeys }) {
                "customParameters must not override standard issuer metadata fields"
            }
        }
    }

    /**
     * Returns the list of Authorization Server issuer identifiers to use for discovery.
     * If none are declared, the Credential Issuer acts as the Authorization Server.
     */
    fun authorizationServerIssuers(): List<String> =
        authorizationServers?.takeIf { it.isNotEmpty() } ?: listOf(credentialIssuer)

    /**
     * Returns the credential configuration for the given identifier, or null if not supported.
     */
    fun getCredentialConfiguration(id: String): CredentialConfiguration? =
        credentialConfigurationsSupported[id]

    /**
     * Returns true if the provided authorization_server value is declared for this issuer.
     */
    fun isAuthorizationServerDeclared(server: String?): Boolean =
        server == null || authorizationServerIssuers().contains(server)

    companion object {
        fun fromBaseUrl(
            baseUrl: String,
            credentialConfigurationsSupported: Map<String, CredentialConfiguration>,
            credentialEndpointPath: String = "/credential",
            deferredCredentialEndpointPath: String? = "/credential_deferred",
            notificationEndpointPath: String? = "/notification",
            nonceEndpointPath: String? = "/nonce",
            authorizationServers: List<String>? = null,
            credentialRequestEncryption: CredentialRequestEncryption? = null,
            credentialResponseEncryption: CredentialResponseEncryption? = null,
            batchCredentialIssuance: BatchCredentialIssuance? = null,
            display: List<IssuerDisplay>? = null,
        ): CredentialIssuerMetadata {
            val normalized = baseUrl.trimEnd('/')
            return CredentialIssuerMetadata(
                credentialIssuer = normalized,
                credentialEndpoint = normalized + credentialEndpointPath,
                credentialConfigurationsSupported = credentialConfigurationsSupported,
                authorizationServers = authorizationServers,
                deferredCredentialEndpoint = deferredCredentialEndpointPath?.let { normalized + it },
                notificationEndpoint = notificationEndpointPath?.let { normalized + it },
                nonceEndpoint = nonceEndpointPath?.let { normalized + it },
                credentialRequestEncryption = credentialRequestEncryption,
                credentialResponseEncryption = credentialResponseEncryption,
                batchCredentialIssuance = batchCredentialIssuance,
                display = display,
            )
        }

        private fun validateIssuerUrl(issuer: String) {
            val url = Url(issuer)
            require(url.protocol == URLProtocol.HTTPS) {
                "Credential issuer must use https scheme"
            }
            require(url.host.isNotBlank()) {
                "Credential issuer must include a host"
            }
            require(url.parameters.isEmpty()) {
                "Credential issuer must not include query parameters"
            }
            require(url.fragment.isEmpty()) {
                "Credential issuer must not include fragment components"
            }
        }

        private fun validateEndpointUrl(fieldName: String, value: String) {
            require(value.isNotBlank()) {
                "Credential issuer $fieldName must not be blank"
            }
            val url = Url(value)
            require(url.protocol == URLProtocol.HTTPS) {
                "Credential issuer $fieldName must use https scheme"
            }
            require(url.host.isNotBlank()) {
                "Credential issuer $fieldName must include a host"
            }
        }
    }
}

internal object CredentialIssuerMetadataSerializer : KSerializer<CredentialIssuerMetadata> {
    private val lenientJson = Json { ignoreUnknownKeys = true }

    override val descriptor: SerialDescriptor =
        CredentialIssuerMetadata.generatedSerializer().descriptor

    internal val knownKeys =
        descriptor.elementNames
            .filter { it != "customParameters" }
            .toSet()

    override fun serialize(encoder: Encoder, value: CredentialIssuerMetadata) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: throw SerializationException("CredentialIssuerMetadataSerializer can only be used with JSON")

        val base = buildJsonObject {
            put("credential_issuer", JsonPrimitive(value.credentialIssuer))
            value.authorizationServers?.takeIf { it.isNotEmpty() }?.let {
                val serializer = ListSerializer(String.serializer())
                put("authorization_servers", Json.encodeToJsonElement(serializer, it))
            }
            put("credential_endpoint", JsonPrimitive(value.credentialEndpoint))
            value.nonceEndpoint?.let { put("nonce_endpoint", JsonPrimitive(it)) }
            value.deferredCredentialEndpoint?.let { put("deferred_credential_endpoint", JsonPrimitive(it)) }
            value.notificationEndpoint?.let { put("notification_endpoint", JsonPrimitive(it)) }
            value.credentialRequestEncryption?.let {
                put("credential_request_encryption", Json.encodeToJsonElement(CredentialRequestEncryption.serializer(), it))
            }
            value.credentialResponseEncryption?.let {
                put("credential_response_encryption", Json.encodeToJsonElement(CredentialResponseEncryption.serializer(), it))
            }
            value.batchCredentialIssuance?.let {
                put("batch_credential_issuance", Json.encodeToJsonElement(BatchCredentialIssuance.serializer(), it))
            }
            value.display?.takeIf { it.isNotEmpty() }?.let {
                val serializer = ListSerializer(IssuerDisplay.serializer())
                put("display", Json.encodeToJsonElement(serializer, it))
            }
            val serializer = MapSerializer(String.serializer(), CredentialConfiguration.serializer())
            put(
                "credential_configurations_supported",
                Json.encodeToJsonElement(serializer, value.credentialConfigurationsSupported),
            )
        }
        val merged = value.customParameters?.let { extras ->
            buildJsonObject {
                base.forEach { (key, jsonValue) -> put(key, jsonValue) }
                extras.forEach { (key, jsonValue) -> put(key, jsonValue) }
            }
        } ?: base

        jsonEncoder.encodeJsonElement(merged)
    }

    override fun deserialize(decoder: Decoder): CredentialIssuerMetadata {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw SerializationException("CredentialIssuerMetadataSerializer can only be used with JSON")
        val jsonObject = jsonDecoder.decodeJsonElement().jsonObject

        val customParameters = jsonObject.filterKeys { it !in knownKeys }.takeIf { it.isNotEmpty() }
        val credentialConfigurationsElement = jsonObject["credential_configurations_supported"]
            ?: throw SerializationException("credential_configurations_supported is required")
        val credentialConfigurationsSupported = credentialConfigurationsElement.jsonObject.mapValues { (_, element) ->
            lenientJson.decodeFromJsonElement(CredentialConfiguration.serializer(), element)
        }

        return CredentialIssuerMetadata(
            credentialIssuer = jsonObject.string("credential_issuer")
                ?: throw SerializationException("credential_issuer is required"),
            authorizationServers = jsonObject["authorization_servers"]?.let {
                val serializer = ListSerializer(String.serializer())
                lenientJson.decodeFromJsonElement(serializer, it)
            },
            credentialEndpoint = jsonObject.string("credential_endpoint")
                ?: throw SerializationException("credential_endpoint is required"),
            nonceEndpoint = jsonObject.string("nonce_endpoint"),
            deferredCredentialEndpoint = jsonObject.string("deferred_credential_endpoint"),
            notificationEndpoint = jsonObject.string("notification_endpoint"),
            credentialRequestEncryption = jsonObject["credential_request_encryption"]?.let {
                lenientJson.decodeFromJsonElement(CredentialRequestEncryption.serializer(), it)
            },
            credentialResponseEncryption = jsonObject["credential_response_encryption"]?.let {
                lenientJson.decodeFromJsonElement(CredentialResponseEncryption.serializer(), it)
            },
            batchCredentialIssuance = jsonObject["batch_credential_issuance"]?.let {
                lenientJson.decodeFromJsonElement(BatchCredentialIssuance.serializer(), it)
            },
            display = jsonObject["display"]?.let {
                val serializer = ListSerializer(IssuerDisplay.serializer())
                lenientJson.decodeFromJsonElement(serializer, it)
            },
            credentialConfigurationsSupported = credentialConfigurationsSupported,
            customParameters = customParameters,
        )
    }
}

private fun JsonObject.string(name: String): String? =
    this[name]?.jsonPrimitive?.contentOrNull
