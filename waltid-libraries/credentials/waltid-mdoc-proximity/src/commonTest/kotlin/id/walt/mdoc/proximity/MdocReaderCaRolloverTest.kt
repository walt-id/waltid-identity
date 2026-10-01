package id.walt.mdoc.proximity

import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.X509CertificateUtil
import id.walt.certificate.x509.extension.AuthorityKeyIdentifierExtension.Companion.extensionAuthorityKeyIdentifier
import id.walt.certificate.x509.extension.BasicConstraintsExtension.Companion.extensionBasicConstraints
import id.walt.certificate.x509.extension.KeyUsageExtension
import id.walt.certificate.x509.extension.KeyUsageExtension.Companion.extensionKeyUsage
import id.walt.certificate.x509.extension.SubjectKeyIdentifierExtension.Companion.extensionSubjectKeyIdentifier
import id.walt.certificate.x509.profile.IsoMdocReaderAuthenticationX509CertificateProfile.profileMdocReaderAuthenticationCertificate
import id.walt.certificate.x509.truststore.InMemoryTrustStore
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.EcdsaSignatureEncoding
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class MdocReaderCaRolloverTest {
    private val now = Instant.parse("2026-09-20T12:00:00Z")
    private val clock = object : Clock {
        override fun now() = now
    }

    @Test
    fun `validates a reader through a self-issued rollover CA from the RICAL`() = runTest {
        val certificates = rolloverCertificates()
        val rolloverInfo = certificateInfo(certificates.rolloverCa, isTrustAnchor = false)
        val rical = Rical(
            version = "1.0",
            provider = "provider",
            date = now,
            certificateInfos = listOf(
                certificateInfo(certificates.oldRoot, isTrustAnchor = true),
                rolloverInfo,
            ),
            type = "org.iso.18013.5.1.reader_authentication",
        )
        val reader = ReaderAuthenticationEvidence(
            scope = ReaderAuthenticationScope.WholeRequest,
            certificateChainDer = listOf(certificates.reader.encodedDer),
        )

        val result = X509RicalReaderPathValidator(clock).validate(reader, rical)

        val valid = assertIs<RicalReaderPathResult.Valid>(
            result,
            "Expected reader -> rollover CA -> old-key root. The reader supplies only its leaf; " +
                    "the RICAL supplies the rollover CA and trusts the old root. " +
                    "The rollover CA has equal subject/issuer names but is signed by the old key. " +
                    "Neither CA has a path-length limit",
        )
        assertEquals(
            listOf(certificates.reader, certificates.rolloverCa, certificates.oldRoot).map { it.encodedDer },
            valid.validatedPath.map { it.encodedDer },
            "The validated path must retain the rollover CA and reach the old-key trust anchor.",
        )
        assertEquals(
            rolloverInfo,
            valid.authority,
            "The rollover CA is the first matching RICAL authority above the reader.",
        )
    }

    @Test
    fun `validates a complete reader chain through a self-issued rollover CA`() = runTest {
        val certificates = rolloverCertificates()

        val result = MdocX509CertificateUtil.modocReaderAuthentication(clock).validateCertificateChain(
            certificateChain = listOf(certificates.reader, certificates.rolloverCa),
            trustOverride = InMemoryTrustStore(listOf(certificates.oldRoot)),
        )

        assertTrue(
            result.valid,
            buildString {
                appendLine("Expected the complete reader -> rollover CA -> old-key root chain to validate.")
                appendLine("Equal CA subject names must not require equal certificate fingerprints:")
                appendLine("the rollover CA contains the new key and is signed by the old root's key.")
                appendLine("Both CAs have cA=true and keyCertSign in critical extensions, with no path-length limit.")
                appendLine("Validation errors:")
                result.errorLog.forEach { error ->
                    appendLine("  ${error.validatorId} (${error.subjectDn}): ${error.message}")
                }
            },
        )
    }

    private data class RolloverCertificates(
        val oldRoot: X509Certificate,
        val rolloverCa: X509Certificate,
        val reader: X509Certificate,
    )

    private fun certificateInfo(certificate: X509Certificate, isTrustAnchor: Boolean) = RicalCertificateInfo(
        certificateDer = certificate.encodedDer,
        serialNumber = certificate.data.serialNumberRaw,
        subjectKeyIdentifier = assertNotNull(
            certificate.data.extensionSubjectKeyIdentifier?.keyIdentifier,
            "Each RICAL fixture certificate must identify its public key.",
        ),
        isTrustAnchor = isTrustAnchor,
        authorityKeyIdentifier = certificate.data.extensionAuthorityKeyIdentifier?.keyIdentifier,
    )

    private suspend fun rolloverCertificates(): RolloverCertificates {
        val runtime = CryptoRuntime(defaultSoftwareKeyProviders())
        try {
            val usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY)
            val oldKey = runtime.generateMdocTestKey("rollover-old", usages)
            val newKey = runtime.generateMdocTestKey("rollover-new", usages)
            val readerKey = runtime.generateMdocTestKey("rollover-reader", usages)
            val algorithm = SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256, EcdsaSignatureEncoding.DER)
            val oldRoot = X509CertificateUtil.createSelfSignedCertificate(oldKey, algorithm) {
                subjectDn = "CN=Reader CA"
                validity = X509Certificate.Validity(now - 10.days, now + 30.days)
                extensionSubjectKeyIdentifier()
                extensionBasicConstraints {
                    critical = true
                    cA = true
                }
                extensionKeyUsage {
                    critical = true
                    addKeyUsage(KeyUsageExtension.KeyUsage.keyCertSign)
                }
            }
            val rolloverCa = X509CertificateUtil.createCertificate(oldKey, oldRoot, algorithm) {
                subjectDn = oldRoot.data.subjectDn
                subjectPublicKey(newKey)
                validity = X509Certificate.Validity(now - 5.days, now + 20.days)
                extensionSubjectKeyIdentifier()
                extensionBasicConstraints {
                    critical = true
                    cA = true
                }
                extensionKeyUsage {
                    critical = true
                    addKeyUsage(KeyUsageExtension.KeyUsage.keyCertSign)
                }
            }
            val reader = X509CertificateUtil.createCertificate(newKey, rolloverCa, algorithm) {
                profileMdocReaderAuthenticationCertificate("https://example.com/crl", readerKey, "Reader")
                validity = X509Certificate.Validity(now - 1.days, now + 10.days)
            }
            val oldKeyId = assertNotNull(
                oldRoot.data.extensionSubjectKeyIdentifier?.keyIdentifier,
                "The old root must identify its CA public key.",
            )
            val newKeyId = assertNotNull(
                rolloverCa.data.extensionSubjectKeyIdentifier?.keyIdentifier,
                "The rollover CA must identify its new public key.",
            )
            assertEquals(
                rolloverCa.data.subjectDn, rolloverCa.data.issuerDn,
                "The fixture must be self-issued: the rollover CA keeps the root's subject name.",
            )
            assertNotEquals(oldKeyId, newKeyId, "Rollover must introduce a different CA public key.")
            assertEquals(
                oldKeyId, rolloverCa.data.extensionAuthorityKeyIdentifier?.keyIdentifier,
                "The old root's key must issue the rollover CA certificate.",
            )
            assertEquals(
                newKeyId, reader.data.extensionAuthorityKeyIdentifier?.keyIdentifier,
                "The new CA key must issue the reader certificate.",
            )
            assertNull(
                oldRoot.data.extensionBasicConstraints?.pathLenConstraint,
                "The root must not impose a path-length limit in this regression fixture.",
            )
            assertNull(
                rolloverCa.data.extensionBasicConstraints?.pathLenConstraint,
                "The rollover CA must not impose a path-length limit in this regression fixture.",
            )
            return RolloverCertificates(oldRoot, rolloverCa, reader)
        } finally {
            runtime.close()
        }
    }
}
