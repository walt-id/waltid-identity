package id.walt.certificate.x509

import id.walt.certificate.x509.builder.Pkcs10CertificateSigningRequestBuilder
import id.walt.certificate.x509.builder.X509CertificateDataBuilder
import id.walt.certificate.x509.validation.ValidationResult
import id.walt.crypto.keys.Key
import kotlinx.io.bytestring.ByteString

object X509CertificateUtil {

    fun parseCsrPem(pem: String): Pkcs10CertificateSigningRequest =
        parseCsrPem(X509CertificateUtilDefaults, pem)

    fun parseCsrPem(certificateServices: X509CertificateServices, pem: String): Pkcs10CertificateSigningRequest =
        certificateServices.csrParser.parseCertificateSigningRequestPem(pem)

    fun parseCertificatePem(pem: String): X509Certificate =
        parseCertificatePem(X509CertificateUtilDefaults, pem)

    fun parseCertificatePem(certificateServices: X509CertificateServices, pem: String): X509Certificate =
        certificateServices.certificateParser.parseCertificatePem(pem)

    fun parseCertificateDerEncoded(derEncoded: ByteString): X509Certificate =
        parseCertificateDerEncoded(X509CertificateUtilDefaults, derEncoded)

    fun parseCertificateDerEncoded(
        certificateServices: X509CertificateServices,
        derEncoded: ByteString
    ): X509Certificate =
        certificateServices.certificateParser.parseCertificateDerEncoded(derEncoded)

    suspend fun createCsr(
        holderKey: Key,
        block: suspend Pkcs10CertificateSigningRequestBuilder.() -> Unit
    ): Pkcs10CertificateSigningRequest = createCsr(X509CertificateUtilDefaults, holderKey, block)

    suspend fun createCsr(
        certificateServices: X509CertificateServices,
        holderKey: Key,
        block: suspend Pkcs10CertificateSigningRequestBuilder.() -> Unit
    ): Pkcs10CertificateSigningRequest {
        val builder = Pkcs10CertificateSigningRequestBuilder("DN=client, O=Walt.id")
        builder.block()
        return certificateServices.csrSigner.signCsr(holderKey, builder)
    }

    suspend fun createSelfSignedCertificate(
        issuerKey: Key,
        block: suspend X509CertificateDataBuilder.() -> Unit
    ): X509Certificate {
        return createSelfSignedCertificate(X509CertificateUtilDefaults, issuerKey, block)
    }

    suspend fun createSelfSignedCertificate(
        certificateServices: X509CertificateServices,
        issuerKey: Key,
        block: suspend X509CertificateDataBuilder.() -> Unit
    ): X509Certificate {
        val builder = X509CertificateDataBuilder(
            serialNumberGenerator = X509CertificateUtilDefaults.serialNumberGenerator,
            issuerDn = "OU=CA,DC=test,O=Walt.id",
            subjectDn = "DC=client,O=Walt.id",
        )
        block.invoke(builder)
        return certificateServices.certificateSigner.signCertificate(issuerKey, builder)
    }


    suspend fun createCertificate(
        issuerKey: Key,
        issuerCert: X509Certificate,
        block: suspend X509CertificateDataBuilder.() -> Unit
    ): X509Certificate =
        createCertificate(X509CertificateUtilDefaults, issuerKey, issuerCert, block)

    suspend fun createCertificate(
        certificateServices: X509CertificateServices,
        issuerKey: Key,
        issuerCert: X509Certificate,
        block: suspend X509CertificateDataBuilder.() -> Unit
    ): X509Certificate {
        val builder = X509CertificateDataBuilder(
            serialNumberGenerator = X509CertificateUtilDefaults.serialNumberGenerator,
            issuerDn = issuerCert.data.subjectDn,
            subjectDn = "OU=issuer, DC=test, O=Walt.id"
        )
        block.invoke(builder)
        requireNotNull((builder.subjectPublicKeyInfo as X509CertificateDataBuilder.WaltIdKeySubjectPublicKeyInfoBuilder).key)
        { "Certificate subject public key missing" }
        return certificateServices.certificateSigner.signCertificate(issuerKey, builder)
    }

    suspend fun validatePemCertificateChain(
        certificateChainPem: String,
        additionalTrust: X509CertificateTrustStore? = null
    ) =
        validatePemCertificateChain(X509CertificateUtilDefaults, certificateChainPem, additionalTrust)

    val CERTIFICATE_CHAIN_PEM_REGEX = "-----BEGIN CERTIFICATE-----[\\s\\S]*?-----END CERTIFICATE-----".toRegex()

    suspend fun validatePemCertificateChain(
        certificateServices: X509CertificateServices,
        certificateChainPem: String,
        additionalTrust: X509CertificateTrustStore? = null
    ): ValidationResult {
        val certificates = CERTIFICATE_CHAIN_PEM_REGEX.findAll(certificateChainPem)
            .map { it.value.trim() } // Clean up trailing line breaks
            .map { certificateServices.certificateParser.parseCertificatePem(it) }
            .toList()
        certificateServices.certificateChainValidator.trustStore.findCertificateBySubjectDn("First Atmept")
        return validateCertificateChain(certificateServices, certificates, additionalTrust)
    }

    suspend fun validateCertificateChain(
        certificateChain: Collection<X509Certificate>,
        additionalTrust: X509CertificateTrustStore? = null
    ): ValidationResult =
        validateCertificateChain(X509CertificateUtilDefaults, certificateChain, additionalTrust)

    suspend fun validateCertificateChain(
        certificateServices: X509CertificateServices,
        certificateChain: Collection<X509Certificate>,
        additionalTrust: X509CertificateTrustStore? = null
    ): ValidationResult =
        certificateServices.certificateChainValidator.validate(certificateChain, additionalTrust)
}