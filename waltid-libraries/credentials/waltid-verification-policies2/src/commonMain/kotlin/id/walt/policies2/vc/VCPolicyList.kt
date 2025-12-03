package id.walt.policies2.vc

import id.walt.policies2.vc.policies.CredentialVerificationPolicy2
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

@Serializable(with = PolicyListSerializer::class)
data class VCPolicyList(
    val policies: List<CredentialVerificationPolicy2>,
)

object PolicyListSerializer : KSerializer<VCPolicyList> {

    override val descriptor: SerialDescriptor =
        ListSerializer(CredentialVerificationPolicy2.serializer()).descriptor

    override fun deserialize(decoder: Decoder): VCPolicyList {
        val jsonInput = decoder as? JsonDecoder ?: throw SerializationException(
            "This serializer can be used only with Json format"
        )
        val json = jsonInput.json
        val jsonArray = jsonInput.decodeJsonElement() as? JsonArray
            ?: throw SerializationException("Expected a JsonArray")

        val policies = jsonArray.map { element ->
            when (element) {
                is JsonPrimitive -> {
                    if (!element.isString) {
                        throw SerializationException(
                            "Simple policy must be a string primitive."
                        )
                    }
                    VerificationPolicyManager.getSimpleVerificationPolicyByName(element.content)
                }

                is JsonObject -> {
                    json.decodeFromJsonElement<CredentialVerificationPolicy2>(element)
                }

                else -> throw SerializationException(
                    "Unexpected element in policies list: ${element::class.simpleName}"
                )
            }
        }
        return VCPolicyList(policies)
    }

    override fun serialize(encoder: Encoder, value: VCPolicyList) {
        val jsonOutput = encoder as? JsonEncoder ?: throw SerializationException(
            "This serializer can be used only with Json format"
        )
        val json = jsonOutput.json

        val jsonElements = value.policies.map { policy ->
            json.encodeToJsonElement(
                CredentialVerificationPolicy2.serializer(),
                policy
            )
        }
        val jsonArray = JsonArray(jsonElements)
        jsonOutput.encodeJsonElement(jsonArray)
    }
}
