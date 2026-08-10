package id.walt.credentials.keyresolver.resolvers

import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto.utils.Base64Utils.decodeFromBase64
import id.walt.x509.CertificateDer
import id.walt.x509.crypto2PublicJwk
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonPrimitive

object X5CKeyResolver : BaseKeyResolver {
    private val log = KotlinLogging.logger { }

    @Deprecated(
        "Use resolveJwkFromX5c for crypto2 key material",
        ReplaceWith("resolveJwkFromX5c(x5c)"),
    )
    suspend fun resolveKeyFromX5c(x5c: JsonArray): JWKKey {
        val jwk = resolveJwkFromX5c(x5c)
        return JWKKey.importJWK(jwk.data.toByteArray().decodeToString()).getOrThrow()
    }

    fun resolveJwkFromX5c(x5c: JsonArray): EncodedKey.Jwk {
        log.debug { "Resolving issuer key from x5c header" }
        if (x5c.isEmpty()) throw IllegalArgumentException("Certificate chain in 'x5c' must not be empty.")

        val certificateChainStrings = x5c.map { it.jsonPrimitive.content }
        val issuerCertificate = certificateChainStrings.first()
        return CertificateDer(issuerCertificate.decodeFromBase64()).crypto2PublicJwk()
    }
}
