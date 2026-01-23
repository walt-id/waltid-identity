package id.walt.did.dids.document.models.verification.relationship

import id.walt.did.dids.document.models.verification.method.VerificationMethod
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * Class representing a Verification Relationship as defined in the respective section of the [DID Core](https://www.w3.org/TR/did-core/#verification-relationships) specification.
 * Only one of the properties of this class can have a value.
 * Refer to the respective builder functions [VerificationRelationship.buildFromId] and [VerificationRelationship.buildFromVerificationMethod]
 * @property id The identifier of the referenced [VerificationMethod]
 * @property verificationMethod The directly embedded [VerificationMethod] instance
 * @see [VerificationRelationship.buildFromId]
 * @see [VerificationRelationship.buildFromVerificationMethod]
 * @see [VerificationMethod]
 */
@ConsistentCopyVisibility
@OptIn(ExperimentalJsExport::class)
@JsExport
@Serializable(with = VerificationRelationshipSerializer::class)
data class VerificationRelationship private constructor(
    val id: String?,
    val verificationMethod: VerificationMethod?,
) {

    companion object Builder {
        /**
         * Construct a new instance of [VerificationRelationship] by referencing a [VerificationMethod] of the DID document
         * @param id The identifier of the referenced [VerificationMethod]
         * @return The constructed [VerificationRelationship] instance with its [VerificationRelationship.id] property assigned
         */
        fun buildFromId(id: String): VerificationRelationship {
            return VerificationRelationship(id, null)
        }

        /**
         * Construct a new instance of [VerificationRelationship] by directly embedding a [VerificationMethod] instance
         * @param verificationMethod The [VerificationMethod] instance that will be directly embedded
         * @return The constructed [VerificationRelationship] instance with its [VerificationRelationship.verificationMethod] property assigned
         */
        fun buildFromVerificationMethod(verificationMethod: VerificationMethod): VerificationRelationship {
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
                return VerificationRelationship.buildFromVerificationMethod(it)
            },
            onFailure = {
                return VerificationRelationship.buildFromId(
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
