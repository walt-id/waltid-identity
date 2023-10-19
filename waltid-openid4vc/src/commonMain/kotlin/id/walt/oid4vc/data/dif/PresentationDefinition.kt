package id.walt.oid4vc.data.dif

import id.walt.oid4vc.data.JsonDataObject
import id.walt.oid4vc.data.JsonDataObjectFactory
import id.walt.oid4vc.data.JsonDataObjectSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
data class PresentationDefinition(
    val id: String = "1",
    @SerialName("input_descriptors") @Serializable(InputDescriptorListSerializer::class) val inputDescriptors: List<InputDescriptor>,
    val name: String? = null,
    val purpose: String? = null,
    @Serializable(VCFormatMapSerializer::class) val format: Map<VCFormat, VCFormatDefinition>? = null,
    @SerialName("submission_requirements") @Serializable(SubmissionRequirementListSerializer::class) val submissionRequirements: List<SubmissionRequirement>? = null,
    override val customParameters: Map<String, JsonElement> = mapOf()
) : JsonDataObject() {
    override fun toJSON() = Json.encodeToJsonElement(PresentationDefinitionSerializer, this).jsonObject


    /**
     * https://identity.foundation/presentation-exchange/spec/v1.0.0/#input-descriptor
     */
    // FIXME: proper PresentationDefinition checker
    fun primitiveVerificationGetTypeList(): List<String> {
        val fields = inputDescriptors.mapNotNull { it.constraints?.fields }.flatten()
            .filter { field -> field.path.any { "type" in it } && field.filter?.get("type")?.jsonPrimitive?.contentOrNull == "string" }

        val types = fields.mapNotNull { it.filter?.get("pattern")?.jsonPrimitive?.contentOrNull }

        return types
    }

    companion object : JsonDataObjectFactory<PresentationDefinition>() {
        override fun fromJSON(jsonObject: JsonObject) =
            Json.decodeFromJsonElement(PresentationDefinitionSerializer, jsonObject)

        fun primitiveGenerationFromVcTypes(types: List<String>): PresentationDefinition {
            return PresentationDefinition(inputDescriptors = types.map { type ->
                InputDescriptor(
                    id = type,
                    format = mapOf(VCFormat.jwt_vc_json to VCFormatDefinition(alg = setOf("EdDSA"))),
                    constraints = InputDescriptorConstraints(
                        listOf(
                            InputDescriptorField(
                                path = listOf("$.type"), filter = JsonObject(
                                    mapOf(
                                        "type" to JsonPrimitive("string"), "pattern" to JsonPrimitive(type)
                                    )
                                )
                            )
                        )
                    )
                )
            })
        }


    }
}

object PresentationDefinitionSerializer :
    JsonDataObjectSerializer<PresentationDefinition>(PresentationDefinition.serializer())

fun main() {
    //language=json
    val presDef = Json.parseToJsonElement(
        """
  {
    "id": "\u003cautomatically assigned\u003e",
    "input_descriptors": [
      {
        "id": "VerifiableId",
        "format": {
          "jwt_vc_json": {
            "alg": [
              "EdDSA"
            ]
          }
        },
        "constraints": {
          "fields": [
            {
              "path": [
                "${'$'}.type"
              ],
              "filter": {
                "type": "string",
                "pattern": "VerifiableId"
              }
            }
          ]
        }
      }
    ]
  }
    """.trimIndent()
    )

    println(Json.decodeFromJsonElement<PresentationDefinition>(presDef).primitiveVerificationGetTypeList())
}
