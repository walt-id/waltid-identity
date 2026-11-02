package id.walt.mdoc.verification

import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.X509CertificateUtil
import id.walt.certificate.x509.extension.BasicConstraintsExtension.Companion.extensionBasicConstraints
import id.walt.certificate.x509.profile.IsoDocumentSignerX509CertificateProfile
import id.walt.certificate.x509.validation.ValidationContext
import id.walt.certificate.x509.validation.validator.*
import id.walt.cose.protectedAlgorithm
import id.walt.cose.verify
import id.walt.crypto2.keys.Key
import id.walt.mdoc.objects.document.Document
import kotlinx.io.bytestring.ByteString
import kotlin.io.encoding.Base64
import kotlin.time.Clock
import kotlin.time.Instant

data class IssuerAuthenticationVerification(
    val certificateChain: List<X509Certificate>,
    val signerKey: Key,
    val coseAlgorithm: Int,
)

/**
 * Verifies mdoc issuerAuth possession and included certificate-chain constraints.
 * Trust anchoring remains the responsibility of VICAL or trusted-list policies.
 * Set [validateCertificateConstraints] to false only for compatibility/conformance
 * tooling that intentionally verifies possession without enforcing the document-signer profile.
 */
suspend fun verifyIssuerAuthentication(
    document: Document,
    verificationTime: Instant? = null,
    validateCertificateConstraints: Boolean = true,
): IssuerAuthenticationVerification {
    // ISO 18013-5 places x5chain in the unprotected header. Readers also support
    // the protected-header location used by qualified and older implementations.
    val parsed = document.issuerSigned.getParsedIssuerAuthCrypto2()
    val certificateChain = parsed.x5c.map {
        X509CertificateUtil.parseCertificateDerEncoded(ByteString(Base64.decode(it)))
    }
    require(certificateChain.isNotEmpty()) { "Document signer certificate chain is empty" }
    val issuerAuth = document.issuerSigned.issuerAuth
    val algorithm = issuerAuth.protectedAlgorithm()
    require(issuerAuth.verify(parsed.signerKey, algorithm)) {
        "IssuerAuth COSE_Sign1 signature is invalid"
    }
    if (validateCertificateConstraints) {
        validateDocumentSignerCertificateChain(certificateChain, verificationTime)
    }
    val mso = document.issuerSigned.decodeMobileSecurityObject()
    val leaf = certificateChain.first()
    val signed = mso.validityInfo.signed
    val leafValidity = leaf.data.validity
    require(leafValidity.notBefore <= signed) {
        "MSO signed is before the document signer certificate notBefore"
    }
    require(signed <= leafValidity.notAfter) {
        "MSO signed is after the document signer certificate notAfter"
    }
    return IssuerAuthenticationVerification(certificateChain, parsed.signerKey, algorithm)
}

private suspend fun validateDocumentSignerCertificateChain(
    certificateChain: List<X509Certificate>,
    verificationTime: Instant? = null,
) {
    require(certificateChain.isNotEmpty()) { "Document signer certificate chain is empty" }
    val certificateUtil = verificationTime?.let { issuerAuthenticationCertificateUtil(it) }
        ?: systemClockAuthenticationCertificateUtil
    val validationResult = certificateUtil.validateCertificateChain(certificateChain)
    if (validationResult.hasErrors) {
        val bcExt = certificateChain.first().data.extensionBasicConstraints
        throw IllegalArgumentException(
            "Document signer certificate chain contains errors: ${
                validationResult.errorLog.joinToString("\n") { "${it.subjectDn}: ${it.message}" }
            }")
    }
}

private val systemClockAuthenticationCertificateUtil = issuerAuthenticationCertificateUtil()

private fun issuerAuthenticationCertificateUtil(verificationTime: Instant? = null): X509CertificateUtil =
    X509CertificateUtil {
        removeValidator(X509CertificateSignatureValidator.ID)
        addValidators(
            X509CertificateCaHasKeyCertSignKeyUsageValidator(),
            X509CertificateChainInOrderValidator(),
            object : X509CertificateValidator {
                override val id: String = IsoDocumentSignerX509CertificateProfile.ID

                override suspend fun accepts(
                    context: ValidationContext,
                    x509Certificate: X509Certificate
                ): Boolean = context.isLeaf

                override suspend fun validate(
                    context: ValidationContext,
                    x509Certificate: X509Certificate
                ) = IsoDocumentSignerX509CertificateProfile.validate(context, x509Certificate)
            })

        if (verificationTime != null) {
            addValidators(
                X509CertificateValidityValidator(
                    clock = object : Clock {
                        override fun now(): Instant {
                            return verificationTime
                        }
                    }
                ))
        }
    }