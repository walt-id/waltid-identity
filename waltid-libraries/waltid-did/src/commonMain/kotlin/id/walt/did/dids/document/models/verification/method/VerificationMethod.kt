package id.walt.did.dids.document.models.verification.method

import id.walt.crypto.keys.Key
import id.walt.crypto.utils.JsonUtils.toJsonElement
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

@Serializable(with = VerificationMethodSerializer::class)
data class VerificationMethod private constructor(
    val id: String,
    val type: VerificationMethodType,
    val material: Pair<VerificationMaterialType, JsonObject>,
    val controller: String,
    val customProperties: Map<String, JsonElement>? = null,
) {

    companion object Builder {

        private val reservedKeys = listOf(
            "id",
            "type",
            "controller",
            VerificationMaterialType.entries.toList(),
        )

        suspend fun build(
            controller: String,
            key: Key,
            id: String? = null,
            customProperties: Map<String, JsonElement>? = null,
        ): VerificationMethod {
            customProperties?.forEach {
                require(!reservedKeys.contains(it.key)) {
                    "Invalid attempt to override reserved DID Doc property with key ${it.key} via customProperties map"
                }
            }
            return VerificationMethod(
                id = id ?: key.getKeyId(),
                controller = controller,
                type = VerificationMethodType.JsonWebKey2020,
                material = Pair(
                    VerificationMaterialType.PublicKeyJwk,
                    key.exportJWKObject(),
                ),
                customProperties = customProperties,
            )
        }
    }
}

object VerificationMethodSerializer : KSerializer<VerificationMethod> {

    override val descriptor = JsonObject.serializer().descriptor

    override fun deserialize(decoder: Decoder): VerificationMethod {
        TODO()

    }

    override fun serialize(encoder: Encoder, value: VerificationMethod) {
        encoder.encodeSerializableValue(
            JsonObject.serializer(),
            buildJsonObject {
                put("id", value.id.toJsonElement())
                put("type", value.type.toJsonElement())
                put("controller", value.controller.toJsonElement())
                put(value.material.first.toString(), value.material.second)
                value.customProperties?.forEach {
                    put(it.key, it.value)
                }
            }
        )
    }
}
