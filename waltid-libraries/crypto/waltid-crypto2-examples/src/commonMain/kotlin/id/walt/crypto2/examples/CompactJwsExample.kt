package id.walt.crypto2.examples

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.InvalidJwsSignatureException
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.CryptographySoftwareKeyProvider
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

data class CompactJwsResult(
    val payloadBytes: Int,
    val algorithm: JwsAlgorithm,
    val tamperedRejected: Boolean,
)

object CompactJwsExample {
    suspend fun run(output: ExampleOutput): CompactJwsResult {
        output("=== Compact JWS ===")
        val provider = CryptographySoftwareKeyProvider()
        val runtime = CryptoRuntime(softwareProviders = listOf(provider))
        output("1. Create provider=${provider.id.value} and generate P-256 SIGN|VERIFY key")
        val key = runtime.generateSoftwareKey(
            request = GenerateSoftwareKeyRequest(
                id = KeyId("compact-jws-key"),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            ),
        )

        // CompactJws binds the requested algorithm into the protected header and rejects conflicting headers.
        val payload = "compact JWS payload".encodeToByteArray()
        output("2. Sign payloadBytes=${payload.size} with ES256 and typ=example+jwt")
        val compactJws = CompactJws.sign(
            payload = payload,
            key = key,
            algorithm = JwsAlgorithm.ES256,
            protectedHeader = JsonObject(mapOf("typ" to JsonPrimitive("example+jwt"))),
        )
        output("   Safe result metadata: compactSegments=${compactJws.count { it == '.' } + 1}: $compactJws")

        // Verification pins ES256 instead of trusting the token's alg value.
        val verified = CompactJws.verify(
            compactJws = compactJws,
            key = key,
            expectedAlgorithm = JwsAlgorithm.ES256,
        )
        val parts = compactJws.split('.')
        val tampered = "${parts[0]}.dGFtcGVyZWQ.${parts[2]}"
        val tamperedRejected = try {
            CompactJws.verify(compactJws = tampered, key = key, expectedAlgorithm = JwsAlgorithm.ES256)
            false
        } catch (_: InvalidJwsSignatureException) {
            true
        }
        output("3. Verification result: algorithm=${verified.algorithm.identifier}, verified=true, tamperedRejected=$tamperedRejected")

        check(verified.payload.contentEquals(payload) && tamperedRejected)
        return CompactJwsResult(verified.payload.size, verified.algorithm, tamperedRejected)
    }
}
