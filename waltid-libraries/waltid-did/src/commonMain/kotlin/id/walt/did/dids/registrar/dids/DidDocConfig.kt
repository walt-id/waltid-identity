package id.walt.did.dids.registrar.dids

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.crypto.utils.UuidUtils.randomUUIDString
import id.walt.did.dids.DidUtils
import id.walt.did.dids.document.DidDocument
import id.walt.did.dids.document.models.service.Service
import id.walt.did.dids.document.models.service.ServiceEndpoint
import id.walt.did.dids.document.models.service.ServiceMap
import id.walt.did.dids.document.models.verification.method.VerificationMaterialType
import id.walt.did.dids.document.models.verification.method.VerificationMethod
import id.walt.did.dids.document.models.verification.method.VerificationMethodType
import id.walt.did.dids.document.models.verification.relationship.VerificationRelationship
import id.walt.did.dids.document.models.verification.relationship.VerificationRelationshipType
import id.walt.did.dids.registrar.dids.DidDocConfig.Companion.buildFromPublicKeySet
import id.walt.did.dids.registrar.dids.DidDocConfig.Companion.buildFromPublicKeySetVerificationConfiguration
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import love.forte.plugin.suspendtrans.annotation.JsPromise
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * The purpose of this class is to provide users of the did library more control over the did documents that are produced when interfacing
 * with subclasses of [id.walt.did.dids.registrar.LocalRegistrar].
 *
 * **Supported subclasses**: [id.walt.did.dids.registrar.local.web.DidWebRegistrar]
 *
 * The default constructor for this class is, arguably, verbose. For more simplified ways of building object of this class, refer to the documentation of the [buildFromPublicKeySet] and [buildFromPublicKeySetVerificationConfiguration] methods.
 * @constructor This is the primary constructor.
 * @property context A list of JSON-LD context IRIs, defaults to the values specified in the respective section of the [DID Core](https://www.w3.org/TR/did-core/#json-ld) specification.
 * @property publicKeyMap An associative array of user-defined public key identifiers (not necessarily `kid` of a JWK) to **public key** instances of the [Key] class. Do not assign private keys as that will lead to an exception being thrown. The public keys provided here form the basis of generating the various verification methods of the did document. Refer to the [verificationConfigurationMap] property for additional information.
 * @property verificationConfigurationMap An associative array of [VerificationRelationshipType] (as defined in the DID Core specification) to a set of [VerificationMethodConfiguration] which, in short, defines the identifier of the public [Key] (must be contained in the [publicKeyMap] property) that will be used to generate a [VerificationMethod] in the resulting did document, along with one, or more, user-defined custom properties via the [VerificationMethodConfiguration.customProperties] map. If this map is empty, the did document that will be produced will **not** have any [VerificationMethod] defined. Note that it is perfectly valid from a specification perspective (albeit, not so useful).
 * @property serviceConfigurationSet This optional property is related with the service property of the did document, as defined in the respective section of the [DID Core](https://www.w3.org/TR/did-core/#services) specification. The input is a set of [ServiceConfiguration] instances, each of which relates to a [ServiceMap] of the did document. For each entry of this set, the user defines the type of the service (we encourage the use of the [id.walt.did.dids.document.models.service.RegisteredServiceType] for the purpose of interoperability), a set of one or more [ServiceEndpoint] subclasses (i.e., [id.walt.did.dids.document.models.service.ServiceEndpointURL] or [id.walt.did.dids.document.models.service.ServiceEndpointObject]) and optional user-defined custom properties that can be added to each [ServiceMap] via the [ServiceConfiguration.customProperties] parameter. If this set is empty, the resulting did document will **not** contain a service property.
 * @property rootCustomProperties Optional user-defined custom properties that can be included in the root portion of the did document that will be produced.
 * @see [VerificationMethodConfiguration]
 * @see [ServiceConfiguration]
 * @see [id.walt.did.dids.registrar.LocalRegistrar]
 * @see [DidWebCreateOptions]
 * @see [id.walt.did.dids.registrar.local.web.DidWebRegistrar]
 *
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
@Serializable
data class DidDocConfig(
    val context: List<String> = DidUtils.DEFAULT_CONTEXT,
    val publicKeyMap: Map<String, Key> = emptyMap(),
    val verificationConfigurationMap: Map<VerificationRelationshipType, Set<VerificationMethodConfiguration>> = emptyMap(),
    val serviceConfigurationSet: Set<ServiceConfiguration> = emptySet(),
    val rootCustomProperties: Map<String, JsonElement>? = null,
) {

    companion object {
        private val reservedKeys = listOf(
            "context",
            "id",
            "verificationMethod",
            "service",
        ) + VerificationRelationshipType.entries.map { it.toString() }

        /**
         * This is (probably) the simplest way of building instances of the [DidDocConfig] class. The [publicKeySet] parameter will be used as a basis to create [VerificationMethod] entries in the did document for all entries of [VerificationRelationshipType].
         * @param context Refer to the [DidDocConfig.context] property.
         * @param publicKeySet A set of public [Key] class instances that will be used to populate the did document's verification methods (the `kid` property will be used to assign a value to the fragment of the respective [VerificationMethod] entry that will be produced). Do not assign private keys as that will lead to an exception being thrown.
         * @param serviceConfigurationSet Refer to the [DidDocConfig.serviceConfigurationSet] property.
         * @param rootCustomProperties Refer to the [DidDocConfig.rootCustomProperties] property.
         */
        @JvmBlocking
        @JvmAsync
        @JsPromise
        @JsExport.Ignore
        suspend fun buildFromPublicKeySet(
            context: List<String> = DidUtils.DEFAULT_CONTEXT,
            publicKeySet: Set<Key> = emptySet(),
            serviceConfigurationSet: Set<ServiceConfiguration> = emptySet(),
            rootCustomProperties: Map<String, JsonElement>? = null,
        ) = DidDocConfig(
            context = context,
            publicKeyMap = publicKeySet.associateBy { it.getKeyId() },
            verificationConfigurationMap = publicKeySet.takeIf { it.isNotEmpty() }?.let {
                VerificationRelationshipType
                    .entries
                    .associateWith {
                        publicKeySet.map { publicKey ->
                            VerificationMethodConfiguration(
                                publicKeyId = publicKey.getKeyId(),
                            )
                        }.toSet()
                    }
            } ?: emptyMap(),
            serviceConfigurationSet = serviceConfigurationSet,
            rootCustomProperties = rootCustomProperties,
        )

        /**
         * This is (probably) the *middle way* (complexity-wise) of building instances of the [DidDocConfig] class (compared to the more simple [buildFromPublicKeySet] and the more verbose constructor of this class). This builder function provides users with more flexibility regarding the associations of [VerificationMethod] and [VerificationRelationshipType] in the did document that will be produced. This function allows users to specify which set of public [Key] instances will be associated with specific [VerificationRelationshipType] properties of the did document via the [verificationKeySetConfiguration] parameter.
         * @param context Refer to the [DidDocConfig.context] property.
         * @param verificationKeySetConfiguration A more simplified version of the main constructor's parameter [verificationConfigurationMap] that provides users with more fine-grained control over which public [Key] instances will be used to associate specific [VerificationRelationshipType] instances with [VerificationMethod] properties in the did document that will be produced. One minor limitation here is that custom properties on each produced [VerificationMethod] cannot be defined.
         * @param serviceConfigurationSet Refer to the [DidDocConfig.serviceConfigurationSet] property.
         * @param rootCustomProperties Refer to the [DidDocConfig.rootCustomProperties] property.
         */
        @JvmBlocking
        @JvmAsync
        @JsPromise
        @JsExport.Ignore
        suspend fun buildFromPublicKeySetVerificationConfiguration(
            context: List<String> = DidUtils.DEFAULT_CONTEXT,
            verificationKeySetConfiguration: Map<VerificationRelationshipType, Set<Key>> = emptyMap(),
            serviceConfigurationSet: Set<ServiceConfiguration> = emptySet(),
            rootCustomProperties: Map<String, JsonElement>? = null,
        ) = verificationKeySetConfiguration.values.flatten().associateBy { it.getKeyId() }.let { publicKeyMap ->
            DidDocConfig(
                context = context,
                publicKeyMap = publicKeyMap,
                verificationConfigurationMap = verificationKeySetConfiguration
                    .entries
                    .filter { it.value.isNotEmpty() }
                    .associate { (verRelType, verRelPublicKeySet) ->
                        verRelType to verRelPublicKeySet.map { publicKey ->
                            VerificationMethodConfiguration(
                                publicKeyId = publicKey.getKeyId(),
                            )
                        }.toSet()
                    },
                serviceConfigurationSet = serviceConfigurationSet,
                rootCustomProperties = rootCustomProperties,
            )
        }
    }

    init {
        validateRootCustomProperties()
        validatePublicKeyMap()
        validateVerificationConfigurationMap()
    }

    private fun validateRootCustomProperties() = rootCustomProperties?.forEach {
        require(!reservedKeys.contains(it.key)) {
            "Invalid attempt to override reserved root did document property with key ${it.key} via rootCustomProperties map"
        }
    }

    private fun validatePublicKeyMap() = publicKeyMap.values.forEach {
        require(!it.hasPrivateKey) { "The key map must contain only public keys" }
    }

    private fun validateVerificationConfigurationMap() =
        verificationConfigurationMap.takeIf { it.isNotEmpty() }?.let {
            require(publicKeyMap.isNotEmpty()) {
                "Key map cannot be empty when verification configuration map is not empty"
            }
            it.forEach { (type, configSet) ->
                configSet.forEach { config ->
                    require(publicKeyMap.containsKey(config.publicKeyId))
                    val key = publicKeyMap[config.publicKeyId] ?: throw IllegalArgumentException(
                        "Key ID ${config.publicKeyId} is missing from key map but is defined " +
                                "in verification configuration $config of type $type"
                    )
                    if (type == VerificationRelationshipType.KeyAgreement) {
                        require(key.keyType != KeyType.Ed25519) { "Invalid key type ${key.keyType} specified for keyAgreement property." }
                    }
                }
            }
        }

    /**
     * This function constructs the did document based on the provided configuration parameters and the did method specific identifier.
     * @param did The did method specific identifier that is required to produce the final did document.
     * @return [DidDocument] The produced did document.
     */
    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    suspend fun toDidDocument(did: String) = DidDocument(buildMap {
        put("context", Json.encodeToJsonElement(context))
        put("id", Json.encodeToJsonElement(did))
        createVerificationMethodSet(did).takeIf { it.isNotEmpty() }?.let {
            put("verificationMethod", Json.encodeToJsonElement(it))
            createVerificationRelationshipMap(did).forEach { (verRelType, verRelValue) ->
                put(verRelType.toString(), Json.encodeToJsonElement(verRelValue))

            }
        }
        createService(did).takeIf { it.serviceMaps.isNotEmpty() }?.let {
            put("service", Json.encodeToJsonElement(it))
        }
        rootCustomProperties?.forEach {
            put(it.key, it.value)
        }
    })

    private suspend fun createVerificationMethodSet(did: String) = verificationConfigurationMap
        .values
        .flatten()
        .map { verConf ->
            val key = publicKeyMap[verConf.publicKeyId]
                ?: throw IllegalStateException(
                    "This exception should never happen, we have already checked " +
                            "that all verification keys exist in the key map"
                )
            VerificationMethod(
                id = "$did#${verConf.publicKeyId}",
                type = VerificationMethodType.JsonWebKey2020,
                material = VerificationMaterialType.PublicKeyJwk to key.exportJWKObject(),
                controller = did,
                customProperties = verConf.customProperties,
            )
        }.toSet()

    private fun createVerificationRelationshipMap(did: String) = verificationConfigurationMap
        .entries
        .associate { (verRelType, verConfSet) ->
            verRelType to verConfSet.map {
                VerificationRelationship
                    .buildFromId(
                        id = "$did#${it.publicKeyId}",
                    )
            }.toSet()
        }

    private fun createService(did: String) = serviceConfigurationSet
        .map {
            ServiceMap(
                id = "$did#${randomUUIDString()}",
                type = setOf(it.type),
                serviceEndpoint = it.serviceEndpoint,
                customProperties = it.customProperties,
            )
        }.toSet().let {
            Service(it)
        }
}

/**
 * This class allows users to configure the verificationMethod property of the did document that will be produced.
 * @property publicKeyId The identifier of a public [Key] that is associated with [Key] entries in the [DidDocConfig.publicKeyMap] property.
 * @property customProperties Optional user-defined custom properties that can be included in the respective [VerificationMethod] entry of the did document that will be produced.
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
@Serializable
data class VerificationMethodConfiguration(
    val publicKeyId: String,
    val customProperties: Map<String, JsonElement>? = null,
)

/**
 * This class allows users to configure the service property of the did document that will be produced.
 * @property type The type of the service (we encourage the use of the [id.walt.did.dids.document.models.service.RegisteredServiceType] for the purpose of interoperability) that corresponds to the value that will be assigned to [ServiceMap.type] property in the did document that will be produced.
 * @property serviceEndpoint A set of one or more [ServiceEndpoint] subclasses (i.e., [id.walt.did.dids.document.models.service.ServiceEndpointURL] or [id.walt.did.dids.document.models.service.ServiceEndpointObject]) that will be assigned to the [ServiceMap.serviceEndpoint] property in the did document that will be produced.
 * @property customProperties Optional user-defined custom properties that can be included in the respective [ServiceMap] entry of the did document that will be produced.
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
@Serializable
data class ServiceConfiguration(
    val type: String,
    val serviceEndpoint: Set<ServiceEndpoint>,
    val customProperties: Map<String, JsonElement>? = null,
)
