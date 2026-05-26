package id.walt.credentials.keyresolver.resolvers

import id.walt.credentials.trustedauthorities.X5CChainValidatorHelper
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.Base64Utils.decodeFromBase64Url
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

        // (check signatures, expiration, and if trusting the root CA)

        // Validate the certificate chain when more than one cert is present.
        if (certificateChainStrings.size > 1) {
            X5CChainValidatorHelper.verifyChain(certificateChainStrings.map { it.decodeFromBase64Url() })
        }

        return JWKKey.importDERorPEM(issuerCertificate).getOrThrow()
    }
}
