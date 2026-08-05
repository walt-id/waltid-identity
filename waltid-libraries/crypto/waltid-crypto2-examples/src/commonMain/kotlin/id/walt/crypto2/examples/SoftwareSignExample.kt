package id.walt.crypto2.examples

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.CryptographySoftwareKeyProvider
import kotlinx.serialization.json.Json

data class SoftwareSignResult(
    val keyId: String,
    val signatureBytes: Int,
    val verified: Boolean,
    val tamperedRejected: Boolean,
)

object SoftwareSignExample {
    suspend fun run(output: ExampleOutput): SoftwareSignResult {
        output("=== Software key generation + sign/verify ===")
        val provider = CryptographySoftwareKeyProvider()
        output("1. Create CryptoRuntime with provider=${provider.id.value}")
        val runtime = CryptoRuntime(softwareProviders = listOf(provider))

        // P-256 with explicit SIGN and VERIFY usages is the portable, least-privilege choice for this operation.
        val key = runtime.generateSoftwareKey(
            request = GenerateSoftwareKeyRequest(
                id = KeyId("software-signing-key"),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            ),
        )
        output("2. Generated keyId=${key.id.value}, spec=P-256, usages=SIGN|VERIFY")

        // JOSE-style ECDSA signatures use SHA-256 and fixed-width IEEE P1363 encoding.
        val algorithm = SignatureAlgorithm.Ecdsa(digest = DigestAlgorithm.SHA_256)
        val message = "crypto2 software signing".encodeToByteArray()
        output("3. Sign messageBytes=${message.size} with ECDSA-SHA-256/P1363: ${message.toHexString()}")
        val signature = requireNotNull(key.capabilities.signer).sign(data = message, algorithm = algorithm)
        output("   Safe result metadata: signatureBytes=${signature.size}: ${signature.toHexString()}")

        val verifier = requireNotNull(key.capabilities.verifier)
        val verified = verifier.verify(data = message, signature = signature, algorithm = algorithm)
        val tamperedRejected = !verifier.verify(
            data = "tampered".encodeToByteArray(),
            signature = signature,
            algorithm = algorithm,
        )
        output("4. Verification result: verified=$verified, tamperedRejected=$tamperedRejected")

        check(verified && tamperedRejected)
        return SoftwareSignResult(key.id.value, signature.size, verified, tamperedRejected)
    }
}
