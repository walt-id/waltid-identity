package id.walt.verifier2.handlers.authrequest

import id.walt.crypto.keys.Key
import id.walt.crypto2.jose.Jwk
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.Key as Crypto2Key

/**
 * Builds the JAR `kid` header for signed authorization requests.
 *
 * For `decentralized_identifier:did:key:…`, the verification method id is
 * `did:key:<multibase>#<multibase>` (DID Key method). Using the raw KMS key id
 * (e.g. an Azure Key Vault URL) produces a DID URL wallets reject.
 *
 * For other DIDs, prefer a stable public-key identifier and avoid absolute URLs
 * (Azure embeds vault URLs as JWK `kid`) so the fragment remains a valid DID URL.
 */
internal object Verifier2RequestObjectKid {

    suspend fun forClient(clientId: String?, key: Key): String =
        forClient(clientId) { fragmentKeyId(key) }

    suspend fun forClient(clientId: String?, key: Crypto2Key): String =
        forClient(clientId) { fragmentKeyId(key) }

    private suspend fun forClient(clientId: String?, fragment: suspend () -> String): String {
        val prefix = "decentralized_identifier:"
        val did = clientId
            ?.takeIf { it.startsWith(prefix) }
            ?.substringAfter(prefix)
            ?.takeIf { it.isNotBlank() }
            ?: return fragment()

        if (did.startsWith("did:key:")) {
            val identifier = did.removePrefix("did:key:")
            return "$did#$identifier"
        }

        return "$did#${fragment()}"
    }

    private suspend fun fragmentKeyId(key: Key): String {
        val publicKey = key.getPublicKey()
        val keyId = publicKey.getKeyId()
        return if (keyId.startsWith("http://") || keyId.startsWith("https://")) {
            publicKey.getThumbprint()
        } else {
            keyId
        }
    }

    private suspend fun fragmentKeyId(key: Crypto2Key): String {
        val keyId = key.id.value
        return if (keyId.startsWith("http://") || keyId.startsWith("https://")) {
            val publicJwk = key.capabilities.publicKeyExporter
                ?.exportPublicKey() as? EncodedKey.Jwk
            if (publicJwk != null) Jwk.sha256Thumbprint(publicJwk) else keyId
        } else {
            keyId
        }
    }
}
