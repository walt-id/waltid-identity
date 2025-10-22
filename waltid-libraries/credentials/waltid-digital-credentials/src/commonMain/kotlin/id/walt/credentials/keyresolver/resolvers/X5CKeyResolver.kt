package id.walt.credentials.keyresolver.resolvers

import id.walt.crypto.keys.jwk.JWKKey
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonPrimitive

object X5CKeyResolver : BaseKeyResolver {
    private val log = KotlinLogging.logger { }

    suspend fun resolveKeyFromX5c(x5c: JsonArray): JWKKey {
        log.debug { "Resolving issuer key from x5c header" }
        if (x5c.isEmpty()) throw IllegalArgumentException("Certificate chain in 'x5c' must not be empty.")

        val certificateChainStrings = x5c.map { it.jsonPrimitive.content }
        val issuerCertificate = certificateChainStrings.first()

        // TODO: certificate chain validation here.
        // (check signatures, expiration, and if trusting the root CA)

        return JWKKey.importDERorPEM(issuerCertificate).getOrThrow()
    }
}
