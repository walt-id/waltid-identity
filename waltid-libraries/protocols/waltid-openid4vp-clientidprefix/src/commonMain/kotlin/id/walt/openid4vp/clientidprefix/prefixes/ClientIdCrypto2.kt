package id.walt.openid4vp.clientidprefix.prefixes

import id.walt.certificate.x509.X509CertificateUtil
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.Jwk
import id.walt.crypto2.keys.*
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.crypto2.serialization.BinaryData
import kotlinx.io.bytestring.ByteString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

internal object ClientIdCrypto2 {
    val runtime = CryptoRuntime(defaultSoftwareKeyProviders())

    suspend fun verify(jws: String, key: Key) {
        val algorithm = CompactJws.decodeUnverified(jws).algorithm
        CompactJws.verify(jws, key, algorithm)
    }

    suspend fun keyFromCertificate(certificate: ByteArray): Key {
        val cert = X509CertificateUtil.parseCertificateDerEncoded(ByteString(certificate))
        val spki = cert.data.subjectPublicKeyInfo
        return spki.restore(runtime)
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
