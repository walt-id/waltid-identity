import id.walt.crypto.keys.EccUtils
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.stream.Stream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@DisplayName("ECC DER ↔ IEEE P1363 signature conversion")
class EccSignatureConversionTest {

    private fun generateKeyPair(jcaCurve: String): KeyPair =
        KeyPairGenerator.getInstance("EC")
            .also { it.initialize(ECGenParameterSpec(jcaCurve)) }
            .generateKeyPair()

    private fun sign(kp: KeyPair, data: ByteArray): ByteArray =
        Signature.getInstance("SHA256withECDSA")
            .also { it.initSign(kp.private); it.update(data) }
            .sign()

    private fun verify(kp: KeyPair, data: ByteArray, derSig: ByteArray): Boolean =
        Signature.getInstance("SHA256withECDSA")
            .also { it.initVerify(kp.public); it.update(data) }
            .verify(derSig)

    @ParameterizedTest(name = "{0} → P1363 length should be {1} bytes")
    @MethodSource("curvesToExpectedP1363Length")
    fun `derToP1363 produces correct byte length`(jcaCurve: String, expectedBytes: Int) {
        val kp = generateKeyPair(jcaCurve)
        val derSig = sign(kp, "test payload".toByteArray())
        val p1363 = EccUtils.convertDERtoIEEEP1363(derSig)
        assertEquals(expectedBytes, p1363.size,
            "P1363 signature for $jcaCurve must be $expectedBytes bytes (was ${p1363.size})")
    }

    @Test
    fun `already-P1363 input is returned unchanged`() {
        val p1363 = ByteArray(64) { it.toByte() }
        val result = EccUtils.convertDERtoIEEEP1363(p1363)
        assertTrue(p1363.contentEquals(result))
    }

    @Test
    fun `already-P1363 input P384 is returned unchanged`() {
        val p1363 = ByteArray(96) { it.toByte() }
        val result = EccUtils.convertDERtoIEEEP1363(p1363)
        assertTrue(p1363.contentEquals(result))
    }

    @Test
    fun `invalid DER input throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> {
            EccUtils.convertDERtoIEEEP1363(byteArrayOf(0x01, 0x02, 0x03))
        }
    }

    @Test
    fun `empty input throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> {
            EccUtils.convertDERtoIEEEP1363(ByteArray(0))
        }
    }

    @Test
    fun `odd-sized P1363 input throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> {
            EccUtils.convertP1363toDER(ByteArray(63))
        }
    }

    @Test
    fun `zero-length P1363 input throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> {
            EccUtils.convertP1363toDER(ByteArray(0))
        }
    }

    @ParameterizedTest(name = "curve={0}")
    @MethodSource("allSupportedCurves")
    fun `DER to P1363 to DER round-trip verifies correctly`(jcaCurve: String) {
        val kp = generateKeyPair(jcaCurve)
        val data = "round-trip test for $jcaCurve".toByteArray()
        val originalDer = sign(kp, data)
        val p1363 = EccUtils.convertDERtoIEEEP1363(originalDer)
        val convertedDer = EccUtils.convertP1363toDER(p1363)
        assertTrue(verify(kp, data, convertedDer),
            "Signature must verify after DER→P1363→DER round-trip for $jcaCurve")
    }

    @ParameterizedTest(name = "curve={0}")
    @MethodSource("allSupportedCurves")
    fun `P1363 intermediate has expected fixed length`(jcaCurve: String) {
        val kp = generateKeyPair(jcaCurve)
        val der = sign(kp, "data".toByteArray())
        val p1363 = EccUtils.convertDERtoIEEEP1363(der)
        val expectedLength = when (jcaCurve) {
            "secp256r1" -> 64
            "secp384r1" -> 96
            "secp521r1" -> 132
            else -> error("unexpected curve")
        }
        assertEquals(expectedLength, p1363.size)
    }

    @ParameterizedTest(name = "curve={0}")
    @MethodSource("allSupportedCurves")
    fun `multiple signatures for same key all round-trip`(jcaCurve: String) {
        val kp = generateKeyPair(jcaCurve)
        repeat(5) { i ->
            val data = "message-$i".toByteArray()
            val der = sign(kp, data)
            val p1363 = EccUtils.convertDERtoIEEEP1363(der)
            val derAgain = EccUtils.convertP1363toDER(p1363)
            assertTrue(verify(kp, data, derAgain), "Iteration $i failed for $jcaCurve")
        }
    }

    @Test
    fun `original DER signature verifies`() {
        val kp = generateKeyPair("secp256r1")
        val data = "baseline test".toByteArray()
        val der = sign(kp, data)
        assertTrue(verify(kp, data, der))
    }

    @Test
    fun `DER and round-tripped DER both start with ASN1 sequence tag 0x30`() {
        val kp = generateKeyPair("secp256r1")
        val data = "structure test".toByteArray()
        val originalDer = sign(kp, data)
        val convertedDer = EccUtils.convertP1363toDER(EccUtils.convertDERtoIEEEP1363(originalDer))
        assertEquals(0x30.toByte(), originalDer[0])
        assertEquals(0x30.toByte(), convertedDer[0])
    }

    @Test
    fun `P1363 for P-256 is 64 bytes`() {
        val kp = generateKeyPair("secp256r1")
        val p1363 = EccUtils.convertDERtoIEEEP1363(sign(kp, "data".toByteArray()))
        assertEquals(64, p1363.size)
    }

    companion object {
        @JvmStatic
        fun curvesToExpectedP1363Length(): Stream<Arguments> = Stream.of(
            Arguments.of("secp256r1", 64),
            Arguments.of("secp384r1", 96),
            Arguments.of("secp521r1", 132),
        )

        @JvmStatic
        fun allSupportedCurves(): Stream<Arguments> = Stream.of(
            Arguments.of("secp256r1"),
            Arguments.of("secp384r1"),
            Arguments.of("secp521r1"),
        )
    }
}
