package id.walt.credentials.verification.policies

import id.walt.credentials.schemes.JwsSignatureScheme.JwsOption
import id.walt.credentials.verification.CredentialWrapperValidatorPolicy
import id.walt.credentials.verification.NotAllowedIssuerException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

class AllowedIssuerPolicy : CredentialWrapperValidatorPolicy(
    "allowed-issuer",
    "Checks that the issuer of the credential is present in the supplied list."
) {
    override suspend fun verify(data: JsonObject, args: Any?, context: Map<String, Any>): Result<Any> {
        val allowedIssuers = when (args) {
            is JsonPrimitive -> listOf(args.content)
            is JsonArray -> args.map { it.jsonPrimitive.content }
            else -> throw IllegalArgumentException("Invalid argument, please provide a single allowed issuer, or an list of allowed issuers.")
        }

        val issuer =
            data[JwsOption.ISSUER]?.jsonPrimitive?.content ?: throw IllegalArgumentException("No issuer found in credential: \"iss\"")

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
