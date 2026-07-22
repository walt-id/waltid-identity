package id.walt.openid4vp.clientidprefix.prefixes

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.Jwk
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.Key
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.toStoredSoftwareKey
import id.walt.crypto2.providers.cryptography.CryptographySoftwareKeyProvider
import id.walt.crypto2.serialization.BinaryData
import id.walt.x509.CertificateDer
import id.walt.x509.crypto2PublicJwk
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

internal object ClientIdCrypto2 {
    private val runtime = CryptoRuntime(listOf(CryptographySoftwareKeyProvider()))

    suspend fun verify(jws: String, key: Key) {
        val algorithm = CompactJws.decodeUnverified(jws).algorithm
        CompactJws.verify(jws, key, algorithm)
    }

    suspend fun keyFromCertificate(certificate: ByteArray): Key {
        val jwk = CertificateDer(certificate).crypto2PublicJwk()
        return runtime.restore(
            jwk.toStoredSoftwareKey(KeyId(Jwk.sha256Thumbprint(jwk)), setOf(KeyUsage.VERIFY))
        )
    }

    suspend fun keyFromJwk(jwk: JsonObject, fallbackId: String): Key {
        require(!Jwk.containsPrivateMaterial(jwk)) { "Verification JWK must not contain private material" }
        val encoded = EncodedKey.Jwk(
            BinaryData(Json.encodeToString(jwk).encodeToByteArray()),
            privateMaterial = false,
        )
        val keyId = Jwk.metadata(encoded).keyId ?: fallbackId
        return runtime.restore(encoded.toStoredSoftwareKey(KeyId(keyId), setOf(KeyUsage.VERIFY)))
    }
}
