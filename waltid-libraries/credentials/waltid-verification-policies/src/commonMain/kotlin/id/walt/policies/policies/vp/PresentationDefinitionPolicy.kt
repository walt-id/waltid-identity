package id.walt.policies.policies.vp

import id.walt.credentials.utils.VCFormat
import id.walt.crypto.utils.JsonUtils.toJsonElement
import id.walt.definitionparser.PresentationDefinition
import id.walt.definitionparser.PresentationDefinitionParser
import id.walt.definitionparser.PresentationSubmission
import id.walt.policies.CredentialWrapperValidatorPolicy
import id.walt.sdjwt.SDJwt
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

private val log = KotlinLogging.logger { }

@Serializable
class PresentationDefinitionPolicy : CredentialWrapperValidatorPolicy(
) {

    override val name = "presentation-definition"
    override val description =
        "Verifies that with an Verifiable Presentation at minimum the list of credentials `request_credentials` has been presented."
    override val supportedVCFormats = setOf(VCFormat.jwt_vp, VCFormat.jwt_vp_json, VCFormat.ldp_vp)

    override suspend fun verify(data: JsonObject, args: Any?, context: Map<String, Any>): Result<Any> {
        val presentationDefinition = context["presentationDefinition"]?.toJsonElement()
            ?.let { Json.decodeFromJsonElement<PresentationDefinition>(it) }
            ?: throw IllegalArgumentException("No presentationDefinition in context!")
        val presentationSubmission = context["presentationSubmission"]?.toJsonElement()
            ?.let { Json.decodeFromJsonElement<PresentationSubmission>(it) }
            ?: throw IllegalArgumentException("No presentationSubmission in context!")
        val format = presentationSubmission.descriptorMap.firstOrNull()?.format?.let { Json.decodeFromJsonElement<VCFormat>(it) }

        val requestedTypes = presentationDefinition.primitiveVerificationGetTypeList()

        val presentedTypes = when(format) {
            VCFormat.sd_jwt_vc -> listOf(data["vct"]!!.jsonPrimitive.content)
            else -> data["vp"]!!.jsonObject["verifiableCredential"]?.jsonArray?.mapNotNull {
                it.jsonPrimitive.contentOrNull?.let { SDJwt.parse(it) }?.fullPayload
                    ?.get("vc")?.jsonObject?.get("type")?.jsonArray?.last()?.jsonPrimitive?.contentOrNull
            } ?: emptyList()
        }

        val presentationDefinitionMatch = when(format) {
            VCFormat.sd_jwt_vc -> PresentationDefinitionParser.matchCredentialsForInputDescriptor(
                flowOf(data), presentationDefinition.inputDescriptors.first()
            ).toList().isNotEmpty()
            else -> data["vp"]!!.jsonObject["verifiableCredential"]?.jsonArray?.mapIndexedNotNull { idx,cred ->
                val payload = cred.jsonPrimitive.contentOrNull?.let { SDJwt.parse(it) }?.fullPayload ?: throw IllegalArgumentException("Credential $idx is not a valid JWT string")
                PresentationDefinitionParser.matchCredentialsForInputDescriptor(
                    flowOf(payload.get("vc")?.jsonObject ?: throw IllegalArgumentException("Credential $idx has no vc property")),
                    presentationDefinition.inputDescriptors.get(idx)
                ).toList().isNotEmpty()
            }!!.all { it }
        }

        val success = presentedTypes.containsAll(requestedTypes) && presentationDefinitionMatch

        return if (success)
            Result.success(presentedTypes)
        else {
            log.debug { "Requested types: $requestedTypes" }
            log.debug { "Presented types: $presentedTypes" }
            log.debug { "Presentation definition: $presentationDefinition" }
            log.debug { "Presented data: $data" }

            Result.failure(
              id.walt.policies.PresentationDefinitionException(
                missingCredentialTypes = requestedTypes.minus(
                  presentedTypes.toSet()
                ), presentationDefinitionMatch
              )
            )
        }
    }
}
