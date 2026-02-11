package id.walt.x509

import okio.ByteString.Companion.toByteString
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.DEROctetString
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.*
import kotlin.test.*

class X509V3ExtensionsTest {

    private val chain = TestCA.generateChain()


    @Test
    fun `criticalX509V3ExtensionOIDs contains keyUsage and basicConstraints for CA certificates`() {

        val expected = setOf(
            X509V3ExtensionOID.BasicConstraints,
            X509V3ExtensionOID.KeyUsage,
        )

        assertEquals(
            expected = expected,
            actual = chain.rootCert.criticalX509V3ExtensionOIDs,
        )

        assertEquals(
            expected = expected,
            actual = chain.interCert.criticalX509V3ExtensionOIDs,
        )

    }

    @Test
    fun `nonCriticalX509V3ExtensionOIDs contains SKI and AKI for CA certificates`() {
        val expected = setOf(
            X509V3ExtensionOID.SubjectKeyIdentifier,
            X509V3ExtensionOID.AuthorityKeyIdentifier,
        )

        assertEquals(
            expected = expected,
            actual = chain.rootCert.nonCriticalX509V3ExtensionOIDs,
        )

        assertEquals(
            expected = expected,
            actual = chain.interCert.nonCriticalX509V3ExtensionOIDs,
        )

    }

    @Test
    fun `criticalX509V3ExtensionOIDs contains only keyUsage for leaf certificates`() {

        assertEquals(
            expected = setOf(X509V3ExtensionOID.KeyUsage),
            actual = chain.leafCert.criticalX509V3ExtensionOIDs,
        )

    }

    @Test
    fun `nonCriticalX509V3ExtensionOIDs contains only SKI for leaf certificates`() {

        assertEquals(
            expected = setOf(X509V3ExtensionOID.SubjectKeyIdentifier),
            actual = chain.leafCert.nonCriticalX509V3ExtensionOIDs,
        )

    }

    @Test
    fun `critical and non-critical OID sets ignore unknown OIDs`() {

        val cert = generateSelfSignedCert { builder, extUtils, keyPair ->
            builder.addExtension(Extension.basicConstraints, true, BasicConstraints(true))
            builder.addExtension(Extension.keyUsage, true, KeyUsage(KeyUsage.keyCertSign or KeyUsage.cRLSign))
            builder.addExtension(
                Extension.subjectKeyIdentifier,
                false,
                extUtils.createSubjectKeyIdentifier(keyPair.public),
            )

            builder.addExtension(ASN1ObjectIdentifier("1.2.3.4"), true, DEROctetString(byteArrayOf(0x01)))
            builder.addExtension(ASN1ObjectIdentifier("1.2.3.5"), false, DEROctetString(byteArrayOf(0x02)))
        }

        assertEquals(
            expected = setOf(X509V3ExtensionOID.BasicConstraints, X509V3ExtensionOID.KeyUsage),
            actual = cert.criticalX509V3ExtensionOIDs,
        )

        assertEquals(
            expected = setOf(X509V3ExtensionOID.SubjectKeyIdentifier),
            actual = cert.nonCriticalX509V3ExtensionOIDs,
        )

    }

    @Test
    fun `subjectKeyIdentifier returns null when extension is missing`() {

        val cert = generateSelfSignedCert { _, _, _ -> }
        assertNull(cert.subjectKeyIdentifier)
    }

    @Test
    fun `subjectKeyIdentifier returns expected bytes when extension is present`() {

        val keyPair = genRsaKeyPair()
        val extUtils = JcaX509ExtensionUtils()
        val expected = extUtils.createSubjectKeyIdentifier(keyPair.public).keyIdentifier.toByteString()

        val cert = generateSelfSignedCert(
            keyPair = keyPair,
        ) { builder, _, _ ->
            builder.addExtension(
                Extension.subjectKeyIdentifier,
                false,
                extUtils.createSubjectKeyIdentifier(keyPair.public),
            )
        }

        assertEquals(
            expected = expected,
            actual = cert.subjectKeyIdentifier,
        )

    }

    @Test
    fun `authorityKeyIdentifier returns null when extension is missing`() {

        assertNull(chain.leafCert.authorityKeyIdentifier)

    }

    @Test
    fun `authorityKeyIdentifier equals subjectKeyIdentifier for self-signed certificates`() {

        assertNotNull(chain.rootCert.subjectKeyIdentifier)

        assertEquals(
            expected = chain.rootCert.subjectKeyIdentifier,
            actual = chain.rootCert.authorityKeyIdentifier,
        )

    }

    @Test
    fun `authorityKeyIdentifier matches issuer subjectKeyIdentifier for issued certificates`() {

        assertNotNull(chain.rootCert.subjectKeyIdentifier)

        assertEquals(
            expected = chain.rootCert.subjectKeyIdentifier,
            actual = chain.interCert.authorityKeyIdentifier,
        )

    }

    @Test
    fun `x509KeyUsages returns empty set when extension is missing`() {

        val cert = generateSelfSignedCert { _, _, _ -> }

        assertEquals(
            expected = emptySet(),
            actual = cert.x509KeyUsages,
        )

    }

    @Test
    fun `x509KeyUsages returns DigitalSignature for leaf cert from test chain`() {

        assertEquals(
            expected = setOf(X509KeyUsage.DigitalSignature),
            actual = chain.leafCert.x509KeyUsages,
        )

    }

    @Test
    fun `x509KeyUsages returns CA usages for CA cert from test chain`() {

        assertEquals(
            expected = setOf(X509KeyUsage.KeyCertSign, X509KeyUsage.CRLSign),
            actual = chain.rootCert.x509KeyUsages,
        )

    }

    @Test
    fun `x509BasicConstraints returns isCA false for leaf cert`() {

        assertEquals(
            expected = false,
            actual = chain.leafCert.x509BasicConstraints.isCA,
        )

        assertEquals(
            expected = -1,
            actual = chain.leafCert.x509BasicConstraints.pathLengthConstraint,
        )

    }

    @Test
    fun `x509BasicConstraints returns isCA true and mirrors JCA basicConstraints for CA cert`() {

        assertTrue(chain.rootCert.x509BasicConstraints.isCA)

        assertEquals(
            expected = chain.rootCert.basicConstraints,
            actual = chain.rootCert.x509BasicConstraints.pathLengthConstraint,
        )

    }

    @Test
    fun `x509BasicConstraints returns isCA false when extension is missing`() {

        val cert = generateSelfSignedCert { builder, _, _ ->
            builder.addExtension(Extension.keyUsage, true, KeyUsage(KeyUsage.digitalSignature))
        }

        assertEquals(
            expected = false,
            actual = cert.x509BasicConstraints.isCA,
        )

        assertEquals(
            expected = -1,
            actual = cert.x509BasicConstraints.pathLengthConstraint,
        )

    }

    private fun genRsaKeyPair() = KeyPairGenerator.getInstance("RSA").apply {
        initialize(2048)
    }.generateKeyPair()

    private fun generateSelfSignedCert(
        keyPair: KeyPair = genRsaKeyPair(),
        subjectDn: String = "CN=Test",
        notBefore: Date = Date(System.currentTimeMillis() - 1_000),
        notAfter: Date = Date(System.currentTimeMillis() + 60_000),
        extensions: (builder: X509v3CertificateBuilder, extUtils: JcaX509ExtensionUtils, keyPair: KeyPair) -> Unit,
    ): X509Certificate {
        val subject = X500Name(subjectDn)
        val builder = JcaX509v3CertificateBuilder(
            subject,
            BigInteger(160, SecureRandom()),
            notBefore,
            notAfter,
            subject,
            keyPair.public,
        )
        val extUtils = JcaX509ExtensionUtils()
        extensions(builder, extUtils, keyPair)
        return TestCA.sign(keyPair.private, builder)
    }

}
