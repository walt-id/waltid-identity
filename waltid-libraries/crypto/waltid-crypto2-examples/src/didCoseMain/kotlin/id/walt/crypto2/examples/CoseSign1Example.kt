package id.walt.crypto2.examples

import id.walt.cose.Cose
import id.walt.cose.CoseHeaders
import id.walt.cose.CoseSign1
import id.walt.cose.createAndSign
import id.walt.cose.verify
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.CryptographySoftwareKeyProvider

data class CoseSign1Result(
    val taggedBytes: Int,
    val signatureBytes: Int,
    val verified: Boolean,
    val tamperedRejected: Boolean,
)

object CoseSign1Example {
    suspend fun run(output: ExampleOutput): CoseSign1Result {
        output("=== COSE Sign1 ===")
        val provider = CryptographySoftwareKeyProvider()
        val runtime = CryptoRuntime(softwareProviders = listOf(provider))
        output("1. Create provider=${provider.id.value} and generate P-256 SIGN|VERIFY key")
        val key = runtime.generateSoftwareKey(
            request = GenerateSoftwareKeyRequest(
                id = KeyId("cose-sign1-key"),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            ),
        )

        // Protecting the ES256 header cryptographically prevents an attacker from replacing the algorithm.
        val payload = "COSE Sign1 payload".encodeToByteArray()
        output("2. Sign attached payloadBytes=${payload.size} with protected alg=ES256")
        val sign1 = CoseSign1.createAndSign(
            protectedHeaders = CoseHeaders(algorithm = Cose.Algorithm.ES256),
            payload = payload,
            key = key,
        )
        val tagged = sign1.toTagged()
        output("   Safe result metadata: taggedBytes=${tagged.size}, signatureBytes=${sign1.signature.size}")

        // Parsing the tagged message before verification exercises the transport representation.
        val decoded = CoseSign1.fromTagged(cborBytes = tagged)
        val verified = decoded.verify(key = key, expectedAlgorithm = Cose.Algorithm.ES256)
        val tamperedSignature = decoded.signature.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }
        val tamperedRejected = !decoded.copy(signature = tamperedSignature).verify(
            key = key,
            expectedAlgorithm = Cose.Algorithm.ES256,
        )
        output("3. Verification result: verified=$verified, tamperedRejected=$tamperedRejected")

        check(verified && tamperedRejected)
        return CoseSign1Result(tagged.size, sign1.signature.size, verified, tamperedRejected)
    }
}
