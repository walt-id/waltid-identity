package id.walt.policies2.vc.policies

import com.nfeld.jsonpathkt.JsonPath
import id.walt.credentials.formats.DigitalCredential
import id.walt.policies2.vc.NotAllowedIssuerException
import id.walt.w3c.schemes.JwsSignatureScheme
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

    private fun JsonElement.getIssuerList(): List<String> = when (allowedIssuer) {
        is JsonPrimitive -> listOf(allowedIssuer.content)
        is JsonArray -> allowedIssuer.jsonArray.map { it.jsonPrimitive.content }
        else -> throw IllegalArgumentException("Invalid form for allowed_issuer: provide string or array")
    }

    fun getAllowedIssuers() = allowedIssuer.getIssuerList()

    companion object {
        private val claims = listOf(JwsSignatureScheme.JwsOption.ISSUER, "issuer").map { JsonPath.compile(it) }
    }

    @Serializable
    data class AllowedIssuerPolicyClaimCheckResult(
        val issuer: String,

        override val claim: String
    ) : PolicyClaimChecker.ClaimCheckResultSuccess()

    override suspend fun verify(credential: DigitalCredential): Result<JsonElement> {
        return PolicyClaimChecker.checkClaim(credential, claims) { claim ->
            val issuer = credential.issuer
            val allowedIssuers = getAllowedIssuers()

            when (issuer) {
                null -> Result.failure( // TODO: clearer exception
                    NotAllowedIssuerException(
                        issuer = issuer,
                        allowedIssuers = allowedIssuers
                    )
                )

                in allowedIssuers -> Result.success(
                    AllowedIssuerPolicyClaimCheckResult(
                        issuer = issuer,
                        claim = claim.toString()
                    )
                )

                else -> Result.failure(
                    NotAllowedIssuerException(
                        issuer = issuer,
                        allowedIssuers = allowedIssuers
                    )
                )
            }
        }
    }
}
