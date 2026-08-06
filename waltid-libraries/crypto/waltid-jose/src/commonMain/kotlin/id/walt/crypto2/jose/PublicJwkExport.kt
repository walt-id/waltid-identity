package id.walt.crypto2.jose

import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.Key
import id.walt.crypto2.keys.toPublicJwk
import kotlinx.serialization.json.JsonObject

/**
 * Exports the public part of this key as a JWK.
 *
 * @throws IllegalArgumentException when the key does not support public-key export (for example a
 *   remote key whose provider withholds the public key).
 */
suspend fun Key.exportPublicJwk(): EncodedKey.Jwk {
    val exporter = requireNotNull(capabilities.publicKeyExporter) {
        "Key '${id.value}' does not support public-key export"
    }
    return exporter.exportPublicKey().toPublicJwk(spec)
}

/** Exports the public part of this key as a parsed JWK object. */
suspend fun Key.exportPublicJwkObject(): JsonObject = Jwk.parse(exportPublicJwk())
