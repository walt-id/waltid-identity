package id.walt.policies2.vc.policies

import id.walt.credentials.formats.DigitalCredential
import id.walt.policies2.vc.NotAllowedIssuerException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
@SerialName("allowed-issuer")
data class AllowedIssuerPolicy(
    @SerialName("allowed_issuer")
    val allowedIssuer: JsonElement
) : CredentialVerificationPolicy2() {
    override val id = "allowed-issuer"

    fun getAllowedIssuers(): List<String> = when (allowedIssuer) {
        is JsonPrimitive -> listOf(allowedIssuer.content)
        is JsonArray -> allowedIssuer.jsonArray.map { it.jsonPrimitive.content }
        else -> throw IllegalArgumentException("Invalid form for allowed_issuer: provide string or array")
    }

    @Serializable
    @SerialName("AllowedIssuerPolicyClaimCheckResult")
    data class AllowedIssuerPolicyClaimCheckResult(
        val issuer: String,

        override val claim: String
    ) : PolicyClaimChecker.ClaimCheckResultSuccess()

    override suspend fun verify(
        credential: DigitalCredential,
        context: PolicyExecutionContext
    ): Result<JsonElement> {
        // The parser resolves the issuer across credential formats, including outer JWT iss.
        val issuer = credential.issuer
        val allowedIssuers = getAllowedIssuers()
        return if (issuer != null && issuer in allowedIssuers) {
            Result.success(Json.encodeToJsonElement(AllowedIssuerPolicyClaimCheckResult(issuer, "issuer")))
        } else {
            // TODO: clearer exception
            Result.failure(NotAllowedIssuerException(issuer = issuer, allowedIssuers = allowedIssuers))
        }
    }
}
