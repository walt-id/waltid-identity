package id.walt.verifier.oidc

import id.walt.w3c.utils.VCFormat
import id.walt.oid4vc.data.dif.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class RequestedCredential(
  val format: VCFormat? = null,
  val vct: String? = null,
  val type: String? = null,
  @SerialName("doc_type") val docType: String? = null,
  @SerialName("id") val credentialId: String? = null,
  @SerialName("input_descriptor") val inputDescriptor: InputDescriptor? = null,
  val policies: JsonArray? = null
) {
  // TODO: auto-generate id if not specified explicitely or in input descriptor ... ?
  val id: String
    get() = inputDescriptor?.id ?: credentialId ?: type ?: vct ?: docType ?: throw IllegalArgumentException("Either input_descriptor with id property or explicit id must be specified")

  private fun getDefaultFormatDefinition(): VCFormatDefinition {
    return when (format) {
      VCFormat.jwt_vc_json -> VCFormatDefinition(alg = setOf("EdDSA"))
      VCFormat.mso_mdoc -> VCFormatDefinition(alg = setOf("EdDSA", "ES256"))
      VCFormat.jwt_vc -> VCFormatDefinition(alg = setOf("ES256"))
      else -> VCFormatDefinition()
    }
  }

  private fun getDefaultInputDescriptorConstraints(): InputDescriptorConstraints {
    return when (format) {
      VCFormat.sd_jwt_vc -> InputDescriptorConstraints(
        limitDisclosure = DisclosureLimitation.required,
        fields = listOf(
          InputDescriptorField(path = listOf("$.vct"), filter = JsonObject(
              mapOf("type" to JsonPrimitive("string"), "pattern" to JsonPrimitive(vct))
            )
          )
        )
      )
      VCFormat.mso_mdoc -> InputDescriptorConstraints(
        limitDisclosure = DisclosureLimitation.required,
        fields = listOf(
          InputDescriptorField(
            path = listOf("$['docType']"),
            intentToRetain = false,
            filter = JsonObject(
              mapOf(
                "type" to JsonPrimitive("string"), "pattern" to JsonPrimitive(docType)
              )
            )
          )
        )
      )
      else -> InputDescriptorConstraints(
        listOf(
          InputDescriptorField(
            path = listOf("$.vc.type"), filter = JsonObject(
              mapOf(
                "type" to JsonPrimitive("string"), "pattern" to JsonPrimitive(type)
              )
            )
          )
        )
      )
    }
  }

  fun toInputDescriptor(): InputDescriptor {
    return inputDescriptor ?: InputDescriptor(
      id = id,
      format = mapOf(
        (format ?: throw IllegalArgumentException("Either input_descriptor or explicit format must be specified"))
        to getDefaultFormatDefinition()
      ),
      constraints = getDefaultInputDescriptorConstraints()
    )
  }
}
