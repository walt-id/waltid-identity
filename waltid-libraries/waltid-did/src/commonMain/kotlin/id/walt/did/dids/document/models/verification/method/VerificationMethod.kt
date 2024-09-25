package id.walt.did.dids.document.models.verification.method

import id.walt.commons.exceptions.InvalidServiceControllerException
import id.walt.commons.exceptions.InvalidServiceIdException
import id.walt.commons.exceptions.ReservedKeyOverrideException
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
        require(id.isNotBlank()) { throw InvalidServiceIdException("Service property id cannot be blank") }
        require(controller.isNotBlank()) {
            throw InvalidServiceControllerException("Service property controller cannot be blank")
        }
        customProperties?.forEach {
            require(!reservedKeys.contains(it.key)) {
                throw ReservedKeyOverrideException("Invalid attempt to override reserved Service property with key ${it.key} via customProperties map")

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
            val type = Json.decodeFromJsonElement<VerificationMethodType>(jsonObject["type"]!!)
            VerificationMethod(
                id = Json.decodeFromJsonElement<String>(jsonObject["id"]!!),
                type = type,
                controller = Json.decodeFromJsonElement<String>(jsonObject["controller"]!!),
                material = getVerificationMaterial(jsonObject, type),
                customProperties = getCustomProperties(jsonObject),
            )
        }
    }

    private fun getVerificationMaterial(
        methodValue: JsonObject,
        type: VerificationMethodType,
    ) = when (type) {
        VerificationMethodType.JsonWebKey2020 -> {
            require(methodValue.containsKey(VerificationMaterialType.PublicKeyJwk.toString()))
            methodValue[VerificationMaterialType.PublicKeyJwk.toString()]!!
            Pair(VerificationMaterialType.PublicKeyJwk, methodValue[VerificationMaterialType.PublicKeyJwk.toString()]!!)
        }

        else -> {
            require(methodValue.containsKey(VerificationMaterialType.PublicKeyMultibase.toString()))
            methodValue[VerificationMaterialType.PublicKeyMultibase.toString()]!!
            Pair(
                VerificationMaterialType.PublicKeyMultibase,
                methodValue[VerificationMaterialType.PublicKeyMultibase.toString()]!!
            )
        }
    }

    private fun getCustomProperties(methodValue: JsonObject) =
        methodValue.filterNot { reservedKeys.contains(it.key) }.let {
            it.ifEmpty { null }
        }

    override fun serialize(encoder: Encoder, value: VerificationMethod) {
        encoder.encodeSerializableValue(
            JsonObject.serializer(),
            buildJsonObject {
                put("id", value.id.toJsonElement())
                put("type", value.type.toJsonElement())
                put("controller", value.controller.toJsonElement())
                putMaterial(value.material)
                putCustomProperties(value.customProperties)
            }
        )
    }

    private fun JsonObjectBuilder.putMaterial(value: Pair<VerificationMaterialType, JsonElement>) =
        put(value.first.toString(), Json.encodeToJsonElement(value.second))

    private fun JsonObjectBuilder.putCustomProperties(value: Map<String, JsonElement>?) =
        value?.forEach {
            put(it.key, it.value)
        }
}
