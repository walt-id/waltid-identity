package id.walt.certificate.x509.profile

import id.walt.certificate.x509.TestKeyUtil
import id.walt.certificate.x509.X509CertificateUtil
import id.walt.certificate.x509.profile.IsoDocumentSignerX509CertificateProfile.profileDocumentSignerCertificate
import id.walt.certificate.x509.profile.IsoIaCaRootX509CertificateProfile.profileIaCaRootCertificate
import id.walt.certificate.x509.truststore.InMemoryTrustStore
import id.walt.certificate.x509.validation.ValidationResult
import id.walt.certificate.x509.validation.validator.X509CertificateBasicConstraintsValidator
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.EcdsaSignatureEncoding
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.Key
import id.walt.crypto2.keys.KeyEncodingFormat
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MdlCertificateChainSigningTest {

    @Test
    fun shouldSignMdlChainWithEcP256() = runTest {
        createAndValidateMdlChain(EcCurve.P256, DigestAlgorithm.SHA_256)
    }

    @Test
    fun shouldSignMdlChainWithEcP384() = runTest {
        createAndValidateMdlChain(EcCurve.P384, DigestAlgorithm.SHA_384)
    }

    suspend fun createAndValidateMdlChain(curve: EcCurve, digest: DigestAlgorithm) {
        val rootCaKey = TestKeyUtil.genEcKey("rootCa", curve)
        val dsKey = TestKeyUtil.genEcKey("mdl DS key", curve)
        return createAndValidateMdlChain(rootCaKey, dsKey, digest)
    }

    suspend fun createAndValidateMdlChain(
        rootCaKey: Key,
        dsKey: Key,
        digest: DigestAlgorithm
    ) {
        val sigAlg = SignatureAlgorithm.Ecdsa(digest, encoding = EcdsaSignatureEncoding.DER)
        val privateCaKey = rootCaKey.capabilities.privateKeyExporter
            ?.exportPrivateKey(KeyEncodingFormat.JWK)
        println("Private Root CA key:")
        println(privateCaKey?.data?.toByteArray()?.decodeToString())

        val rootCert = caTool.createSelfSignedCertificate(rootCaKey, sigAlg) {
            profileIaCaRootCertificate(
                issuerDn = "cn=Walt ID Root,C=AT",
                issuerEmailAddress = "office@walt.id",
                issuerUri = "https://walt.id",
            )
        }
        caTool.validateCertificateChain(listOf(rootCert), InMemoryTrustStore(listOf(rootCert))).also { result ->
            assertTrue(result.valid, "Validation log: ${result.log}")
            assertFalse(
                result.hasWarnings,
                "Warnings: ${result.log.filter { it.severity == ValidationResult.Severity.WARNING }}"
            )
        }
        println("Root CA cert:")
        println(rootCert.encodedPem)

        val privateDsKey = dsKey.capabilities.privateKeyExporter
            ?.exportPrivateKey(KeyEncodingFormat.JWK)
        println("Private DS key:")
        println(privateDsKey?.data?.toByteArray()?.decodeToString())
        val dsCert = dsTool.createCertificate(rootCaKey, rootCert, sigAlg) {
            profileDocumentSignerCertificate(
                subjectKey = dsKey,
                subjectDn = "cn=Walt ID mDL DS,C=AT",
                crlDistributionPointUri = "https://crl.walt.id/crl.der",
                issuerEmailAddress = "office@walt.id",
                issuerUri = "https://walt.id",
            )
        }

        println("DS cert:")
        println(dsCert.encodedPem)

        dsTool.validateCertificateChain(listOf(dsCert), InMemoryTrustStore(listOf(rootCert))).also { result ->
            assertTrue(result.valid, "Validation log: ${result.log}")
            assertFalse(
                result.hasWarnings,
                "Warnings: ${result.log.filter { it.severity == ValidationResult.Severity.WARNING }}"
            )
        }
    }

    companion object {
        val caTool = X509CertificateUtil {
            addValidators(
                X509CertificateBasicConstraintsValidator(leafCanBeCa = true),
                IsoIaCaRootX509CertificateProfile
            )
        }

        val dsTool = X509CertificateUtil { addValidators(IsoDocumentSignerX509CertificateProfile) }

    }
}