package id.walt.rpcert.issuance

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.Base64Utils.decodeFromBase64
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

    /**
     * Issue a signed Wallet-Relying Party Registration Certificate (`rc-wrp+jwt`).
     *
     * @param key The signing (private) key; must match the public key of the leaf `x5c` certificate.
     * @param x5c The certificate chain for the JWT `x5c` header: base64 (not base64url) encoded
     * DER certificates, leaf first.
     * @param payload The registration certificate content.
     */
    suspend fun issue(
        key: Key,
        x5c: List<String>,
        payload: RelyingPartyRegistrationCertificate,
    ): String {
        require(key.hasPrivateKey) { "Signing key must be a private key" }
        require(x5c.isNotEmpty()) { "x5c certificate chain must not be empty" }

        val leafKey = JWKKey.importFromDerCertificate(x5c.first().decodeFromBase64())
            .getOrElse { throw IllegalArgumentException("Leaf x5c certificate is not a valid X.509 certificate", it) }
        require(leafKey.getThumbprint() == key.getPublicKey().getThumbprint()) {
            "Signing key does not match the public key of the leaf x5c certificate"
        }

        val headers = mapOf(
            "typ" to JsonPrimitive(JWT_TYPE),
            "x5c" to JsonArray(x5c.map { JsonPrimitive(it) }),
        )

        val payloadBytes = json.encodeToString(payload).encodeToByteArray()
        return key.signJws(payloadBytes, headers)
    }

}