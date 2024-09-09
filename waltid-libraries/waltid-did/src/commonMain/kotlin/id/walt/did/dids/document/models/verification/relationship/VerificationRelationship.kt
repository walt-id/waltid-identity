package id.walt.did.dids.document.models.verification.relationship

import id.walt.did.dids.document.models.verification.method.VerificationMethod
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

@Serializable(with = VerificationRelationshipSerializer::class)
data class VerificationRelationship private constructor(
    val id: String?,
    val verificationMethod: VerificationMethod?,
) {
    companion object Builder {
        fun build(id: String): VerificationRelationship {
            return VerificationRelationship(id, null)
        }

        fun build(verificationMethod: VerificationMethod): VerificationRelationship {
            return VerificationRelationship(null, verificationMethod)
        }
    }
}

object VerificationRelationshipSerializer : KSerializer<VerificationRelationship> {
    override val descriptor: SerialDescriptor = JsonElement.serializer().descriptor

    override fun deserialize(decoder: Decoder): VerificationRelationship {
        val value = decoder.decodeSerializableValue(JsonElement.serializer())
        runCatching {
            Json.decodeFromJsonElement<VerificationMethod>(value)
        }.fold(
            onSuccess = {
                return VerificationRelationship.build(it)
            },
            onFailure = {
                return VerificationRelationship.build(
                    value.jsonPrimitive.content,
                )
            }
        )
    }

    override fun serialize(encoder: Encoder, value: VerificationRelationship) {
        if (value.verificationMethod != null) {
            encoder.encodeSerializableValue(
                JsonElement.serializer(),
                Json.encodeToJsonElement(value.verificationMethod),
            )
        } else {
            encoder.encodeString(value.id!!)
        }
    }
}