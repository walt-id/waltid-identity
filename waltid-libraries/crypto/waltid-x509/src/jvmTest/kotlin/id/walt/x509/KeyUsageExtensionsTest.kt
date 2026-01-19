package id.walt.x509

import org.bouncycastle.asn1.x509.KeyUsage
import kotlin.test.Test
import kotlin.test.assertEquals

class KeyUsageExtensionsTest {

    @Test
    fun `toCertificateKeyUsages returns empty set for zero bits`() {
        val converted = KeyUsage(0).toCertificateKeyUsages()

        assertEquals(
            expected = emptySet(),
            actual = converted,
        )

    }

    @Test
    fun `toCertificateKeyUsages returns expected subset for mixed bits`() {
        val bits = KeyUsage.digitalSignature or KeyUsage.keyEncipherment or KeyUsage.keyAgreement
        val converted = KeyUsage(bits).toCertificateKeyUsages()

        assertEquals(
            expected = setOf(
                X509KeyUsage.DigitalSignature,
                X509KeyUsage.KeyEncipherment,
                X509KeyUsage.KeyAgreement,
            ),
            actual = converted,
        )

    }

    @Test
    fun `toCertificateKeyUsages returns all usages when all bits are set`() {
        val allBits = X509KeyUsage.entries
            .map { it.toBCKeyUsage() }
            .fold(0) { acc, bit -> acc or bit }

        val converted = KeyUsage(allBits).toCertificateKeyUsages()

        assertEquals(
            expected = X509KeyUsage.entries.toSet(),
            actual = converted,
        )

    }

    @Test
    fun `toBouncyCastleKeyUsage returns zero bits for empty iterable`() {
        val bc = emptySet<X509KeyUsage>().toBouncyCastleKeyUsage()

        assertEquals(
            expected = emptySet(),
            actual = bc.toCertificateKeyUsages(),
        )

    }

    @Test
    fun `toBouncyCastleKeyUsage ignores duplicates`() {
        val bc = listOf(
            X509KeyUsage.DigitalSignature,
            X509KeyUsage.DigitalSignature,
            X509KeyUsage.KeyAgreement,
        ).toBouncyCastleKeyUsage()

        assertEquals(
            expected = setOf(X509KeyUsage.DigitalSignature, X509KeyUsage.KeyAgreement),
            actual = bc.toCertificateKeyUsages(),
        )

    }

    @Test
    fun `roundtrip each usage through BouncyCastle bits`() {
        X509KeyUsage.entries.forEach { usage ->
            val bc = KeyUsage(usage.toBCKeyUsage())
            val converted = bc.toCertificateKeyUsages()

            assertEquals(
                expected = setOf(usage),
                actual = converted,
            )

        }
    }

    @Test
    fun `roundtrip multiple usages through toBouncyCastleKeyUsage`() {
        val usages = listOf(
            X509KeyUsage.DigitalSignature,
            X509KeyUsage.KeyCertSign,
            X509KeyUsage.CRLSign,
            X509KeyUsage.KeyAgreement,
        )

        val bc = usages.toBouncyCastleKeyUsage()

        assertEquals(
            expected = usages.toSet(),
            actual = bc.toCertificateKeyUsages(),
        )

    }
}

