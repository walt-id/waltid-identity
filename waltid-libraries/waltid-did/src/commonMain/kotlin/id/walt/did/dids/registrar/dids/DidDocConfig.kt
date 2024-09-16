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
    val keyMap: Map<String, Key> = emptyMap(),
    val verificationConfigurationMap: Map<VerificationRelationshipType, Set<VerificationConfiguration>> = emptyMap(),
    val serviceConfigurationSet: Set<ServiceConfiguration> = emptySet(),
    val rootCustomProperties: Map<String, JsonElement>? = null,
) {

    companion object {
        private val reservedKeys = listOf(
            "context",
            "id",
            "verificationMethod",
            VerificationRelationshipType.entries.map { it.toString() },
            "service",
        )
    }

    init {
        rootCustomProperties?.forEach {
            require(!reservedKeys.contains(it.key)) {
                "Invalid attempt to override reserved root did document property with key ${it.key} via rootCustomProperties map"
            }
        }
        keyMap.values.forEach {
            require(!it.hasPrivateKey) { "The key map must contain only public keys" }
        }
        if (verificationConfigurationMap.isNotEmpty()) require(keyMap.isNotEmpty()) {
            "Key map cannot be empty when verification configuration map is not empty"
        }
        verificationConfigurationMap.forEach { (type, configSet) ->
            configSet.forEach { config ->
                require(keyMap.containsKey(config.keyId))
                val key = keyMap[config.keyId] ?: throw IllegalArgumentException(
                    "Key ID ${config.keyId} is missing from key map but is defined " +
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

        val verificationMethod = verificationConfigurationMap.mapTo(mutableSetOf()) { (verRelType, verConfSet) ->
            verConfSet.map { verConf ->
                val key = keyMap[verConf.keyId]
                    ?: throw IllegalStateException(
                        "This exception should never happen, we have already checked " +
                                "that all verification keys exist in the key map"
                    )
                VerificationMethod(
                    id = "$did#${verConf.keyId}",
                    type = VerificationMethodType.JsonWebKey2020,
                    material = VerificationMaterialType.PublicKeyJwk to key.exportJWKObject(),
                    controller = did,
                    customProperties = verConf.customProperties,
                )
            }
        }.flatten().toSet()
        val verificationRelationship = verificationConfigurationMap
            .mapValues { (verRelType, verConfSet) ->
                verConfSet.map { verConf ->
                    VerificationRelationship
                        .buildFromId(
                            id = "$did#${verConf.keyId}",
                        )
                }.toSet()
            }

        val service = serviceConfigurationSet.mapTo(mutableSetOf()) {
            ServiceBlock(
                id = "$did#${randomUUID()}",
                type = it.type,
                serviceEndpoint = it.serviceEndpoint,
                customProperties = it.customProperties,
            )
        }.let {
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
data class VerificationConfiguration(
    val keyId: String,
    val customProperties: Map<String, JsonElement>? = null,
)

@OptIn(ExperimentalJsExport::class)
@JsExport
@Serializable
data class ServiceConfiguration(
    val type: Set<String>,
    val serviceEndpoint: Set<ServiceEndpoint>,
    val customProperties: Map<String, JsonElement>? = null,
)
