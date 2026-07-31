package id.walt.rpcert.issuance

import id.walt.crypto.keys.Key
import id.walt.rpcert.models.RelyingPartyRegistrationCertificate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

object RelyingPartyRegistrationCertificateIssuer {
    const val JWT_TYPE = "rc-wrp+jwt"

    private val json = Json {
        encodeDefaults = false
        explicitNulls = false
    }

    suspend fun issue(
        key: Key,
        x5c: List<String>,
        payload: RelyingPartyRegistrationCertificate,
    ): String {
        require(key.hasPrivateKey) { "Signing key must be a private key" }
        require(x5c.isNotEmpty()) { "x5c certificate chain must not be empty" }

        val headers = mapOf(
            "typ" to JsonPrimitive(JWT_TYPE),
            "x5c" to JsonArray(x5c.map { JsonPrimitive(it) }),
        )

        val payloadBytes = json.encodeToString(payload).encodeToByteArray()
        return key.signJws(payloadBytes, headers)
    }

}