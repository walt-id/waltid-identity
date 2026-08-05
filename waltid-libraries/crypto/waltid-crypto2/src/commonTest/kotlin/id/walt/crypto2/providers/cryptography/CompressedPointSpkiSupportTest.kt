package id.walt.crypto2.providers.cryptography

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.EC
import dev.whyoleg.cryptography.algorithms.ECDSA
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.toPublicJwk
import id.walt.crypto2.serialization.BinaryData
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Answers one question per platform: does this platform's SPKI decoder accept a **compressed** EC point?
 *
 * It decides how `did:key` can be resolved without the crypto1 detour. A did:key identifier carries the EC public
 * key as a compressed SEC1 point, so if wrapping those bytes in SPKI and letting the platform decode works
 * everywhere, no elliptic-curve point decompression has to be written by hand. Where it does not work, the raw
 * point has to be decompressed in common code (or by Signum) before a key can be built.
 *
 * Both fixtures are the same P-256 key, exported by OpenSSL in both point formats.
 */
class CompressedPointSpkiSupportTest {

    @Test
    fun `report whether compressed EC points can be imported from SPKI`() = runTest {
        val spec = KeySpec.Ec(EcCurve.P256)
        val uncompressed = decodeJwk(UNCOMPRESSED_P256_SPKI, spec)
        val compressed = decodeJwk(COMPRESSED_P256_SPKI, spec)
        // The provider's own SEC1 point decoders, which is what a did:key resolver would rather use than SPKI.
        val rawUncompressed = decodeRawPoint(UNCOMPRESSED_P256_POINT, EC.PublicKey.Format.RAW)
        val rawCompressed = decodeRawPoint(COMPRESSED_P256_POINT, EC.PublicKey.Format.RAW.Compressed)

        println(
            """
            |
            |Compressed EC point support - cryptography-kotlin provider: ${CryptographyProvider.Default.name}
            |  SPKI, uncompressed point (0x04): ${uncompressed.describe()}
            |  SPKI, compressed point (0x02/03): ${compressed.describe()}
            |  RAW point, uncompressed (0x04):   ${rawUncompressed.describe()}
            |  RAW point, compressed (0x02/03):  ${rawCompressed.describe()}
            """.trimMargin()
        )

        // The uncompressed form is the baseline: if that fails, the fixture or the decoder is broken, not the
        // compressed-point question this test is about.
        assertTrue(uncompressed.isSuccess, "uncompressed SPKI must decode: ${uncompressed.exceptionOrNull()}")

        // Same key, so a successful compressed import has to produce the same public coordinates.
        compressed.getOrNull()?.let { fromCompressed ->
            assertTrue(
                fromCompressed == uncompressed.getOrThrow(),
                "compressed and uncompressed SPKI decoded to different keys",
            )
        }
    }

    private suspend fun decodeRawPoint(base64Point: String, format: EC.PublicKey.Format): Result<String> = try {
        val key = CryptographyProvider.Default.get(ECDSA)
            .publicKeyDecoder(EC.Curve.P256)
            .decodeFromByteArray(format, Base64.decode(base64Point))
        Result.success(key.encodeToByteArray(EC.PublicKey.Format.JWK).decodeToString())
    } catch (cause: Throwable) {
        Result.failure(cause)
    }

    private suspend fun decodeJwk(base64Spki: String, spec: KeySpec): Result<String> = try {
        val spki = EncodedKey.SpkiDer(BinaryData(Base64.decode(base64Spki)))
        Result.success(spki.toPublicJwk(spec).data.toByteArray().decodeToString())
    } catch (cause: Throwable) {
        Result.failure(cause)
    }

    private fun Result<String>.describe(): String =
        fold({ "OK" }, { "FAILED - ${it::class.simpleName}: ${it.message?.take(120)}" })

    private companion object {
        const val COMPRESSED_P256_SPKI =
            "MDkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDIgACYgnIzZczJISMji9O7O7JCuuIls7rvbTjEw0XjSKotGg="
        const val COMPRESSED_P256_POINT = "AmIJyM2XMySEjI4vTuzuyQrriJbO67204xMNF40iqLRo"
        const val UNCOMPRESSED_P256_POINT =
            "BGIJyM2XMySEjI4vTuzuyQrriJbO67204xMNF40iqLRo2Rq22dZjsMSQfqPhoT4KYM3dbG9M7M6eTOEyYSRG2iY="
        const val UNCOMPRESSED_P256_SPKI =
            "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEYgnIzZczJISMji9O7O7JCuuIls7rvbTjEw0XjSKotGjZGrbZ1mOw" +
                "xJB+o+GhPgpgzd1sb0zszp5M4TJhJEbaJg=="
    }
}
