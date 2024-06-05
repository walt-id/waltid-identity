package id.walt.verifier.policies

import id.walt.credentials.verification.CredentialWrapperValidatorPolicy
import id.walt.credentials.verification.PresentationDefinitionException
import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.oid4vc.data.dif.PresentationDefinition
import kotlinx.serialization.json.*

class PresentationDefinitionPolicy : CredentialWrapperValidatorPolicy(
    "presentation-definition",
    "Verifies that with an Verifiable Presentation at minimum the list of credentials `request_credentials` has been presented."
) {

    override suspend fun verify(data: JsonElement, args: Any?, context: Map<String, Any>): Result<Any> {
        val presentationDefinition = context["presentationDefinition"] as? PresentationDefinition
            ?: throw IllegalArgumentException("No presentationDefinition in context!")

        val requestedTypes = presentationDefinition.primitiveVerificationGetTypeList()

        println(data)
        val presentedTypes =
            data.jsonObject["vp"]!!.jsonObject["verifiableCredential"]?.jsonArray?.mapNotNull {
                it.jsonPrimitive.contentOrNull?.decodeJws()?.payload
                    ?.jsonObject?.get("vc")?.jsonObject?.get("type")?.jsonArray?.last()?.jsonPrimitive?.contentOrNull
            }?.filterNotNull() ?: emptyList()

        val success = presentedTypes.containsAll(requestedTypes)

        return if (success)
            Result.success(Unit)
        else {
            println("Requested types: $requestedTypes")
            println("Presented types: $presentedTypes")

            Result.failure(
                PresentationDefinitionException(
                    requestedTypes.minus(presentedTypes.toSet())
                )
            )
        }
    }
}
