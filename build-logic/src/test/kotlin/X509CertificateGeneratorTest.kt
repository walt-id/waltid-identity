import id.walt.gradle.x509.X509CertificateGenerator
import id.walt.gradle.x509.X509CertificateRequest
import id.walt.gradle.x509.X509Profile
import id.walt.gradle.x509.jwk
import org.bouncycastle.asn1.x509.AuthorityKeyIdentifier
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.ExtendedKeyUsage
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.asn1.x509.KeyPurposeId
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.asn1.x509.SubjectKeyIdentifier
import org.bouncycastle.cert.X509CertificateHolder
import org.gradle.api.InvalidUserDataException
import java.security.cert.CertPathValidator
import java.security.cert.CertificateFactory
import java.security.cert.PKIXParameters
import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate
import java.security.interfaces.ECPublicKey
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class X509CertificateGeneratorTest {
    private val iaca = X509CertificateRequest(
        name = "iaca", profile = X509Profile.IACA, subject = "C=US,ST=NY,CN=Test IACA",
        issuerAlternativeNameUri = "https://iaca.example.com", crlDistributionPointUri = "https://iaca.example.com/crl",
    )
    private val documentSigner = X509CertificateRequest(
        name = "ds", profile = X509Profile.DOCUMENT_SIGNER, issuer = "iaca", subject = "C=US,ST=NY,CN=Test DS",
        crlDistributionPointUri = "https://iaca.example.com/crl",
    )

    @Test
    fun `CA chains validate with PKIX for every key algorithm`() {
        for (algorithm in listOf("P-256", "P-384", "P-521", "RSA-2048", "Ed25519")) {
            // Declared leaf-first to prove issuers are generated before the certificates they sign.
            val generated = X509CertificateGenerator.generate(listOf(
                X509CertificateRequest("server", X509Profile.LEAF, "CN=localhost", issuer = "intermediate",
                    keyAlgorithm = algorithm, dnsNames = listOf("localhost"), ipAddresses = listOf("127.0.0.1"),
                    extendedKeyUsages = listOf("serverAuth")),
                X509CertificateRequest("intermediate", X509Profile.CA, "CN=Intermediate", issuer = "root",
                    keyAlgorithm = algorithm, pathLength = 0, validityDays = 1000),
                X509CertificateRequest("root", X509Profile.CA, "CN=Root,O=walt.id,C=AT", keyAlgorithm = algorithm),
            ))
            val server = generated.getValue("server")
            assertEquals(3, server.chain.size)
            assertPkixValid(server.chain)

            assertFalse(BasicConstraints.fromExtensions(server.certificate.extensions).isCA)
            assertTrue(ExtendedKeyUsage.fromExtensions(server.certificate.extensions).hasKeyPurposeId(KeyPurposeId.id_kp_serverAuth))
            assertEquals(2, GeneralNames.fromExtensions(server.certificate.extensions, Extension.subjectAlternativeName).names.size)
            assertEquals(0, BasicConstraints.fromExtensions(generated.getValue("intermediate").certificate.extensions).pathLenConstraint.toInt())
        }
    }

    @Test
    fun `IACA and Document Signer follow the ISO 18013-5 profile`() {
        val generated = X509CertificateGenerator.generate(listOf(iaca, documentSigner))
        val root = generated.getValue("iaca").certificate
        val ds = generated.getValue("ds").certificate
        assertPkixValid(generated.getValue("ds").chain)

        assertEquals(root.subject, root.issuer)
        BasicConstraints.fromExtensions(root.extensions).let { assertTrue(it.isCA); assertEquals(0, it.pathLenConstraint.toInt()) }
        assertEquals(KeyUsage(KeyUsage.keyCertSign or KeyUsage.cRLSign), KeyUsage.fromExtensions(root.extensions))
        assertTrue(root.getExtension(Extension.basicConstraints).isCritical)
        assertTrue(root.getExtension(Extension.keyUsage).isCritical)
        assertTrue(root.hasExtensions() && root.getExtension(Extension.issuerAlternativeName) != null)
        assertNull(root.getExtension(Extension.authorityKeyIdentifier))

        assertNull(ds.getExtension(Extension.basicConstraints))
        assertEquals(KeyUsage(KeyUsage.digitalSignature), KeyUsage.fromExtensions(ds.extensions))
        assertTrue(ds.getExtension(Extension.extendedKeyUsage).isCritical)
        assertEquals("1.0.18013.5.1.2", ExtendedKeyUsage.fromExtensions(ds.extensions).usages.single().id)
        assertEquals(
            SubjectKeyIdentifier.fromExtensions(root.extensions).keyIdentifier.toList(),
            AuthorityKeyIdentifier.fromExtensions(ds.extensions).keyIdentifierOctets.toList(),
        )
        // Inherited from the IACA.
        assertEquals(root.getExtension(Extension.issuerAlternativeName).parsedValue, ds.getExtension(Extension.issuerAlternativeName).parsedValue)
        assertTrue(ds.getExtension(Extension.cRLDistributionPoints) != null)
        for (certificate in listOf(root, ds)) {
            assertTrue(certificate.serialNumber.signum() > 0 && certificate.serialNumber.bitLength() in 63..160)
        }
    }

    @Test
    fun `JWK carries the certificate key, chain and RFC 7638 kid`() {
        val ds = X509CertificateGenerator.generate(listOf(iaca, documentSigner)).getValue("ds")
        val private = jwk(ds, includePrivate = true)
        val public = jwk(ds, includePrivate = false)

        assertEquals(setOf("kty", "crv", "x", "y", "d", "kid", "alg", "x5c"), private.keys)
        assertEquals(private - "d", public)
        assertEquals("ES256", public["alg"])
        assertEquals(ds.chain.map { Base64.getEncoder().encodeToString(it.encoded) }, public["x5c"])
        val point = (ds.keyPair.public as ECPublicKey).w
        assertEquals(point.affineX, java.math.BigInteger(1, Base64.getUrlDecoder().decode(public["x"] as String)))
        assertEquals(32, Base64.getUrlDecoder().decode(private["d"] as String).size)

        // RFC 7638 section 3.1 example.
        assertEquals("NzbLsXh8uDCcd-6MNwXF4W_7noWXFZAfHkxZsRGC9Xs", id.walt.gradle.x509.thumbprint(mapOf(
            "kty" to "RSA",
            "e" to "AQAB",
            "n" to "0vx7agoebGcQSuuPiLJXZptN9nndrQmbXEps2aiAFbWhM78LhWx4cbbfAAtVT86zwu1RK7aPFFxuhDR1L6tSoc_BJECPebWKRXjBZCiFV4n3oknjhMstn64tZ_2W-5JsGY4Hc5n9yBXArwl93lqt7_RN5w6Cf0h4QyQ5v-65YGjQR0_FDW2QvzqY368QQMicAtaSqzs8KJZgnYb9c7d0zgdAZHzu6qMQvRL5hajrn1n91CbOpbISD08qNLyrdkt-bFTWhAI4vMQFh6WeZu0fM4lFd2NcRwr3XPksINHaQ-G_xBniIqbw0Ls1jF44-csFCur-kEgU8awapJzKnqDKgw",
        )))
    }

    @Test
    fun `profile violations are rejected`() {
        fun rejects(message: String, vararg requests: X509CertificateRequest) {
            val error = assertFailsWith<InvalidUserDataException> { X509CertificateGenerator.generate(requests.toList()) }
            assertTrue(message in error.message.orEmpty(), "expected '$message' in '${error.message}'")
        }
        val ca = X509CertificateRequest("ca", X509Profile.CA, "CN=CA")
        rejects("must be an IACA", ca, documentSigner.copy(issuer = "ca"))
        rejects("require an EC key", iaca.copy(keyAlgorithm = "RSA-2048"))
        rejects("country must match", iaca, documentSigner.copy(subject = "C=DE,ST=NY,CN=DS"))
        rejects("(ST) must match", iaca, documentSigner.copy(subject = "C=US,CN=DS"))
        rejects("not an ISO 3166-1", iaca.copy(subject = "C=XX,CN=IACA"))
        rejects("needs issuerAlternativeNameUri", iaca.copy(issuerAlternativeNameUri = null))
        rejects("needs crlDistributionPointUri", iaca, documentSigner.copy(crlDistributionPointUri = null))
        rejects("must not exceed 457 days", iaca, documentSigner.copy(validityDays = 500))
        rejects("cannot sign", X509CertificateRequest("leaf", X509Profile.LEAF, "CN=a"),
            X509CertificateRequest("child", X509Profile.LEAF, "CN=b", issuer = "leaf"))
        rejects("after its issuer", ca, X509CertificateRequest("leaf", X509Profile.LEAF, "CN=a", issuer = "ca", validityDays = 4000))
        rejects("unknown issuer", X509CertificateRequest("leaf", X509Profile.LEAF, "CN=a", issuer = "missing"))
        rejects("cycle", X509CertificateRequest("a", X509Profile.CA, "CN=a", issuer = "b"),
            X509CertificateRequest("b", X509Profile.CA, "CN=b", issuer = "a"))
    }

    private fun assertPkixValid(chain: List<X509CertificateHolder>) {
        val factory = CertificateFactory.getInstance("X.509")
        val certificates = chain.map { factory.generateCertificate(it.encoded.inputStream()) as X509Certificate }
        val parameters = PKIXParameters(setOf(TrustAnchor(certificates.last(), null))).apply { isRevocationEnabled = false }
        CertPathValidator.getInstance("PKIX").validate(factory.generateCertPath(certificates.dropLast(1)), parameters)
    }
}
