package id.walt.verifier.policies

import id.walt.credentials.utils.VCFormat
import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.policies.CredentialWrapperValidatorPolicy
import io.github.oshai.kotlinlogging.KotlinLogging
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
        val presentationDefinition = context["presentationDefinition"] as? PresentationDefinition
            ?: throw IllegalArgumentException("No presentationDefinition in context!")

        val requestedTypes = presentationDefinition.primitiveVerificationGetTypeList()

        val presentedTypes =
            data["vp"]!!.jsonObject["verifiableCredential"]?.jsonArray?.mapNotNull {
                it.jsonPrimitive.contentOrNull?.decodeJws()?.payload
                    ?.jsonObject?.get("vc")?.jsonObject?.get("type")?.jsonArray?.last()?.jsonPrimitive?.contentOrNull
            } ?: emptyList()

        val success = presentedTypes.containsAll(requestedTypes)

        return if (success)
            Result.success(presentedTypes)
        else {
            log.debug { "Requested types: $requestedTypes" }
            log.debug { "Presented types: $presentedTypes" }

            Result.failure(
              id.walt.policies.PresentationDefinitionException(
                missingCredentialTypes = requestedTypes.minus(
                  presentedTypes.toSet()
                )
              )
            )
        }
    }
}
