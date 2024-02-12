package id.walt.credentials.verification.policies

import id.walt.credentials.schemes.JwsSignatureScheme.JwsOption
import id.walt.credentials.verification.CredentialWrapperValidatorPolicy
import id.walt.credentials.verification.NotAllowedIssuerException
import id.walt.credentials.verification.VerificationPolicy.VerificationPolicyArgumentType.DID
import id.walt.credentials.verification.VerificationPolicy.VerificationPolicyArgumentType.DID_ARRAY
import kotlinx.serialization.json.*

class AllowedIssuerPolicy : CredentialWrapperValidatorPolicy(
    "allowed-issuer",
    "Checks that the issuer of the credential is present in the supplied list.",
    listOf(DID, DID_ARRAY)
) {
    override suspend fun verify(data: JsonElement, args: Any?, context: Map<String, Any>): Result<Any> {
        val allowedIssuers = when (args) {
            is JsonPrimitive -> listOf(args.content)
            is JsonArray -> args.map { it.jsonPrimitive.content }
            else -> throw IllegalArgumentException("Invalid argument, please provide a single allowed issuer, or an list of allowed issuers.")
        }

        val issuer =
            data.jsonObject[JwsOption.ISSUER]?.jsonPrimitive?.content
                ?: data.jsonObject["issuer"]?.jsonPrimitive?.content
                ?: throw IllegalArgumentException("No issuer found in credential: $data")

        return when (issuer) {
            in allowedIssuers -> Result.success(issuer)
            else -> Result.failure(
                NotAllowedIssuerException(
                    issuer = issuer,
                    allowedIssuers = allowedIssuers
                )
            )
        }
    }
}
