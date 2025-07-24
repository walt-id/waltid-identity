package id.walt.holderpolicies.checks

import com.nfeld.jsonpathkt.JsonPath
import com.nfeld.jsonpathkt.kotlinx.resolveOrNull
import id.walt.credentials.formats.DigitalCredential
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.all
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

@Serializable
@SerialName("basic")
data class BasicHolderPolicyCheck(
    val format: String? = null,
    val issuer: String? = null,
    val subject: String? = null,

    @SerialName("claims_present")
    val claimsPresent: List<String>? = null,

    @SerialName("claims_values")
    val claimsValues: Map<String, JsonElement>? = null
) : HolderPolicyCheck {
    override suspend fun matchesCredentials(credentials: Flow<DigitalCredential>): Boolean =
        credentials.all { credential ->
            if (format != null) {
                if (credential.format != format) return@all false
            }
            if (issuer != null) {
                if (credential.issuer != issuer) return@all false
            }
            if (subject != null) {
                if (credential.subject != subject) return@all false
            }

            val credentialData = credential.credentialData

            claimsPresent?.forEach { claimPath ->
                val resolved = JsonPath.compile(claimPath).resolveOrNull(credentialData) ?: JsonPath.compile(claimPath)
                    .resolveOrNull(credentialData["vc"]?.jsonObject ?: JsonObject(emptyMap()))
                if (resolved == null) return@all false
            }

            claimsValues?.forEach { (claimPath, expectedValue) ->
                val resolved = JsonPath.compile(claimPath).resolveOrNull(credentialData) ?: JsonPath.compile(claimPath)
                    .resolveOrNull(credentialData["vc"]?.jsonObject ?: JsonObject(emptyMap()))
                if (resolved != expectedValue) return@all false
            }

            return@all true
        }
}
