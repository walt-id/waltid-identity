package id.walt.policies2.vp.policies

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

@Serializable(with = VPPolicyListSerializer::class)
data class VPPolicyList(
    @SerialName("jwt_vc_json")
    val jwtVcJson: List<JwtVcJsonVPPolicy>,

    @SerialName("dc+sd-jwt")
    val dcSdJwt: List<DcSdJwtVPPolicy>,

    @SerialName("mso_mdoc")
    val msoMdoc: List<MdocVPPolicy>
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun splitFromVPPolicyList(mixedPolicies: List<VPPolicy2>): VPPolicyList =
            VPPolicyList(
                jwtVcJson = mixedPolicies.filter { it is JwtVcJsonVPPolicy } as List<JwtVcJsonVPPolicy>,
                dcSdJwt = mixedPolicies.filter { it is DcSdJwtVPPolicy } as List<DcSdJwtVPPolicy>,
                msoMdoc = mixedPolicies.filter { it is MdocVPPolicy } as List<MdocVPPolicy>
            )
    }
}

object VPPolicyListSerializer : KSerializer<VPPolicyList> {

    override val descriptor: SerialDescriptor =
        ListSerializer(VPPolicy2.serializer()).descriptor

    override fun deserialize(decoder: Decoder): VPPolicyList {
        val jsonInput = decoder as? JsonDecoder ?: throw SerializationException(
            "This serializer can be used only with Json format"
        )
        val json = jsonInput.json

        val elem = jsonInput.decodeJsonElement()

        val allPolicies = when (elem) {
            is JsonObject -> elem.values.flatMap { it as? JsonArray ?: throw SerializationException("Expected a JsonArray, but was: $elem") }
            is JsonArray -> elem.map { it }
            else -> throw SerializationException("Invalid JSON structure of VPPolicyList, was: $elem")
        }

        val policies = allPolicies.map { element ->
            when (element) {
                is JsonPrimitive -> {
                    if (!element.isString) {
                        throw SerializationException(
                            "Simple policy must be a string primitive."
                        )
                    }
                    VPVerificationPolicyManager.getSimpleVerificationPolicyByName(element.content)
                }

                is JsonObject -> {
                    json.decodeFromJsonElement<VPPolicy2>(element)
                }

                else -> throw SerializationException(
                    "Unexpected element in policies list: ${element::class.simpleName}"
                )
            }
        }

        return VPPolicyList.splitFromVPPolicyList(policies)
    }

    private fun List<VPPolicy2>.serializePolicyList() =
        JsonArray(map { if (VPVerificationPolicyManager.isSimplePolicy(it.id)) JsonPrimitive(it.id) else Json.encodeToJsonElement(it) })


    override fun serialize(encoder: Encoder, value: VPPolicyList) {
        val jsonOutput = encoder as? JsonEncoder ?: throw SerializationException(
            "This serializer can be used only with Json format"
        )

        val result = JsonObject(
            mapOf(
                "jwt_vc_json" to value.jwtVcJson.serializePolicyList(),
                "dc+sd-jwt" to value.dcSdJwt.serializePolicyList(),
                "mso_mdoc" to value.msoMdoc.serializePolicyList(),
            )
        )

        jsonOutput.encodeJsonElement(result)
    }
}
