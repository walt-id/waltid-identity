package id.walt.crypto2.providers.cryptography

import dev.whyoleg.cryptography.CryptographyProvider
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.algorithms.AsymmetricEncryptionAlgorithm
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.EcdsaSignatureEncoding
import id.walt.crypto2.algorithms.KeyAgreementAlgorithm
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.EdwardsCurve
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyEncodingFormat
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.MontgomeryCurve
import id.walt.crypto2.keys.SoftwareKey
import id.walt.crypto2.providers.CryptoOperation
import id.walt.crypto2.providers.CryptoRequirement
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * Prints the local (software-key) algorithm support matrix of the platform it runs on, and asserts the one
 * invariant that must hold everywhere: whatever a key advertises through [id.walt.crypto2.keys.KeyCapabilities]
 * has to actually work. An advertised-but-failing algorithm is a capability-reporting bug - callers pick
 * algorithms from those sets, so a wrong entry surfaces as a runtime failure in production instead of a
 * negotiation failure.
 *
 * Run it per platform to compare, e.g.
 * `./gradlew :waltid-libraries:crypto:waltid-crypto2:jvmTest :waltid-libraries:crypto:waltid-crypto2:jsNodeTest
 * :waltid-libraries:crypto:waltid-crypto2:linuxX64Test --tests '*SoftwareKeyAlgorithmMatrixTest*'`
 */
class SoftwareKeyAlgorithmMatrixTest {
    private val providers = defaultSoftwareKeyProviders()
    private val runtime = CryptoRuntime(providers)

    private enum class Support {
        /** Advertised by the key and the round trip succeeded. */
        OK,

        /** Advertised by the key but the operation failed - always a bug. */
        BROKEN,

        /** Not advertised: the capability profile or the cryptography-kotlin provider does not offer it. */
        UNAVAILABLE,

        /** The key specification itself cannot be generated on this platform. */
        NO_KEY,
    }

    @Test
    fun `software key algorithm support matrix`() = runTest(timeout = 10.minutes) {
        val rows = mutableListOf<Triple<String, String, Support>>()
        // One key per specification and usage set: probing every algorithm with a fresh key would make RSA key
        // generation dominate the runtime, which is slow enough in browsers to trip their test timeout.
        val keys = mutableMapOf<Pair<KeySpec, Set<KeyUsage>>, SoftwareKey>()

        suspend fun probe(
            spec: KeySpec,
            label: String,
            usages: Set<KeyUsage>,
            advertises: (SoftwareKey) -> Boolean,
            block: suspend (SoftwareKey) -> Unit,
        ) {
            if (providers.none { it.supports(generationRequirement(spec, usages)) }) {
                rows += Triple(spec.label(), label, Support.NO_KEY)
                return
            }
            val key = try {
                keys.getOrPut(spec to usages) { generate(spec, usages) }
            } catch (cause: Throwable) {
                rows += Triple(spec.label(), label, Support.BROKEN)
                return
            }
            if (!advertises(key)) {
                rows += Triple(spec.label(), label, Support.UNAVAILABLE)
                return
            }
            val result = try {
                block(key)
                Support.OK
            } catch (cause: Throwable) {
                Support.BROKEN
            }
            rows += Triple(spec.label(), label, result)
        }

        signingSpecs.forEach { spec ->
            spec.signatureAlgorithms().forEach { algorithm ->
                probe(
                    spec = spec,
                    label = algorithm.label(),
                    usages = signUsages,
                    advertises = { it.capabilities.supportsSignatureAlgorithm(algorithm) },
                ) { key ->
                    val signature = requireNotNull(key.capabilities.signer).sign(message, algorithm)
                    check(requireNotNull(key.capabilities.verifier).verify(message, signature, algorithm)) {
                        "signature did not verify"
                    }
                    check(!key.capabilities.verifier!!.verify(tamperedMessage, signature, algorithm)) {
                        "signature verified over the wrong message"
                    }
                }
            }
        }

        oaepDigests.forEach { digest ->
            val algorithm = AsymmetricEncryptionAlgorithm.RsaOaep(digest)
            probe(
                spec = rsaSweepSpec,
                label = algorithm.label(),
                usages = encryptUsages,
                advertises = { it.capabilities.supportsEncryptionAlgorithm(algorithm) },
            ) { key ->
                val ciphertext = requireNotNull(key.capabilities.encryptor).encrypt(message, algorithm, null)
                val plaintext = requireNotNull(key.capabilities.decryptor).decrypt(ciphertext, null)
                check(plaintext.contentEquals(message)) { "OAEP round trip returned different plaintext" }
            }
        }

        agreementSpecs.forEach { (spec, algorithm) ->
            probe(
                spec = spec,
                label = algorithm.label(),
                usages = setOf(KeyUsage.KEY_AGREEMENT),
                advertises = { it.capabilities.supportsKeyAgreementAlgorithm(algorithm) },
            ) { key ->
                val peer = generate(spec, setOf(KeyUsage.KEY_AGREEMENT))
                val ours = requireNotNull(key.capabilities.keyAgreement).generateSharedSecret(
                    requireNotNull(peer.capabilities.publicKeyExporter).exportPublicKey(),
                    algorithm,
                )
                val theirs = requireNotNull(peer.capabilities.keyAgreement).generateSharedSecret(
                    requireNotNull(key.capabilities.publicKeyExporter).exportPublicKey(),
                    algorithm,
                )
                check(ours.toByteArray().contentEquals(theirs.toByteArray())) { "shared secrets differ" }
            }
        }

        println(rows.render())

        val broken = rows.filter { it.third == Support.BROKEN }
        assertTrue(broken.isEmpty(), "Advertised algorithms that do not work: $broken")
    }

    private fun List<Triple<String, String, Support>>.render(): String {
        val rows = this
        return buildString {
            appendLine()
            appendLine("Software-key algorithm support")
            appendLine("  cryptography-kotlin default provider: ${CryptographyProvider.Default.name}")
            appendLine("  registered providers: " + providers.joinToString { it.id.value })
            val keyWidth = maxOf(rows.maxOfOrNull { it.first.length } ?: 0, 3)
            val algorithmWidth = maxOf(rows.maxOfOrNull { it.second.length } ?: 0, 9)
            rows.groupBy { it.first }.forEach { (key, entries) ->
                entries.forEach { (_, algorithm, support) ->
                    appendLine("  ${key.padEnd(keyWidth)}  ${algorithm.padEnd(algorithmWidth)}  $support")
                }
            }
            val counts = rows.groupingBy { it.third }.eachCount()
            appendLine("  summary: " + Support.entries.joinToString { "$it=${counts[it] ?: 0}" })
        }
    }

    private fun generationRequirement(spec: KeySpec, usages: Set<KeyUsage>) = CryptoRequirement(
        operation = CryptoOperation.GENERATE_KEY,
        spec = spec,
        usages = usages,
        keyEncoding = KeyEncodingFormat.JWK,
    )

    private suspend fun generate(spec: KeySpec, usages: Set<KeyUsage>): SoftwareKey = runtime.generateSoftwareKey(
        GenerateSoftwareKeyRequest(id = KeyId("matrix-key"), spec = spec, usages = usages),
    )

    private fun KeySpec.signatureAlgorithms(): List<SignatureAlgorithm> = when (this) {
        is KeySpec.Ec -> digests.flatMap { digest ->
            EcdsaSignatureEncoding.entries.map { SignatureAlgorithm.Ecdsa(digest, it) }
        }
        is KeySpec.Edwards -> listOf(SignatureAlgorithm.EdDsa)
        is KeySpec.Rsa -> digests.flatMap { digest ->
            listOf(
                SignatureAlgorithm.RsaPkcs1(digest),
                SignatureAlgorithm.RsaPss(digest),
                SignatureAlgorithm.RsaPss(digest, saltLengthBytes = digest.sizeBytes),
            )
        }
        else -> emptyList()
    }

    private fun KeySpec.label(): String = when (this) {
        is KeySpec.Ec -> curve.name
        is KeySpec.Edwards -> curve.name
        is KeySpec.Montgomery -> curve.name
        is KeySpec.Rsa -> "RSA-$bits"
        is KeySpec.Symmetric -> "${family.name}-$bits"
        is KeySpec.Custom -> family
    }

    private fun SignatureAlgorithm.label(): String = when (this) {
        is SignatureAlgorithm.Ecdsa -> "ECDSA/${digest.name}/${encoding.name}"
        SignatureAlgorithm.EdDsa -> "EdDSA"
        is SignatureAlgorithm.RsaPkcs1 -> "RSASSA-PKCS1/${digest.name}"
        is SignatureAlgorithm.RsaPss -> "RSASSA-PSS/${digest.name}/salt=${saltLengthBytes ?: "default"}"
        is SignatureAlgorithm.Custom -> id
    }

    private fun AsymmetricEncryptionAlgorithm.label(): String = when (this) {
        is AsymmetricEncryptionAlgorithm.RsaOaep -> "RSA-OAEP/${digest.name}"
        AsymmetricEncryptionAlgorithm.RsaPkcs1 -> "RSAES-PKCS1"
        is AsymmetricEncryptionAlgorithm.Custom -> id
    }

    private fun KeyAgreementAlgorithm.label(): String = when (this) {
        KeyAgreementAlgorithm.Ecdh -> "ECDH"
        KeyAgreementAlgorithm.Xdh -> "XDH"
        is KeyAgreementAlgorithm.Named -> id
        is KeyAgreementAlgorithm.Custom -> id
    }

    private companion object {
        val message = "algorithm matrix probe".encodeToByteArray()
        val tamperedMessage = "algorithm matrix prob3".encodeToByteArray()
        val signUsages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY)
        val encryptUsages = setOf(KeyUsage.ENCRYPT, KeyUsage.DECRYPT)
        val digests: List<DigestAlgorithm> = listOf(
            DigestAlgorithm.SHA_1,
            DigestAlgorithm.SHA_256,
            DigestAlgorithm.SHA_384,
            DigestAlgorithm.SHA_512,
            DigestAlgorithm.SHA3_256,
        )
        val oaepDigests: List<DigestAlgorithm> = listOf(
            DigestAlgorithm.SHA_1,
            DigestAlgorithm.SHA_256,
            DigestAlgorithm.SHA_384,
            DigestAlgorithm.SHA_512,
        )
        /**
         * One representative RSA size on purpose. Generating RSA-3072/4096 here dominated the runtime and made the
         * Karma browser runs flake against their 2s Mocha budget; key *sizes* are covered by
         * CryptographySoftwareKeyProviderTest, whereas this test is about algorithm support.
         */
        val rsaSweepSpec = KeySpec.Rsa(2048)
        val signingSpecs: List<KeySpec> = listOf(
            KeySpec.Ec(EcCurve.P256),
            KeySpec.Ec(EcCurve.P384),
            KeySpec.Ec(EcCurve.P521),
            KeySpec.Ec(EcCurve.SECP256K1),
            KeySpec.Edwards(EdwardsCurve.ED25519),
            KeySpec.Edwards(EdwardsCurve.ED448),
            rsaSweepSpec,
        )
        val agreementSpecs: List<Pair<KeySpec, KeyAgreementAlgorithm>> = listOf(
            KeySpec.Ec(EcCurve.P256) to KeyAgreementAlgorithm.Ecdh,
            KeySpec.Ec(EcCurve.P384) to KeyAgreementAlgorithm.Ecdh,
            KeySpec.Ec(EcCurve.P521) to KeyAgreementAlgorithm.Ecdh,
            KeySpec.Montgomery(MontgomeryCurve.X25519) to KeyAgreementAlgorithm.Xdh,
            KeySpec.Montgomery(MontgomeryCurve.X448) to KeyAgreementAlgorithm.Xdh,
        )
    }
}
