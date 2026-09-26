package id.walt.issuer2.repository

import id.walt.issuer2.domain.IssuanceSession
import kotlinx.serialization.json.*

/** Reads saved issuance snapshots from before and after multi-selection support. */
object IssuanceSessionStorageCodec {
    fun decode(encoded: String): IssuanceSession =
        Json.decodeFromJsonElement(normalize(Json.parseToJsonElement(encoded).jsonObject))

    internal fun normalize(stored: JsonObject): JsonObject {
        val normalized = stored.toMutableMap()
        if ("issuanceRequests" !in stored) {
            // Original tokens used the configuration ID as their credential identifier.
            val identifier = stored.getValue("credentialConfigurationId")
            val request = stored.filterKeys { it in requestFields }.toMutableMap()
            request["credentialIdentifier"] = identifier
            stored["expectedCredentialProofKeyJwk"]?.takeUnless { it is JsonNull }?.let {
                request["expectedCredentialProofKeyJwks"] = JsonArray(listOf(it))
            }
            normalized.keys.removeAll(requestFields + setOf("expectedCredentialProofKeyJwk", "issuedCredentialFormat"))
            normalized["issuanceRequests"] = JsonArray(listOf(JsonObject(request)))
            stored["issuedCredentialFormat"]?.takeUnless { it is JsonNull }?.let { format ->
                normalized["issuanceResults"] = buildJsonObject {
                    put(identifier.jsonPrimitive.content, buildJsonObject { put("issuedCredentialFormat", format) })
                }
            }
        }
        (normalized["issuanceResults"] as? JsonObject)?.let { results ->
            normalized["issuanceResults"] = JsonObject(results.mapValues { (_, result) ->
                JsonObject(result.jsonObject - "issuedAt")
            })
        }
        return JsonObject(normalized)
    }

    private val requestFields = setOf(
        "profileId", "credentialConfigurationId", "issuerKey", "credentialData", "mapping",
        "selectiveDisclosure", "idTokenClaimsMapping", "mDocNameSpacesDataMappingConfig",
        "authorizedTransactionDataTypes", "msoData", "x5Chain", "issuerDid", "credentialStatus", "expectedCredentialProofKeyJwks",
    )
}
