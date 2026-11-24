package id.walt.crypto2.keys

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.EC
import dev.whyoleg.cryptography.algorithms.ECDSA
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith

class EcPointValidationTest {

    @Test
    fun `each curve's generator and its negation are on the curve`() {
        generators.forEach { (curve, x, y, negatedY) ->
            curve.requirePointOnCurve(x.hexToByteArray(), y.hexToByteArray())
            curve.requirePointOnCurve(x.hexToByteArray(), negatedY.hexToByteArray())
            curve.requireUncompressedPointOnCurve(byteArrayOf(0x04) + x.hexToByteArray() + y.hexToByteArray())
        }
    }

    @Test
    fun `a generator with either coordinate moved by one is rejected`() {
        generators.forEach { (curve, x, y, _) ->
            assertFailsWith<IllegalArgumentException>(curve.name) {
                curve.requirePointOnCurve(x.hexToByteArray(), y.hexToByteArray().plusOne())
            }
            assertFailsWith<IllegalArgumentException>(curve.name) {
                curve.requirePointOnCurve(x.hexToByteArray().plusOne(), y.hexToByteArray())
            }
        }
    }

    @Test
    fun `keys the platform generates are on the curve`() = runTest {
        val ecdsa = CryptographyProvider.Default.get(ECDSA)
        listOf(EcCurve.P256 to EC.Curve.P256, EcCurve.P384 to EC.Curve.P384, EcCurve.P521 to EC.Curve.P521)
            .forEach { (curve, providerCurve) ->
                repeat(8) {
                    val point = ecdsa.keyPairGenerator(providerCurve).generateKey().publicKey
                        .encodeToByteArray(EC.PublicKey.Format.RAW)
                    curve.requireUncompressedPointOnCurve(point)
                }
            }
    }

    @Test
    fun `a coordinate must be below the field prime`() {
        // (0, sqrt(b)) is on P-256; x = p is the same residue but not a canonical field element.
        val y = "66485c780e2f83d72433bd5d84a06bb6541c2af31dae871728bf856a174f93f4".hexToByteArray()
        EcCurve.P256.requirePointOnCurve(ByteArray(32), y)
        assertFailsWith<IllegalArgumentException> {
            EcCurve.P256.requirePointOnCurve(P256_PRIME.hexToByteArray(), y)
        }
    }

    @Test
    fun `malformed points and unknown curves are rejected`() {
        val (curve, x, y, _) = generators.first()
        assertFailsWith<IllegalArgumentException> {
            curve.requirePointOnCurve(x.hexToByteArray().copyOfRange(1, 32), y.hexToByteArray().copyOfRange(1, 32))
        }
        assertFailsWith<IllegalArgumentException> {
            curve.requireUncompressedPointOnCurve(byteArrayOf(0x02) + x.hexToByteArray() + y.hexToByteArray())
        }
        assertFailsWith<IllegalArgumentException> { curve.requireUncompressedPointOnCurve(byteArrayOf()) }
        assertFailsWith<IllegalArgumentException> {
            EcCurve("P-192").requirePointOnCurve(x.hexToByteArray(), y.hexToByteArray())
        }
    }

    private fun ByteArray.plusOne(): ByteArray = copyOf().also { value ->
        var index = value.lastIndex
        while (index >= 0) {
            value[index] = (value[index] + 1).toByte()
            if (value[index] != 0.toByte()) break
            index--
        }
    }

    private data class Generator(val curve: EcCurve, val x: String, val y: String, val negatedY: String)

    private companion object {
        const val P256_PRIME = "ffffffff00000001000000000000000000000000ffffffffffffffffffffffff"

        // Base points from SEC 2 and RFC 5639; negatedY is p - y.
        val generators = listOf(
            Generator(
                EcCurve.P256,
                "6b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c296",
                "4fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33576b315ececbb6406837bf51f5",
                "b01cbd1c01e58065711814b583f061e9d431cca994cea1313449bf97c840ae0a",
            ),
            Generator(
                EcCurve.P384,
                "aa87ca22be8b05378eb1c71ef320ad746e1d3b628ba79b9859f741e082542a385502f25dbf55296c3a545e3872760ab7",
                "3617de4a96262c6f5d9e98bf9292dc29f8f41dbd289a147ce9da3113b5f0b8c00a60b1ce1d7e819d7a431d7c90ea0e5f",
                "c9e821b569d9d390a26167406d6d23d6070be242d765eb831625ceec4a0f473ef59f4e30e2817e6285bce2846f15f1a0",
            ),
            Generator(
                EcCurve.P521,
                "00c6858e06b70404e9cd9e3ecb662395b4429c648139053fb521f828af606b4d3dbaa14b5e77efe75928fe1dc127a2ffa8de3348" +
                    "b3c1856a429bf97e7e31c2e5bd66",
                "011839296a789a3bc0045c8a5fb42c7d1bd998f54449579b446817afbd17273e662c97ee72995ef42640c550b9013fad0761353c" +
                    "7086a272c24088be94769fd16650",
                "00e7c6d6958765c43ffba375a04bd382e426670abbb6a864bb97e85042e8d8c199d368118d66a10bd9bf3aaf46fec052f89ecac3" +
                    "8f795d8d3dbf77416b89602e99af",
            ),
            Generator(
                EcCurve.SECP256K1,
                "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
                "483ada7726a3c4655da4fbfc0e1108a8fd17b448a68554199c47d08ffb10d4b8",
                "b7c52588d95c3b9aa25b0403f1eef75702e84bb7597aabe663b82f6f04ef2777",
            ),
            Generator(
                EcCurve.BRAINPOOL_P256R1,
                "8bd2aeb9cb7e57cb2c4b482ffc81b7afb9de27e1e3bd23c23a4453bd9ace3262",
                "547ef835c3dac4fd97f8461a14611dc9c27745132ded8e545c1d54c72f046997",
                "557c5fa5de13e4bea66dc47689226fa8abc4b110a73891d3c3f5f355f069e9e0",
            ),
            Generator(
                EcCurve.BRAINPOOL_P384R1,
                "1d1c64f068cf45ffa2a63a81b7c13f6b8847a3e77ef14fe3db7fcafe0cbd10e8e826e03436d646aaef87b2e247d4af1e",
                "8abe1d7520f9c2a45cb1eb8e95cfd55262b70b29feec5864e19c054ff99129280e4646217791811142820341263c5315",
                "01fb010d823eaa83b2ab83efbb166c8cb27865dfee67fe4f3115d4c98625e7fb9e8d6108188b996044c4fcd20acb993e",
            ),
            Generator(
                EcCurve.BRAINPOOL_P512R1,
                "81aee4bdd82ed9645a21322e9c4c6a9385ed9f70b5d916c1b43b62eef4d0098eff3b1f78e2d0d48d50d1687b93b97d5f7c6d50" +
                    "47406a5e688b352209bcb9f822",
                "7dde385d566332ecc0eabfa9cf7822fdf209f70024a57b1aa000c55b881f8111b2dcde494a5f485e5bca4bd88a2763aed1ca2b" +
                    "2fa8f0540678cd1e0f3ad80892",
                "2cff655b8586919e7eea27046451d909d92696b38f2456f43662d76ee813875fca70bcb751671fe4530355525c7c1d3756b7d3" +
                    "ff8492727eafdd42471d624061",
            ),
        )
    }
}
