package id.walt.crypto2.examples

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.JWK_ALGORITHM_METADATA_KEY
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.SoftwareKeyProvider

data class Secp256k1Result(
    val providerId: String,
    val algorithm: JwsAlgorithm,
    val verified: Boolean,
)

suspend fun runSecp256k1Example(
    provider: SoftwareKeyProvider,
    providerDescription: String,
    output: ExampleOutput,
): Secp256k1Result {
    output("=== Opt-in secp256k1 ES256K ===")
    output("1. Select explicit $providerDescription provider=${provider.id.value}")
    val runtime = CryptoRuntime(softwareProviders = listOf(provider))

    // secp256k1 is deliberately absent from the portable provider and must be requested from a known implementation.
    val key = runtime.generateSoftwareKey(
        request = GenerateSoftwareKeyRequest(
            id = KeyId("secp256k1-key"),
            spec = KeySpec.Ec(EcCurve.SECP256K1),
            usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            metadata = mapOf(JWK_ALGORITHM_METADATA_KEY to JwsAlgorithm.ES256K.identifier),
        ),
    )
    output("2. Generated keyId=${key.id.value}, spec=secp256k1, usages=SIGN|VERIFY, alg=ES256K")

    // The JWK alg restriction and expectedAlgorithm pin every operation to ES256K.
    val payload = "ES256K payload".encodeToByteArray()
    val compactJws = CompactJws.sign(payload = payload, key = key, algorithm = JwsAlgorithm.ES256K)
    output("3. Sign compact JWS: payloadBytes=${payload.size}, compactSegments=3")
    val verified = CompactJws.verify(
        compactJws = compactJws,
        key = key,
        expectedAlgorithm = JwsAlgorithm.ES256K,
    )
    val valid = verified.payload.contentEquals(payload)
    output("4. Verification result: algorithm=${verified.algorithm.identifier}, verified=$valid")

    check(valid)
    return Secp256k1Result(provider.id.value, verified.algorithm, valid)
}
