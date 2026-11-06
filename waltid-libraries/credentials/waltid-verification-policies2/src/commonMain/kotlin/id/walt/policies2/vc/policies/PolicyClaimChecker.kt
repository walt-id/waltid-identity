@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.policies2.vc.policies

import com.nfeld.jsonpathkt.JsonPath
import com.nfeld.jsonpathkt.kotlinx.resolveOrNull
import id.walt.credentials.formats.DigitalCredential
import id.walt.credentials.signatures.JwtBasedSignature
import id.walt.credentials.utils.JwtUtils
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

object PolicyClaimChecker {

    @Serializable
    sealed class ClaimCheckResult {
        @EncodeDefault
        @SerialName("claim_checked")
        open val claimChecked: Boolean = true
    }

    @Serializable
    sealed class ClaimCheckResultSuccess() : ClaimCheckResult() {
        abstract val claim: String
    }

    @Serializable
    @SerialName("ClaimNotFoundClaimCheckResult")
    class ClaimNotFoundClaimCheckResult(

    ) : ClaimCheckResult() {
        override val claimChecked = false

        companion object {
            val CLAIM_NOT_FOUND = Json.encodeToJsonElement(ClaimNotFoundClaimCheckResult()).jsonObject
        }
    }

    fun checkClaim(
        credential: DigitalCredential,
        claims: List<JsonPath>,
        requireField: Boolean = false,
        checkAllClaims: Boolean = false,
        block: JsonElement.(path: JsonPath) -> Result<ClaimCheckResult>
    ): Result<JsonObject> {
        val dataSources = buildList {
            add(credential.credentialData)
            // JWT validity claims apply even when the parser unwraps the inner vc object.
            // W3C VC 1.1 section 6.3.1 and RFC 7519 sections 4.1.4-4.1.5.
            if (checkAllClaims && credential.signature is JwtBasedSignature) {
                credential.signed?.let { add(JwtUtils.parseJwt(it.substringBefore("~")).second) }
            }
        }
        val foundClaims = dataSources.flatMap { data ->
            claims.mapNotNull { claim -> data.resolveOrNull(claim)?.let { claim to it } }
        }.let { if (checkAllClaims) it else it.take(1) }

        if (foundClaims.isEmpty()) {
            if (requireField) {
                val claimNames = claims.joinToString(", ")
                return Result.failure(IllegalArgumentException("Mandatory claim ($claimNames) is absent"))
            }
            return Result.success(ClaimNotFoundClaimCheckResult.CLAIM_NOT_FOUND)
        }

        val results = foundClaims.map { (claim, data) ->
            block.invoke(data, claim).getOrElse { return Result.failure(it) }
        }
        return Result.success(Json.encodeToJsonElement(results.first()).jsonObject)
    }
}
