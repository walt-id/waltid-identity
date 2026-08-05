package id.walt.examples

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.EdwardsCurve
import id.walt.crypto2.keys.Key
import id.walt.crypto2.keys.KeyEncodingFormat
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.providers.CryptoOperation
import id.walt.crypto2.providers.CryptoRequirement
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders

/**
 * Signing keys for the usage examples, produced exactly the way an application would: software keys from the
 * platform's default provider set. Every example takes a [Key], so the same scenario runs unchanged against a
 * KMS, an HSM, or a platform keystore key.
 */
object ExampleKeys {
    private val providers = defaultSoftwareKeyProviders()
    val runtime = CryptoRuntime(providers)

    /** The key specifications the examples cover, with the JWS algorithm each one is used with. */
    val signingSpecs: List<Pair<KeySpec, JwsAlgorithm>> = listOf(
        KeySpec.Ec(EcCurve.P256) to JwsAlgorithm.ES256,
        KeySpec.Ec(EcCurve.P384) to JwsAlgorithm.ES384,
        KeySpec.Ec(EcCurve.P521) to JwsAlgorithm.ES512,
        KeySpec.Ec(EcCurve.SECP256K1) to JwsAlgorithm.ES256K,
        KeySpec.Edwards(EdwardsCurve.ED25519) to JwsAlgorithm.EDDSA,
        KeySpec.Rsa(2048) to JwsAlgorithm.RS256,
    )

    /** Whether this platform can produce a signing key for [spec] at all - see the crypto2 support matrix. */
    fun canSign(spec: KeySpec): Boolean = providers.any {
        it.supports(
            CryptoRequirement(
                operation = CryptoOperation.GENERATE_KEY,
                spec = spec,
                usages = signingUsages,
                keyEncoding = KeyEncodingFormat.JWK,
            )
        )
    }

    suspend fun signingKey(spec: KeySpec, id: String = "example-issuer"): Key = runtime.generateSoftwareKey(
        GenerateSoftwareKeyRequest(id = KeyId(id), spec = spec, usages = signingUsages),
    )

    fun label(spec: KeySpec): String = when (spec) {
        is KeySpec.Ec -> spec.curve.name
        is KeySpec.Edwards -> spec.curve.name
        is KeySpec.Montgomery -> spec.curve.name
        is KeySpec.Rsa -> "RSA-${spec.bits}"
        is KeySpec.Symmetric -> "${spec.family.name}-${spec.bits}"
        is KeySpec.Custom -> spec.family
    }

    private val signingUsages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY)
}
