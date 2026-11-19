package id.walt.ades

import id.walt.ades.crypto.AdesDigestAlgorithm
import id.walt.ades.test.TestPki
import id.walt.ades.x509.AttributeCertificateHolder
import kotlinx.coroutines.test.runTest
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.DERUTF8String
import org.bouncycastle.cert.AttributeCertificateIssuer
import org.bouncycastle.cert.X509v2AttributeCertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.math.BigInteger
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.toJavaInstant
import org.bouncycastle.cert.AttributeCertificateHolder as BcHolder

class AttributeCertificateHolderTest {
    private val pki = TestPki()

    private fun certificate(holder: BcHolder): ByteArray = X509v2AttributeCertificateBuilder(
        holder, AttributeCertificateIssuer(pki.issuingCa.holder.subject), BigInteger.valueOf(42),
        Date.from((pki.now - 1.days).toJavaInstant()), Date.from((pki.now + 1.days).toJavaInstant()),
    ).apply { addAttribute(ASN1ObjectIdentifier("2.5.4.72"), DERUTF8String("participant")) }
        .build(JcaContentSignerBuilder("SHA256withECDSA").build(pki.issuingCa.keyPair.private)).encoded

    @Test
    fun issuerSerialAndEntityNameBindToTheCorrectSigner() = runTest {
        for (holder in listOf(BcHolder(pki.signer.holder), BcHolder(pki.signer.holder.subject))) {
            val binding = AttributeCertificateHolder(certificate(holder))
            assertTrue(binding.matches(pki.signer.certificate))
            assertFalse(binding.matches(pki.secondSigner.certificate))
        }
    }

    @Test
    fun objectDigestsBindPublicKeyOrWholeCertificate() = runTest {
        for ((type, data) in listOf(0 to pki.signer.holder.subjectPublicKeyInfo.publicKeyData.bytes, 1 to pki.signer.der)) {
            val holder = BcHolder(type, ASN1ObjectIdentifier(AdesDigestAlgorithm.SHA256.oid), null,
                AdesDigestAlgorithm.SHA256.digest(data))
            val binding = AttributeCertificateHolder(certificate(holder))
            assertTrue(binding.matches(pki.signer.certificate))
            assertFalse(binding.matches(pki.secondSigner.certificate))
        }
    }

    @Test
    fun malformedOrUnsupportedHolderCannotBeAccepted() = runTest {
        assertFailsWith<IllegalArgumentException> { AttributeCertificateHolder(byteArrayOf(0x30, 0)) }
        val holder = BcHolder(2, ASN1ObjectIdentifier(AdesDigestAlgorithm.SHA256.oid), ASN1ObjectIdentifier("1.2.3"), ByteArray(32))
        assertFailsWith<IllegalArgumentException> { AttributeCertificateHolder(certificate(holder)).matches(pki.signer.certificate) }
    }
}
