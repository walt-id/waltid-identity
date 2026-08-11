package id.walt.credentials.keyresolver.resolvers

import id.walt.certificate.x509.X509CertificateUtil
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.Base64Utils.decodeFromBase64
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.keys.Key
import id.walt.x509.CertificateDer
import id.walt.x509.crypto2PublicJwk
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.io.bytestring.ByteString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonPrimitive

object X5CKeyResolver : BaseKeyResolver {
    private val log = KotlinLogging.logger { }

    @Deprecated(
        "Use resolveJwkFromX5c for crypto2 key material",
        ReplaceWith("restoreKeyFromX5c(x5c,runtime)"),
    )
    suspend fun resolveKeyFromX5c(x5c: JsonArray): JWKKey {
        log.debug { "Resolving issuer key from x5c header" }
        if (x5c.isEmpty()) throw IllegalArgumentException("Certificate chain in 'x5c' must not be empty.")
        val certificateChainStrings = x5c.map { it.jsonPrimitive.content }
        val issuerCertificate = certificateChainStrings.first()
        val jwk = CertificateDer(issuerCertificate.decodeFromBase64()).crypto2PublicJwk()
        return JWKKey.importJWK(jwk.data.toByteArray().decodeToString()).getOrThrow()
    }

    suspend fun restoreKeyFromX5c(x5c: JsonArray, runtime: CryptoRuntime): Key {
        log.debug { "Resolving issuer key from x5c header" }
        if (x5c.isEmpty()) throw IllegalArgumentException("Certificate chain in 'x5c' must not be empty.")

        val certificateChainStrings = x5c.map { it.jsonPrimitive.content }
        val issuerCertificate = certificateChainStrings.first()
        val cert = X509CertificateUtil.parseCertificateDerEncoded(ByteString(issuerCertificate.decodeFromBase64()))
        return cert.data.subjectPublicKeyInfo.restore(runtime)
    }
}
