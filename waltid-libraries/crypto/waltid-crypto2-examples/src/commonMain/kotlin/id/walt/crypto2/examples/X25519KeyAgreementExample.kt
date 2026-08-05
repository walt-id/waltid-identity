package id.walt.crypto2.examples

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.algorithms.KeyAgreementAlgorithm
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.MontgomeryCurve
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.CryptographySoftwareKeyProvider

data class X25519KeyAgreementResult(
    val sharedSecretBytes: Int,
    val secretsMatch: Boolean,
)

object X25519KeyAgreementExample {
    suspend fun run(output: ExampleOutput): X25519KeyAgreementResult {
        output("=== X25519 key agreement ===")
        val provider = CryptographySoftwareKeyProvider()
        val runtime = CryptoRuntime(softwareProviders = listOf(provider))
        output("1. Create provider=${provider.id.value} for X25519")

        // KEY_AGREEMENT-only usages prevent these keys from being misused for signing or encryption.
        val alice = runtime.generateSoftwareKey(
            request = GenerateSoftwareKeyRequest(
                id = KeyId("alice-x25519"),
                spec = KeySpec.Montgomery(MontgomeryCurve.X25519),
                usages = setOf(KeyUsage.KEY_AGREEMENT),
            ),
        )
        val bob = runtime.generateSoftwareKey(
            request = GenerateSoftwareKeyRequest(
                id = KeyId("bob-x25519"),
                spec = KeySpec.Montgomery(MontgomeryCurve.X25519),
                usages = setOf(KeyUsage.KEY_AGREEMENT),
            ),
        )
        output("2. Generate alice and bob keys: spec=X25519, usages=KEY_AGREEMENT")

        // Each side exports only its public key and combines it with its own private key.
        val aliceSecret = requireNotNull(alice.capabilities.keyAgreement).generateSharedSecret(
            peerPublicKey = requireNotNull(bob.capabilities.publicKeyExporter).exportPublicKey(),
            algorithm = KeyAgreementAlgorithm.Xdh,
        )
        val bobSecret = requireNotNull(bob.capabilities.keyAgreement).generateSharedSecret(
            peerPublicKey = requireNotNull(alice.capabilities.publicKeyExporter).exportPublicKey(),
            algorithm = KeyAgreementAlgorithm.Xdh,
        )
        val secretsMatch = aliceSecret == bobSecret
        output("3. Agreement result: sharedSecretBytes=${aliceSecret.toByteArray().size}, secretsMatch=$secretsMatch")
        output("   Shared secret material is intentionally not printed")

        check(secretsMatch)
        return X25519KeyAgreementResult(aliceSecret.toByteArray().size, secretsMatch)
    }
}
