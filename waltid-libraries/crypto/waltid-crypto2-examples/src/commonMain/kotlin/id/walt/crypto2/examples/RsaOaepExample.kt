package id.walt.crypto2.examples

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.algorithms.AsymmetricEncryptionAlgorithm
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.keys.AsymmetricCiphertext
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.CryptographySoftwareKeyProvider

data class RsaOaepResult(
    val ciphertextBytes: Int,
    val roundTrip: Boolean,
    val oversizedPlaintextRejected: Boolean,
)

object RsaOaepExample {
    suspend fun run(output: ExampleOutput): RsaOaepResult {
        output("=== RSA OAEP encrypt/decrypt ===")
        val provider = CryptographySoftwareKeyProvider()
        val runtime = CryptoRuntime(softwareProviders = listOf(provider))
        output("1. Create provider=${provider.id.value} and generate RSA-2048 ENCRYPT|DECRYPT key")
        val key = runtime.generateSoftwareKey(
            request = GenerateSoftwareKeyRequest(
                id = KeyId("rsa-oaep-key"),
                spec = KeySpec.Rsa(bits = 2048),
                usages = setOf(KeyUsage.ENCRYPT, KeyUsage.DECRYPT),
            ),
        )

        // OAEP with SHA-256 avoids legacy PKCS1 v1.5 padding and fixes the plaintext limit for this key size.
        val algorithm = AsymmetricEncryptionAlgorithm.RsaOaep(digest = DigestAlgorithm.SHA_256)
        val plaintext = "RSA OAEP payload".encodeToByteArray()
        output("2. Encrypt plaintextBytes=${plaintext.size} with RSA-OAEP-256")
        val ciphertext = requireNotNull(key.capabilities.encryptor).encrypt(
            plaintext = plaintext,
            algorithm = algorithm,
            associatedData = null,
        )
        val ciphertextBytes = (ciphertext as AsymmetricCiphertext.Raw).data.toByteArray().size
        output("   Safe result metadata: ciphertextBytes=$ciphertextBytes")

        val decrypted = requireNotNull(key.capabilities.decryptor).decrypt(ciphertext = ciphertext, associatedData = null)
        val oversizedPlaintextRejected = try {
            requireNotNull(key.capabilities.encryptor).encrypt(
                plaintext = ByteArray(191),
                algorithm = algorithm,
                associatedData = null,
            )
            false
        } catch (_: Throwable) {
            true
        }
        val roundTrip = plaintext.contentEquals(decrypted)
        output("3. Decryption result: roundTrip=$roundTrip, oversizedPlaintextRejected=$oversizedPlaintextRejected")

        check(roundTrip && oversizedPlaintextRejected)
        return RsaOaepResult(ciphertextBytes, roundTrip, oversizedPlaintextRejected)
    }
}
