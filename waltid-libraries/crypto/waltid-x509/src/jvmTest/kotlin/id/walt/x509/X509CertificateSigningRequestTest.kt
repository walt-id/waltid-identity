package id.walt.x509

import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.toByteString
import org.bouncycastle.asn1.DERPrintableString
import org.bouncycastle.asn1.DERUTF8String
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class X509CertificateSigningRequestTest {

    private val generator = X509CertificateSigningRequestGenerator()
    private val parser = X509CertificateSigningRequestParser()

    @Test
    fun `generates parses and validates a CSR`() = runTest {
        val key = JWKKey.generate(KeyType.secp256r1)
        val challengePasswordDer = DERPrintableString("secret-password").encoded.toByteString()
        val customExtensionValueDer = DERUTF8String("custom-extension-value").encoded.toByteString()

        val csrData = X509CertificateSigningRequestBuilder(
            subject = x509SubjectOf(
                X509SubjectAttributes.country("AT"),
                X509SubjectAttributes.commonName("service.example.org"),
                X509SubjectAttributes.organization("walt.id"),
            ),
        ).addSubjectAlternativeNames(
            listOf(
                X509SubjectAlternativeName.DnsName("service.example.org"),
                X509SubjectAlternativeName.Uri("spiffe://example/service"),
            )
        ).addKeyUsages(
            listOf(
                X509KeyUsage.DigitalSignature,
                X509KeyUsage.KeyEncipherment,
            )
        ).addExtendedKeyUsages(
            listOf(
                X509ExtendedKeyUsage.ServerAuth,
                X509ExtendedKeyUsage.ClientAuth,
            )
        ).basicConstraints(
            X509BasicConstraints(
                isCA = false,
                pathLengthConstraint = 0,
            )
        ).addRequestedExtension(
            X509RequestedExtension.raw(
                oid = "1.2.3.4.5",
                critical = false,
                valueDer = customExtensionValueDer,
            )
        ).addAttribute(
            X509CsrAttribute.singleValue(
                oid = X509CsrAttributeOids.ChallengePassword,
                valueDer = challengePasswordDer,
            )
        ).build()

        val csr = generator.generate(
            X509CertificateSigningRequestSpec(
                csrData = csrData,
                signingKey = key,
            )
        )
        val parsed = parser.parse(csr)

        validateCertificateSigningRequestSignature(csr)

        assertEquals(csrData.subject, parsed.subject)
        assertEquals(csrData.subjectAlternativeNames, parsed.subjectAlternativeNames)
        assertEquals(csrData.keyUsages, parsed.keyUsages)
        assertEquals(csrData.extendedKeyUsages, parsed.extendedKeyUsages)
        assertEquals(csrData.basicConstraints, parsed.basicConstraints)
        val expectedJwk = key.getPublicKey().exportJWK().replace(Regex("\\\"kid\\\":\\\"[^\\\"]+\\\",?"), "")
        val actualJwk = parsed.publicKey.exportJWK().replace(Regex("\\\"kid\\\":\\\"[^\\\"]+\\\",?"), "")
        assertEquals(expectedJwk, actualJwk)
        assertTrue(parsed.signatureAlgorithmOid.isNotBlank())
        assertTrue(parsed.attributes.any { it.oid == X509CsrAttributeOids.ExtensionRequest })
        assertTrue(
            parsed.attributes.any {
                it.oid == X509CsrAttributeOids.ChallengePassword && it.valuesDer.single() == challengePasswordDer
            }
        )
        assertTrue(
            parsed.requestedExtensions.any {
                it.oid == "1.2.3.4.5" && it.valueDer == customExtensionValueDer
            }
        )

        val pem = csr.toPEMEncodedString()
        assertEquals(csr, CertificateSigningRequestDer.fromPEMEncodedString(pem))
    }

    @Test
    fun `generates a Document Signer CSR compatible with the ISO profile`() = runTest {
        val key = JWKKey.generate(KeyType.secp256r1)

        val csrData = X509CertificateSigningRequestBuilder(
            subject = x509SubjectOf(
                X509SubjectAttributes.country("AT"),
                X509SubjectAttributes.commonName("Example Document Signer"),
                X509SubjectAttributes.locality("Vienna"),
            ),
        ).addKeyUsage(X509KeyUsage.DigitalSignature)
            .addExtendedKeyUsage(X509KnownCertificateProfiles.IsoDocumentSigner.extendedKeyUsages.single())
            .build()

        val csr = generator.generate(
            X509CertificateSigningRequestSpec(
                csrData = csrData,
                signingKey = key,
            )
        )
        val parsed = parser.parse(csr)
        val compatibility = parsed.checkCompatibility(X509KnownCertificateProfiles.IsoDocumentSigner)

        validateCertificateSigningRequestSignature(csr)

        assertEquals(csrData.subject, parsed.subject)
        assertEquals(setOf(X509KeyUsage.DigitalSignature), parsed.keyUsages)
        assertEquals(X509KnownCertificateProfiles.IsoDocumentSigner.extendedKeyUsages, parsed.extendedKeyUsages)
        assertEquals(null, parsed.basicConstraints)
        assertTrue(compatibility.isCompatible, compatibility.issues.joinToString())
    }

    @Test
    fun `rejects tampered CSR signatures`() = runTest {
        val key = JWKKey.generate(KeyType.secp256r1)
        val csr = generator.generate(
            X509CertificateSigningRequestSpec(
                csrData = X509CertificateSigningRequestBuilder(
                    subject = x509SubjectOf(
                        X509SubjectAttributes.country("AT"),
                        X509SubjectAttributes.commonName("tamper.example.org"),
                    ),
                ).addKeyUsage(X509KeyUsage.DigitalSignature)
                    .build(),
                signingKey = key,
            )
        )

        val tamperedBytes = csr.bytes.toByteArray().also { bytes ->
            bytes[bytes.lastIndex] = (bytes.last().toInt() xor 0x01).toByte()
        }
        val tamperedCsr = CertificateSigningRequestDer(tamperedBytes.toByteString())

        assertFailsWith<X509ValidationException> {
            validateCertificateSigningRequestSignatureBlocking(tamperedCsr)
        }
    }
}
