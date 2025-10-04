package id.walt.policies2

import id.walt.policies2.policies.VerificationPolicy2
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

@Serializable(with = PolicyListSerializer::class)
data class PolicyList(
    val policies: List<VerificationPolicy2>,
)

object PolicyListSerializer : KSerializer<PolicyList> {

    override val descriptor: SerialDescriptor =
        ListSerializer(VerificationPolicy2.serializer()).descriptor

    override fun deserialize(decoder: Decoder): PolicyList {
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
                    json.decodeFromJsonElement<VerificationPolicy2>(element)
                }

                else -> throw SerializationException(
                    "Unexpected element in policies list: ${element::class.simpleName}"
                )
            }
        }
        return PolicyList(policies)
    }

    override fun serialize(encoder: Encoder, value: PolicyList) {
        val jsonOutput = encoder as? JsonEncoder ?: throw SerializationException(
            "This serializer can be used only with Json format"
        )
        val json = jsonOutput.json

        val jsonElements = value.policies.map { policy ->
//            when (policy) {
//                is CredentialSignaturePolicy -> JsonPrimitive(policy.id)
//                else -> json.encodeToJsonElement(
//                    VerificationPolicy2.serializer(),
//                    policy
//                )
//            }
            json.encodeToJsonElement(
                VerificationPolicy2.serializer(),
                policy
            )
        }
        val jsonArray = JsonArray(jsonElements)
        jsonOutput.encodeJsonElement(jsonArray)
    }
}
