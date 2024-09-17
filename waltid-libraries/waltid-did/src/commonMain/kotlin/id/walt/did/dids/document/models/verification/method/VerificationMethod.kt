package id.walt.did.dids.document.models.verification.method

import id.walt.crypto.utils.JsonUtils.toJsonElement
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

private val requiredKeys = listOf(
    "id",
    "type",
    "controller",
)

private val reservedKeys = requiredKeys + VerificationMaterialType.entries.map { it.toString() }

/**
 * Class for representing a verification method of a DID Document according to the respective section of the [DID Core](https://www.w3.org/TR/did-core/#dfn-verificationmethod) specification
 * @property id The identifier of this [VerificationMethod] instance (cannot be empty).
 * @property type The type of this [VerificationMethod] instance. Refer to [VerificationMethodType] for more information.
 * @property material The cryptographic material is represented as a JsonElement and is paired with its encoding.
 * @property controller The identifier of the controller of this [VerificationMethod] instance (cannot be empty).
 * @property customProperties Optional user-defined custom properties that can be included in this [VerificationMethod] instance.
 * @see [VerificationMethodType]
 * @see [VerificationMaterialType]
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
@Serializable(with = VerificationMethodSerializer::class)
data class VerificationMethod(
    val id: String,
    val type: VerificationMethodType,
    val material: Pair<VerificationMaterialType, JsonElement>,
    val controller: String,
    val customProperties: Map<String, JsonElement>? = null,
) {

    init {
        require(id.isNotBlank()) { "id property of VerificationMethod must not be an empty string" }
        require(controller.isNotBlank()) { "controller property of VerificationMethod must not be an empty string" }
        customProperties?.forEach {
            require(!reservedKeys.contains(it.key)) {
                "Invalid attempt to override reserved VerificationMethod property with key ${it.key} via customProperties map"
            }
        }
    }
}

object VerificationMethodSerializer : KSerializer<VerificationMethod> {

    override val descriptor = JsonObject.serializer().descriptor

    override fun deserialize(decoder: Decoder): VerificationMethod {
        return decoder.decodeSerializableValue(
            JsonObject.serializer()
        ).let { jsonObject ->
            requiredKeys.forEach {
                require(jsonObject.contains(it))
            }
            val id = Json.decodeFromJsonElement<String>(jsonObject["id"]!!)
            val controller = Json.decodeFromJsonElement<String>(jsonObject["controller"]!!)
            val type = Json.decodeFromJsonElement<VerificationMethodType>(jsonObject["type"]!!)
            val customProperties = jsonObject.filterNot { reservedKeys.contains(it.key) }.let {
                it.ifEmpty { null }
            }
            val verificationMaterial = when (type) {
                VerificationMethodType.JsonWebKey2020 -> {
                    require(jsonObject.containsKey(VerificationMaterialType.PublicKeyJwk.toString()))
                    jsonObject[VerificationMaterialType.PublicKeyJwk.toString()]!!
                    Pair(VerificationMaterialType.PublicKeyJwk, jsonObject[VerificationMaterialType.PublicKeyJwk.toString()]!!)
                }

                else -> {
                    require(jsonObject.containsKey(VerificationMaterialType.PublicKeyMultibase.toString()))
                    jsonObject[VerificationMaterialType.PublicKeyMultibase.toString()]!!
                    Pair(VerificationMaterialType.PublicKeyMultibase, jsonObject[VerificationMaterialType.PublicKeyMultibase.toString()]!!)
                }
            }
            VerificationMethod(
                id = id,
                type = type,
                controller = controller,
                material = verificationMaterial,
                customProperties = customProperties,
            )
        }

    }


    override fun serialize(encoder: Encoder, value: VerificationMethod) {
        encoder.encodeSerializableValue(
            JsonObject.serializer(),
            buildJsonObject {
                put("id", value.id.toJsonElement())
                put("type", value.type.toJsonElement())
                put("controller", value.controller.toJsonElement())
                put(value.material.first.toString(), Json.encodeToJsonElement(value.material.second))
                value.customProperties?.forEach {
                    put(it.key, it.value)
                }
            }
        )
    }
}
