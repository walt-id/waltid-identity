package id.walt.certificate.x509

import id.walt.certificate.x509.PemUtil.normalizePem
import id.walt.certificate.x509.builder.Pkcs10CertificateSigningRequestBuilder
import id.walt.certificate.x509.builder.X509CertificateDataBuilder
import id.walt.certificate.x509.extension.AuthorityKeyIdentifierExtension.Companion.extensionAuthorityKeyIdentifier
import id.walt.certificate.x509.extension.BasicConstraintsExtension.Companion.extensionBasicConstraints
import id.walt.certificate.x509.extension.SubjectKeyIdentifierExtension.Companion.extensionSubjectKeyIdentifier
import id.walt.certificate.x509.truststore.InMemoryTrustStore
import id.walt.certificate.x509.validation.ValidationResult
import id.walt.certificate.x509.validation.X509CertificateChainValidator
import id.walt.certificate.x509.validation.validator.X509CertificateValidator
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.algorithms.EcdsaSignatureEncoding
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.Key
import kotlinx.io.bytestring.ByteString
import id.walt.crypto.keys.Key as Crypt1Key

sealed class X509CertificateUtil(val services: X509CertificateServices) {

    fun nextSerialNumber(): ByteString = services.serialNumberGenerator.next()

    fun parseCsrPem(pem: String): Pkcs10CertificateSigningRequest =
        services.csrParser.parseCertificateSigningRequestPem(normalizePem(pem))

    fun parseCertificatePem(pem: String): X509Certificate =
        services.certificateParser.parseCertificatePem(normalizePem(pem))

    fun parseCertificateDerEncoded(derEncoded: ByteString): X509Certificate =
        services.certificateParser.parseCertificateDerEncoded(derEncoded)

    suspend fun createCsr(
        holderKey: Crypt1Key,
        block: suspend Pkcs10CertificateSigningRequestBuilder.() -> Unit
    ): Pkcs10CertificateSigningRequest {
        val builder = Pkcs10CertificateSigningRequestBuilder("DN=client, O=Walt.id")
        builder.block()
        return services.csrSigner.signCsr(holderKey, builder)
    }

    suspend fun createSelfSignedCertificate(
        issuerKey: Key,
        signatureAlgorithm: SignatureAlgorithm,
        block: suspend X509CertificateDataBuilder.() -> Unit
    ): X509Certificate {
        if (signatureAlgorithm is SignatureAlgorithm.Ecdsa) {
            require(signatureAlgorithm.encoding == EcdsaSignatureEncoding.DER) { "Certificates must use signature DER encoding" }
        }
        val builder = X509CertificateDataBuilder(
            serialNumberGenerator = services.serialNumberGenerator,
            issuerDnRaw = ByteString(),
            subjectDn = "OU=CA,DC=test,O=Walt.id",
        )
        builder.extensionBasicConstraints {
            cA = true
        }
        block.invoke(builder)
        builder.issuerDnRaw = ByteString()
        builder.extensionAuthorityKeyIdentifier()
        builder.extensionSubjectKeyIdentifier()
        return services.certificateSigner.signCertificate(issuerKey, signatureAlgorithm, builder)
    }

    suspend fun createSelfSignedCertificate(
        issuerKey: Crypt1Key,
        block: suspend X509CertificateDataBuilder.() -> Unit
    ): X509Certificate {
        val builder = X509CertificateDataBuilder(
            serialNumberGenerator = services.serialNumberGenerator,
            issuerDnRaw = ByteString(),
            subjectDn = "OU=CA,DC=test,O=Walt.id",
        )
        builder.extensionBasicConstraints {
            cA = true
        }
        block.invoke(builder)
        builder.issuerDnRaw = ByteString()
        builder.extensionAuthorityKeyIdentifier()
        builder.extensionSubjectKeyIdentifier()
        return services.certificateSigner.signCertificate(issuerKey, builder)
    }

    suspend fun createCertificate(
        issuerKey: Key,
        issuerCert: X509Certificate,
        signatureAlgorithm: SignatureAlgorithm,
        block: suspend X509CertificateDataBuilder.() -> Unit
    ): X509Certificate {
        val builder = X509CertificateDataBuilder(
            serialNumberGenerator = services.serialNumberGenerator,
            issuerDnRaw = issuerCert.data.subjectDnRaw,
            subjectDn = "OU=issuer, DC=test, O=Walt.id"
        )
        block.invoke(builder)
        builder.issuerDnRaw = issuerCert.data.subjectDnRaw
        requireNotNull((builder.subjectPublicKeyInfo as X509CertificateDataBuilder.WaltIdKeySubjectPublicKeyInfoBuilder).key) {
            "Certificate subject public key missing"
        }
        builder.extensionAuthorityKeyIdentifier()
        issuerCert.data.extensionSubjectKeyIdentifier?.let { subjectKeyId ->
            val issuerPublicKeyInfo = services.certificateSigner.convertKeyToPublicKeyInfo(issuerKey)
            require(subjectKeyId.keyIdentifier == issuerPublicKeyInfo.keyId) {
                "Issuer certificate is not signed by issuer key. Subject key identifier does not match issuer public key identifier."
            }
        }
        return services.certificateSigner.signCertificate(issuerKey, signatureAlgorithm, builder)
    }


    suspend fun createCertificate(
        issuerKey: Crypt1Key,
        issuerCert: X509Certificate,
        block: suspend X509CertificateDataBuilder.() -> Unit
    ): X509Certificate {
        val builder = X509CertificateDataBuilder(
            serialNumberGenerator = services.serialNumberGenerator,
            issuerDnRaw = issuerCert.data.subjectDnRaw,
            subjectDn = "OU=issuer, DC=test, O=Walt.id"
        )
        block.invoke(builder)
        requireNotNull((builder.subjectPublicKeyInfo as X509CertificateDataBuilder.WaltIdKeySubjectPublicKeyInfoBuilder).crypto1key) {
            "Certificate subject public key missing"
        }
        builder.extensionAuthorityKeyIdentifier()
        issuerCert.data.extensionSubjectKeyIdentifier?.let { subjectKeyId ->
            val issuerPublicKeyInfo = services.certificateSigner.convertKeyToPublicKeyInfo(issuerKey)
            require(subjectKeyId.keyIdentifier == issuerPublicKeyInfo.keyId) {
                "Issuer certificate is not signed by issuer key. Subject key identifier does not match issuer public key identifier."
            }
        }
        return services.certificateSigner.signCertificate(issuerKey, builder)
    }

    suspend fun validatePemCertificateChain(
        certificateChainPem: String,
        additionalTrust: X509CertificateTrustStore? = null
    ): ValidationResult {
        val certificates =
            PemUtil.spitPemChain(certificateChainPem)
                .map { services.certificateParser.parseCertificatePem(it) }
                .toList()
        services.certificateChainValidator.trustStore.findCertificateBySubjectDn("First Atmept")
        return validateCertificateChain(certificates, additionalTrust)
    }

    suspend fun validateCsrSignature(csr: Pkcs10CertificateSigningRequest): Boolean =
        services.signatureValidator.validateCsrSignature(services.cryptoRuntime, csr)

    suspend fun validateCertificateChain(
        certificateChain: Collection<X509Certificate>,
        trustedRootCert: X509Certificate
    ): ValidationResult =
        validateCertificateChain(certificateChain, InMemoryTrustStore(listOf(trustedRootCert)))

    suspend fun validateCertificateChain(
        certificateChain: Collection<X509Certificate>,
        additionalTrust: X509CertificateTrustStore? = null
    ): ValidationResult =
        services.certificateChainValidator.validate(services.cryptoRuntime, certificateChain, additionalTrust)

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

    private var cryptoRuntime: CryptoRuntime = from.services.cryptoRuntime

    //trust
    private var trustStore: X509CertificateTrustStore = from.services.certificateChainValidator.trustStore

    //services
    private var certificateParser: X509CertificateParser = from.services.certificateParser
    private var csrParser: Pkcs10CertificateSigningRequestParser = from.services.csrParser
    private var signatureValidator: SignatureValidator = from.services.signatureValidator
    private var csrSigner: Pkcs10CertificateSigningRequestSigner = from.services.csrSigner
    private var certificateSigner: X509CertificateSigner = from.services.certificateSigner
    private var certificateChainValidator: X509CertificateChainValidator = from.services.certificateChainValidator

    private var servicesChanged: Boolean = false
    private var trustChanged: Boolean = false

    fun setCryptoRuntime(cryptoRuntime: CryptoRuntime) {
        this.cryptoRuntime = cryptoRuntime
    }

    fun addValidators(vararg validators: X509CertificateValidator) {
        val originValidatorIdMap = certificateChainValidator.validators
            .associateBy { it.id }
        val additionalValidatorIdMap = validators.associateBy { it.id }
        val newValidators = originValidatorIdMap + additionalValidatorIdMap
        certificateChainValidator =
            X509CertificateChainValidator(newValidators.values, certificateChainValidator.trustStore)
    }

    fun setServices(
        csrParser: Pkcs10CertificateSigningRequestParser,
        csrSigner: Pkcs10CertificateSigningRequestSigner,
        signatureValidator: SignatureValidator,
        certificateParser: X509CertificateParser,
        certificateSigner: X509CertificateSigner,
        certificateChainValidator: X509CertificateChainValidator
    ) {
        this.csrParser = csrParser
        this.csrSigner = csrSigner
        this.certificateParser = certificateParser
        this.certificateSigner = certificateSigner
        this.signatureValidator = signatureValidator
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
            cryptoRuntime = cryptoRuntime,
            certificateParser = certificateParser,
            csrParser = csrParser,
            csrSigner = csrSigner,
            certificateSigner = certificateSigner,
            certificateChainValidator = evaluateChainValidator()
        )
    } else {
        from.services.copy(
            cryptoRuntime = cryptoRuntime,
            certificateChainValidator = evaluateChainValidator()
        )
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