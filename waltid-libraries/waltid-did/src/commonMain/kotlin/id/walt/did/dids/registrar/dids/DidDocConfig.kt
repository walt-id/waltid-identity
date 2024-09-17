package id.walt.did.dids.registrar.dids

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.did.dids.DidUtils
import id.walt.did.dids.document.DidDocument
import id.walt.did.dids.document.models.service.Service
import id.walt.did.dids.document.models.service.ServiceBlock
import id.walt.did.dids.document.models.service.ServiceEndpoint
import id.walt.did.dids.document.models.verification.method.VerificationMaterialType
import id.walt.did.dids.document.models.verification.method.VerificationMethod
import id.walt.did.dids.document.models.verification.method.VerificationMethodType
import id.walt.did.dids.document.models.verification.relationship.VerificationRelationship
import id.walt.did.dids.document.models.verification.relationship.VerificationRelationshipType
import id.walt.did.utils.randomUUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import love.forte.plugin.suspendtrans.annotation.JsPromise
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

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
        rootCustomProperties?.forEach {
            require(!reservedKeys.contains(it.key)) {
                "Invalid attempt to override reserved root did document property with key ${it.key} via rootCustomProperties map"
            }
        }
        publicKeyMap.values.forEach {
            require(!it.hasPrivateKey) { "The key map must contain only public keys" }
        }
        if (verificationConfigurationMap.isNotEmpty()) require(publicKeyMap.isNotEmpty()) {
            "Key map cannot be empty when verification configuration map is not empty"
        }
        verificationConfigurationMap.forEach { (type, configSet) ->
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

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    suspend fun toDidDocument(did: String): DidDocument {

        val verificationMethod = verificationConfigurationMap
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

        val verificationRelationship = verificationConfigurationMap
            .entries
            .associate { (verRelType, verConfSet) ->
                verRelType to verConfSet.map {
                    VerificationRelationship
                        .buildFromId(
                            id = "$did#${it.publicKeyId}",
                        )
                }.toSet()
            }

        val service = serviceConfigurationSet
            .map {
                ServiceBlock(
                    id = "$did#${randomUUID()}",
                    type = setOf(it.type),
                    serviceEndpoint = it.serviceEndpoint,
                    customProperties = it.customProperties,
                )
            }.toSet().let {
                Service(it)
            }

        return DidDocument(buildMap {
            put("context", Json.encodeToJsonElement(context))
            put("id", Json.encodeToJsonElement(did))
            if (verificationMethod.isNotEmpty()) {
                put("verificationMethod", Json.encodeToJsonElement(verificationMethod))
                verificationRelationship.forEach { (verRelType, verRelValue) ->
                    put(verRelType.toString(), Json.encodeToJsonElement(verRelValue))
                }
            }
            if (service.serviceBlocks.isNotEmpty()) put("service", Json.encodeToJsonElement(service))
            rootCustomProperties?.forEach {
                put(it.key, it.value)
            }
        })
    }
}

@OptIn(ExperimentalJsExport::class)
@JsExport
@Serializable
data class VerificationMethodConfiguration(
    val publicKeyId: String,
    val customProperties: Map<String, JsonElement>? = null,
)

@OptIn(ExperimentalJsExport::class)
@JsExport
@Serializable
data class ServiceConfiguration(
    val type: String,
    val serviceEndpoint: Set<ServiceEndpoint>,
    val customProperties: Map<String, JsonElement>? = null,
)
