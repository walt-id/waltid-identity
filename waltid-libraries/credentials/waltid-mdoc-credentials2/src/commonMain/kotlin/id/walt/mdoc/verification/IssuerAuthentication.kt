package id.walt.mdoc.verification

import id.walt.cose.protectedAlgorithm
import id.walt.cose.verify
import id.walt.crypto2.keys.Key
import id.walt.mdoc.objects.document.Document
import id.walt.x509.CertificateDer
import id.walt.x509.X509ValidationException
import id.walt.x509.validateCertificateAuthorityUsage
import id.walt.x509.validateDocumentSigningCertificateUsage
import id.walt.x509.verifyOrderedCertificateChainSignatures
import kotlin.io.encoding.Base64
import kotlin.time.Clock
import kotlin.time.Instant

data class IssuerAuthenticationVerification(
    val certificateChain: List<CertificateDer>,
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
    verificationTime: Instant = Clock.System.now(),
    validateCertificateConstraints: Boolean = true,
): IssuerAuthenticationVerification {
    // ISO 18013-5 places x5chain in the unprotected header. Readers also support
    // the protected-header location used by qualified and older implementations.
    val parsed = document.issuerSigned.getParsedIssuerAuthCrypto2()
    val certificateChain = parsed.x5c.map { CertificateDer(Base64.Default.decode(it)) }
    require(certificateChain.isNotEmpty()) { "Document signer certificate chain is empty" }
    val issuerAuth = document.issuerSigned.issuerAuth
    val algorithm = issuerAuth.protectedAlgorithm()
    require(issuerAuth.verify(parsed.signerKey, algorithm)) {
        "IssuerAuth COSE_Sign1 signature is invalid"
    }
    if (validateCertificateConstraints) {
        validateDocumentSignerCertificateChain(certificateChain, verificationTime)
    }
    return IssuerAuthenticationVerification(certificateChain, parsed.signerKey, algorithm)
}

fun validateDocumentSignerCertificateChain(
    certificateChain: List<CertificateDer>,
    verificationTime: Instant = Clock.System.now(),
) {
    require(certificateChain.isNotEmpty()) { "Document signer certificate chain is empty" }
    try {
        certificateChain.first().validateDocumentSigningCertificateUsage(verificationTime)
        certificateChain.drop(1).forEach { it.validateCertificateAuthorityUsage(verificationTime) }
        verifyOrderedCertificateChainSignatures(certificateChain)
    } catch (cause: X509ValidationException) {
        throw IllegalArgumentException(cause.message, cause)
    }
}
