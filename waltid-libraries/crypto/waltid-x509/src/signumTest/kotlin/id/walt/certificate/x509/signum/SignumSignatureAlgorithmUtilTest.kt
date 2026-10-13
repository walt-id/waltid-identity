package id.walt.certificate.x509.signum

import at.asitplus.signum.indispensable.CryptoSignature
import at.asitplus.signum.indispensable.asn1.Asn1BitString
import at.asitplus.signum.indispensable.asn1.Asn1Exception
import at.asitplus.signum.indispensable.pki.x509Encoded
import id.walt.certificate.x509.X509SigningAlgorithmInfo
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class SignumSignatureAlgorithmUtilTest {

    @Test
    fun rawEcSignatureThatResemblesBitStringKeepsItsComponents() {
        // A complete DER BIT STRING with invalid padding, and valid-width P-256 raw components.
        val raw = byteArrayOf(0x03, 0x3e, 0x08) + ByteArray(61) { 0x11 }
        val signature = assertIs<CryptoSignature.EC.DefiniteLength>(
            SignumSignatureAlgorithmUtil.evaluateSignature(ecAlgorithm, raw)
        )

        assertContentEquals(raw, signature.rawByteArray)
        val bitString = Asn1BitString.decodeFromTlv(signature.x509Encoded)
        assertEquals(0, bitString.numPaddingBits.toInt())
        assertContentEquals(signature.encodeToDer(), bitString.rawBytes)
    }

    @Test
    fun rawRsaSignatureThatResemblesBitStringKeepsEveryByte() {
        val raw = byteArrayOf(0x03, 0x81.toByte(), 0xfd.toByte(), 0x00) + ByteArray(252) { 0x11 }
        assertRsaBytesPreserved(raw)
    }

    @Test
    fun rawRsaSignatureThatResemblesEcSequenceKeepsEveryByte() {
        val raw = byteArrayOf(0x30, 0x81.toByte(), 0xfd.toByte(), 0x02, 0x7d) +
                ByteArray(125) { 0x11 } + byteArrayOf(0x02, 0x7c) + ByteArray(124) { 0x22 }
        assertRsaBytesPreserved(raw)
    }

    @Test
    fun derEcSignatureKeepsItsComponents() {
        val expected = CryptoSignature.EC.fromRawBytes(ByteArray(64) { 0x11 })
        val signature = SignumSignatureAlgorithmUtil.evaluateSignature(ecAlgorithm, expected.encodeToDer())
        assertEquals(expected, assertIs<CryptoSignature.EC>(signature))
    }

    @Test
    fun rawEcSignatureKeepsItsComponents() {
        val raw = ByteArray(64) { 0x11 }
        val signature = SignumSignatureAlgorithmUtil.evaluateSignature(ecAlgorithm, raw)
        assertContentEquals(raw, assertIs<CryptoSignature.EC.DefiniteLength>(signature).rawByteArray)
    }

    @Test
    fun unsupportedAlgorithmIsRejectedEvenForDerSignature() {
        val der = CryptoSignature.EC.fromRawBytes(ByteArray(64) { 0x11 }).encodeToDer()
        val unsupported = ecAlgorithm.copy(signingAlgorithmOid = "1.2.3.4")
        assertFailsWith<IllegalArgumentException> {
            SignumSignatureAlgorithmUtil.evaluateSignature(unsupported, der)
        }
    }

    @Test
    fun explicitDerEcRejectsRawSignature() {
        assertFailsWith<Asn1Exception> {
            SignumSignatureAlgorithmUtil.evaluateDerSignature(ecAlgorithm, ByteArray(64) { 0x11 })
        }
    }

    @Test
    fun shortDerEcSignatureKeepsDerInterpretation() {
        // This valid DER encoding is also 64 bytes long: length alone cannot determine its format.
        val der = byteArrayOf(0x30, 0x3e, 0x02, 0x1d) + ByteArray(29) { 0x11 } +
                byteArrayOf(0x02, 0x1d) + ByteArray(29) { 0x22 }
        assertEquals(64, der.size)
        val signature = SignumSignatureAlgorithmUtil.evaluateDerSignature(ecAlgorithm, der)
        assertIs<CryptoSignature.EC>(signature)
        assertContentEquals(der, signature.encodeToDer())
        assertEquals(signature, SignumSignatureAlgorithmUtil.evaluateSignature(ecAlgorithm, der))
    }

    @Test
    fun explicitDerRsaStillKeepsOpaqueBytes() {
        val raw = byteArrayOf(0x03, 0x81.toByte(), 0xfd.toByte(), 0x00) + ByteArray(252) { 0x11 }
        val signature = assertIs<CryptoSignature.RSA>(
            SignumSignatureAlgorithmUtil.evaluateDerSignature(rsaAlgorithm, raw)
        )
        assertContentEquals(raw, signature.rawByteArray)
    }

    private fun assertRsaBytesPreserved(raw: ByteArray) {
        assertEquals(256, raw.size)
        val signature = assertIs<CryptoSignature.RSA>(
            SignumSignatureAlgorithmUtil.evaluateSignature(rsaAlgorithm, raw)
        )
        assertContentEquals(raw, signature.rawByteArray)
        assertContentEquals(raw, Asn1BitString.decodeFromTlv(signature.x509Encoded).rawBytes)
    }

    private val ecAlgorithm = AlgorithmInfo(
        signingAlgorithmName = "SHA256withECDSA",
        signingAlgorithmOid = "1.2.840.10045.4.3.2",
        keyAlgorithmName = X509SigningAlgorithmInfo.KEY_ALG_NAME_EC,
        keyAlgorithmOid = X509SigningAlgorithmInfo.KEY_ALG_OID_EC,
        keyEllipticCurveOid = X509SigningAlgorithmInfo.CURVE_SECP256R1_OID,
    )

    private val rsaAlgorithm = AlgorithmInfo(
        signingAlgorithmName = "SHA256withRSA",
        signingAlgorithmOid = "1.2.840.113549.1.1.11",
        keyAlgorithmName = X509SigningAlgorithmInfo.KEY_ALG_NAME_RSA,
        keyAlgorithmOid = X509SigningAlgorithmInfo.KEY_ALG_OID_RSA,
        keyEllipticCurveOid = null,
    )

    private data class AlgorithmInfo(
        override val signingAlgorithmName: String,
        override val signingAlgorithmOid: String,
        override val keyAlgorithmName: String,
        override val keyAlgorithmOid: String,
        override val keyEllipticCurveOid: String?,
    ) : X509SigningAlgorithmInfo
}
