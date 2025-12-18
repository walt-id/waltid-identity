@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.policies2.vc.policies

import com.nfeld.jsonpathkt.JsonPath
import com.nfeld.jsonpathkt.kotlinx.resolveOrNull
import id.walt.credentials.formats.DigitalCredential
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
        block: JsonElement.(path: JsonPath) -> Result<ClaimCheckResult>
    ): Result<JsonObject> {
        val credentialData = credential.credentialData
        val foundClaim = claims.firstOrNull { claim ->
            credentialData.resolveOrNull(claim) != null
        }

        if (foundClaim == null) {
            return Result.success(ClaimNotFoundClaimCheckResult.CLAIM_NOT_FOUND)
        }

        val data = credentialData.resolveOrNull(foundClaim)
            ?: throw IllegalStateException("This should not happen: claim could not be resolved the second time")

        return block.invoke(data, foundClaim).map { Json.encodeToJsonElement(it).jsonObject }
    }
}
