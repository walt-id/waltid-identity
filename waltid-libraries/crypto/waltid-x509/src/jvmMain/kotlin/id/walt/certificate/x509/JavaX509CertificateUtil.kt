package id.walt.certificate.x509

import id.walt.certificate.x509.builder.Pkcs10CertificateSigningRequestBuilder
import id.walt.certificate.x509.builder.X509CertificateDataBuilder
import id.walt.certificate.x509.validation.ValidationResult
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.Key
import kotlinx.coroutines.runBlocking
import kotlinx.io.bytestring.ByteString
import java.util.function.Consumer
import id.walt.crypto.keys.Key as Crypto1Key

/**
 * A Java-friendly facade over [X509CertificateUtil].
 *
 * [X509CertificateUtil] is designed around Kotlin idioms that don't translate cleanly to
 * Java: `suspend` functions, and builder configuration via a lambda-with-receiver
 * (`Builder.() -> Unit`). This class wraps it so the same operations - parsing, issuing and
 * validating certificates and CSRs - are callable from Java as plain blocking methods
 * configured with [Consumer] callbacks, with `byte[]` accepted in place of
 * [kotlinx.io.bytestring.ByteString] wherever this class itself takes DER input.
 *
 * ```java
 * X509Certificate cert = JavaX509CertificateUtil.getDefault().createSelfSignedCertificate(
 *     issuerKey,
 *     new SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA256, EcdsaSignatureEncoding.DER),
 *     builder -> builder.setSubjectDn("CN=example")
 * );
 * System.out.println(cert.getEncodedPem());
 * ```
 *
 * Kotlin callers should keep using [X509CertificateUtil] directly; this class exists for
 * Java (and other JVM languages without first-class coroutine/DSL support) call sites.
 */
class JavaX509CertificateUtil(private val delegate: X509CertificateUtil) {

    /** The underlying Kotlin [X509CertificateUtil], for interop with Kotlin call sites. */
    fun asKotlin(): X509CertificateUtil = delegate

    fun nextSerialNumber(): ByteArray = delegate.nextSerialNumber().toByteArray()

    fun parseCsrPem(pem: String): Pkcs10CertificateSigningRequest = delegate.parseCsrPem(pem)

    fun parseCertificatePem(pem: String): X509Certificate = delegate.parseCertificatePem(pem)

    fun parseCertificateDerEncoded(derEncoded: ByteArray): X509Certificate =
        delegate.parseCertificateDerEncoded(ByteString(derEncoded))

    @JvmOverloads
    fun createCsr(
        holderKey: Crypto1Key,
        block: Consumer<Pkcs10CertificateSigningRequestBuilder> = Consumer {}
    ): Pkcs10CertificateSigningRequest = runBlocking {
        delegate.createCsr(holderKey) { block.accept(this) }
    }

    @JvmOverloads
    fun createSelfSignedCertificate(
        issuerKey: Key,
        signatureAlgorithm: SignatureAlgorithm,
        block: Consumer<X509CertificateDataBuilder> = Consumer {}
    ): X509Certificate = runBlocking {
        delegate.createSelfSignedCertificate(issuerKey, signatureAlgorithm) { block.accept(this) }
    }

    @JvmOverloads
    fun createSelfSignedCertificate(
        issuerKey: Crypto1Key,
        block: Consumer<X509CertificateDataBuilder> = Consumer {}
    ): X509Certificate = runBlocking {
        delegate.createSelfSignedCertificate(issuerKey) { block.accept(this) }
    }

    @JvmOverloads
    fun createCertificate(
        issuerKey: Key,
        issuerCert: X509Certificate,
        signatureAlgorithm: SignatureAlgorithm,
        block: Consumer<X509CertificateDataBuilder> = Consumer {}
    ): X509Certificate = runBlocking {
        delegate.createCertificate(issuerKey, issuerCert, signatureAlgorithm) { block.accept(this) }
    }

    @JvmOverloads
    fun createCertificate(
        issuerKey: Crypto1Key,
        issuerCert: X509Certificate,
        block: Consumer<X509CertificateDataBuilder> = Consumer {}
    ): X509Certificate = runBlocking {
        delegate.createCertificate(issuerKey, issuerCert) { block.accept(this) }
    }

    /**
     * @param trustOverride if given, used *instead of* this util's configured trust store for
     *   this call - not merged with it. May be `null` (the default) to use the configured store.
     */
    @JvmOverloads
    fun validatePemCertificateChain(
        certificateChainPem: String,
        trustOverride: X509CertificateTrustStore? = null
    ): ValidationResult = runBlocking {
        delegate.validatePemCertificateChain(certificateChainPem, trustOverride)
    }

    fun validateCsrSignature(csr: Pkcs10CertificateSigningRequest): Boolean = runBlocking {
        delegate.validateCsrSignature(csr)
    }

    fun validateCertificateChain(
        certificateChain: Collection<X509Certificate>,
        trustedRootCert: X509Certificate
    ): ValidationResult = runBlocking {
        delegate.validateCertificateChain(certificateChain, trustedRootCert)
    }

    /**
     * @param trustOverride if given, used *instead of* this util's configured trust store for
     *   this call - not merged with it. May be `null` (the default) to use the configured store.
     */
    @JvmOverloads
    fun validateCertificateChain(
        certificateChain: Collection<X509Certificate>,
        trustOverride: X509CertificateTrustStore? = null
    ): ValidationResult = runBlocking {
        delegate.validateCertificateChain(certificateChain, trustOverride)
    }

    companion object {
        /** Ready-to-use instance backed by [X509CertificateUtil.Default] (Bouncy Castle on JVM). */
        @JvmField
        val DEFAULT: JavaX509CertificateUtil = JavaX509CertificateUtil(X509CertificateUtil.Default)

        @JvmStatic
        fun getDefault(): JavaX509CertificateUtil = DEFAULT

        /**
         * Builds a custom-configured instance, e.g. to install additional chain validators or a
         * different trust store, starting from [from] (the default instance unless given):
         * ```java
         * JavaX509CertificateUtil custom = JavaX509CertificateUtil.configure(
         *     builder -> builder.setTrust(myTrustStore));
         * ```
         */
        @JvmStatic
        @JvmOverloads
        fun configure(
            configurer: Consumer<X509CertificateUtilBuilder>,
            from: JavaX509CertificateUtil = DEFAULT
        ): JavaX509CertificateUtil =
            JavaX509CertificateUtil(X509CertificateUtil(from.delegate) { configurer.accept(this) })
    }
}
