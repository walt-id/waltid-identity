package id.walt.crypto2

import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.Key
import id.walt.crypto2.keys.StoredKey
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.CryptographySoftwareKeyProvider
import id.walt.crypto2.serialization.StoredKeyCodec
import kotlinx.coroutines.runBlocking
import kotlin.system.measureNanoTime

object StoredKeyBenchmark {
    @JvmStatic
    fun main(args: Array<String>) = runBlocking {
        val runtime = CryptoRuntime(listOf(CryptographySoftwareKeyProvider()))
        val algorithm = SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256)
        val generated = runtime.generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId("benchmark-key"),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            ),
        )
        val encoded = StoredKeyCodec.encodeToByteArray(generated.storedKey)

        benchmark("encode stored record", DECODE_ITERATIONS) {
            StoredKeyCodec.encodeToByteArray(generated.storedKey).size
        }
        benchmark("decode stored record", DECODE_ITERATIONS) {
            StoredKeyCodec.decodeFromByteArray(encoded).hashCode()
        }
        val stored = StoredKeyCodec.decodeFromByteArray(encoded) as StoredKey.Software
        benchmark("restore provider key", RESTORE_ITERATIONS) {
            runtime.restore(stored).hashCode()
        }

        val message = "benchmark-message".encodeToByteArray()
        val coldKeys = List(COLD_SIGN_ITERATIONS) { runtime.restore(stored) }
        var coldKeyIndex = 0
        benchmark("first sign including materialization", COLD_SIGN_ITERATIONS) {
            coldKeys[coldKeyIndex++].capabilities.signer!!.sign(message, algorithm).first().toInt()
        }
        val restored: Key = runtime.restore(stored)
        restored.capabilities.signer!!.sign(message, algorithm)
        benchmark("cached sign", SIGN_ITERATIONS) {
            restored.capabilities.signer!!.sign(message, algorithm).first().toInt()
        }
    }

    private suspend fun benchmark(name: String, iterations: Int, block: suspend () -> Int) {
        var blackhole = 0
        val elapsed = measureNanoTime {
            repeat(iterations) {
                blackhole = blackhole xor block()
            }
        }
        val averageMicros = elapsed.toDouble() / iterations / NANOS_PER_MICROSECOND
        println("$name: ${"%.2f".format(averageMicros)} us/op ($iterations iterations, $blackhole)")
    }

    private const val DECODE_ITERATIONS = 10_000
    private const val RESTORE_ITERATIONS = 2_000
    private const val COLD_SIGN_ITERATIONS = 100
    private const val SIGN_ITERATIONS = 1_000
    private const val NANOS_PER_MICROSECOND = 1_000.0
}
