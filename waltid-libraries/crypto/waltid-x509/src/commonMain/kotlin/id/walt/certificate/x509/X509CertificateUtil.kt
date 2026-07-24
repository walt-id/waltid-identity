package id.walt.certificate.x509

import id.walt.certificate.x509.builder.Pkcs10CertificateSigningRequestBuilder
import id.walt.certificate.x509.builder.X509CertificateDataBuilder
import id.walt.certificate.x509.validation.ValidationResult
import id.walt.certificate.x509.validation.X509CertificateChainValidator
import id.walt.crypto.keys.Key
import kotlinx.io.bytestring.ByteString

sealed class X509CertificateUtil(val services: X509CertificateServices) {

    fun nextSerialNumber(): ByteString = services.serialNumberGenerator.next()

    fun parseCsrPem(pem: String): Pkcs10CertificateSigningRequest =
        services.csrParser.parseCertificateSigningRequestPem(pem)

    fun parseCertificatePem(pem: String): X509Certificate =
        services.certificateParser.parseCertificatePem(pem)

    fun parseCertificateDerEncoded(derEncoded: ByteString): X509Certificate =
        services.certificateParser.parseCertificateDerEncoded(derEncoded)

    suspend fun createCsr(
        holderKey: Key,
        block: suspend Pkcs10CertificateSigningRequestBuilder.() -> Unit
    ): Pkcs10CertificateSigningRequest {
        val builder = Pkcs10CertificateSigningRequestBuilder("DN=client, O=Walt.id")
        builder.block()
        return services.csrSigner.signCsr(holderKey, builder)
    }

    suspend fun createSelfSignedCertificate(
        issuerKey: Key,
        block: suspend X509CertificateDataBuilder.() -> Unit
    ): X509Certificate {
        val builder = X509CertificateDataBuilder(
            serialNumberGenerator = services.serialNumberGenerator,
            issuerDn = "OU=CA,DC=test,O=Walt.id",
            subjectDn = "DC=client,O=Walt.id",
        )
        block.invoke(builder)
        return services.certificateSigner.signCertificate(issuerKey, builder)
    }

    suspend fun createCertificate(
        issuerKey: Key,
        issuerCert: X509Certificate,
        block: suspend X509CertificateDataBuilder.() -> Unit
    ): X509Certificate {
        val builder = X509CertificateDataBuilder(
            serialNumberGenerator = services.serialNumberGenerator,
            issuerDn = issuerCert.data.subjectDn,
            subjectDn = "OU=issuer, DC=test, O=Walt.id"
        )
        block.invoke(builder)
        requireNotNull((builder.subjectPublicKeyInfo as X509CertificateDataBuilder.WaltIdKeySubjectPublicKeyInfoBuilder).key)
        { "Certificate subject public key missing" }
        return services.certificateSigner.signCertificate(issuerKey, builder)
    }

    val CERTIFICATE_CHAIN_PEM_REGEX = "-----BEGIN CERTIFICATE-----[\\s\\S]*?-----END CERTIFICATE-----".toRegex()

    suspend fun validatePemCertificateChain(
        certificateChainPem: String,
        additionalTrust: X509CertificateTrustStore? = null
    ): ValidationResult {
        val certificates = CERTIFICATE_CHAIN_PEM_REGEX.findAll(certificateChainPem)
            .map { it.value.trim() } // Clean up trailing line breaks
            .map { services.certificateParser.parseCertificatePem(it) }
            .toList()
        services.certificateChainValidator.trustStore.findCertificateBySubjectDn("First Atmept")
        return validateCertificateChain(certificates, additionalTrust)
    }

    suspend fun validateCertificateChain(
        certificateChain: Collection<X509Certificate>,
        additionalTrust: X509CertificateTrustStore? = null
    ): ValidationResult =
        services.certificateChainValidator.validate(certificateChain, additionalTrust)

    companion object Default : X509CertificateUtil(platformDefaultServices())

}

expect fun platformDefaultServices(): X509CertificateServices

fun X509CertificateUtil(
    from: X509CertificateUtil = X509CertificateUtil.Default,
    builderAction: X509CertificateUtilBuilder.() -> Unit
): X509CertificateUtil {
    val builder = X509CertificateUtilBuilder(from)
    builderAction.invoke(builder)
    return builder.toUtil()
}

private class UtilImpl(services: X509CertificateServices) : X509CertificateUtil(services)

class X509CertificateUtilBuilder internal constructor(val from: X509CertificateUtil) {

    //trust
    private var trustStore: X509CertificateTrustStore = from.services.certificateChainValidator.trustStore

    //services
    private var certificateParser: X509CertificateParser = from.services.certificateParser
    private var csrParser: Pkcs10CertificateSigningRequestParser = from.services.csrParser
    private var csrSigner: Pkcs10CertificateSigningRequestSigner = from.services.csrSigner
    private var certificateSigner: X509CertificateSigner = from.services.certificateSigner
    private var certificateChainValidator: X509CertificateChainValidator = from.services.certificateChainValidator

    private var servicesChanged: Boolean = false
    private var trustChanged: Boolean = false

    fun setServices(
        csrParser: Pkcs10CertificateSigningRequestParser,
        csrSigner: Pkcs10CertificateSigningRequestSigner,
        certificateParser: X509CertificateParser,
        certificateSigner: X509CertificateSigner,
        certificateChainValidator: X509CertificateChainValidator
    ) {
        this.csrParser = csrParser
        this.csrSigner = csrSigner
        this.certificateParser = certificateParser
        this.certificateSigner = certificateSigner
        this.certificateChainValidator = X509CertificateChainValidator(
            certificateChainValidator.validators,
            this.trustStore
        )
        this.servicesChanged = true
        this.trustChanged = false
    }

    fun setTrust(trustStore: X509CertificateTrustStore) {
        this.trustStore = trustStore
        this.trustChanged = true
    }

    internal fun toUtil(): X509CertificateUtil = if (servicesChanged) {
        from.services.copy(
            certificateParser = certificateParser,
            csrParser = csrParser,
            csrSigner = csrSigner,
            certificateSigner = certificateSigner,
            certificateChainValidator = evaluateChainValidator()
        )
    } else {
        from.services.copy(certificateChainValidator = evaluateChainValidator())
    }.let {
        UtilImpl(it)
    }

    private fun evaluateChainValidator(): X509CertificateChainValidator =
        if (trustChanged) {
            X509CertificateChainValidator(
                this.certificateChainValidator.validators,
                trustStore
            )
        } else {
            this.certificateChainValidator
        }
}