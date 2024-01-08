package id.walt.credentials.verification

import id.walt.credentials.verification.policies.JwtSignaturePolicy
import kotlinx.serialization.json.JsonObject

object Verifier {

    private val EMPTY_MAP = emptyMap<String, Any>()

    @Suppress("UNCHECKED_CAST" /* as? */)
    suspend fun verifyJws(jwt: String): Result<JsonObject> =
        JwtSignaturePolicy().verify(jwt, null, EMPTY_MAP) as? Result<JsonObject> ?: Result.failure(IllegalArgumentException("Could not get JSONObject from VC verification"))
}
