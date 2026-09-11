package id.walt.certificate.x509.revocation

import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.X509CertificateUtil
import id.walt.certificate.x509.extension.AuthorityKeyIdentifierExtension.Companion.extensionAuthorityKeyIdentifier
import id.walt.certificate.x509.extension.BasicConstraintsExtension.Companion.extensionBasicConstraints
import id.walt.certificate.x509.extension.KeyUsageExtension.Companion.extensionKeyUsage
import id.walt.certificate.x509.extension.KeyUsageExtension.KeyUsage
import id.walt.certificate.x509.extension.SubjectKeyIdentifierExtension.Companion.extensionSubjectKeyIdentifier
import id.walt.crypto2.CryptoRuntime
import kotlinx.coroutines.CancellationException
import kotlinx.io.bytestring.ByteString
import kotlin.time.Clock
import kotlin.time.Instant

/** Revocation evidence for one certificate, independently of its path's trust decision. */
sealed interface CrlCertificateStatus {
    /** A supported, current, authenticated complete CRL does not contain the certificate. */
    data class Good(val thisUpdate: Instant, val nextUpdate: Instant) : CrlCertificateStatus

    /** A supported, current, authenticated CRL contains the certificate's serial number. */
    data class Revoked(val revocationTime: Instant, val reasonCode: Int?) : CrlCertificateStatus

    /** No conclusive status was established. This must not be interpreted as Good. */
    data class Indeterminate(val reason: CrlFailure) : CrlCertificateStatus
}

/** Stable failure categories without provider errors or untrusted certificate text. */
enum class CrlFailure {
    INVALID_DER, UNSUPPORTED_PROFILE, STALE, ISSUER_MISMATCH, INVALID_ISSUER, INVALID_SIGNATURE, UNAVAILABLE,
}

/**
 * Verifies complete, direct X.509 v2 CRLs using RFC 5280 section 5.
 *
 * Supports ECDSA and RSA PKCS#1 signatures with SHA-256/384/512. Delta CRLs, issuing-distribution-point
 * restrictions, indirect CRLs and unrecognised critical extensions remain indeterminate. Inputs are
 * bounded to 2 MiB and 10,000 entries. Issuer names must have identical DER encodings; equivalent
 * names with different encodings are conservatively rejected. No network access or caching occurs.
 *
 * The caller selects an issuer from its validated path. A Good status does not establish path trust.
 * The same operation can explicitly check a configured CA certificate, including a self-signed CA.
 */
class CertificateRevocationListVerifier(
    private val cryptoRuntime: CryptoRuntime = X509CertificateUtil.services.cryptoRuntime,
) {
    suspend fun verify(
        crlDer: ByteString,
        certificate: X509Certificate,
        issuer: X509Certificate,
        at: Instant = Clock.System.now(),
    ): CrlCertificateStatus = try {
        val crl = parseCompleteCrl(crlDer)
        requireCrl(crl.thisUpdate <= at && at < crl.nextUpdate, CrlFailure.STALE)
        requireCrl(
            crl.issuer == issuer.data.subjectDnRaw && certificate.data.issuerDnRaw == issuer.data.subjectDnRaw,
            CrlFailure.ISSUER_MISMATCH,
        )
        requireCrl(
            issuer.data.extensionBasicConstraints?.cA == true &&
                KeyUsage.cRLSign in (issuer.data.extensionKeyUsage?.keyPurposeIdList ?: emptySet()) &&
                at >= issuer.data.validity.notBefore && at <= issuer.data.validity.notAfter,
            CrlFailure.INVALID_ISSUER,
        )
        val issuerKeyId = issuer.data.extensionSubjectKeyIdentifier?.keyIdentifier
        requireCrl(issuerKeyId != null && crl.authorityKeyIdentifier == issuerKeyId, CrlFailure.ISSUER_MISMATCH)
        certificate.data.extensionAuthorityKeyIdentifier?.keyIdentifier?.let {
            requireCrl(it == issuerKeyId, CrlFailure.ISSUER_MISMATCH)
        }
        requireCrl(
            X509CertificateUtil.services.signatureValidator.validateCertificateSignature(
                cryptoRuntime, issuer.data.subjectPublicKeyInfo, certificate,
            ),
            CrlFailure.INVALID_ISSUER,
        )
        val key = issuer.restoreSubjectPublicKey(cryptoRuntime)
        val verifier = key.capabilities.verifier
        requireCrl(verifier != null, CrlFailure.UNAVAILABLE)
        requireCrl(
            verifier!!.verify(crl.tbs.toByteArray(), crl.signature.toByteArray(), crl.algorithm),
            CrlFailure.INVALID_SIGNATURE,
        )
        val serial = normaliseSerial(certificate.data.serialNumberRaw.toByteArray())
        crl.revoked[serial]?.let { CrlCertificateStatus.Revoked(it.time, it.reason) }
            ?: CrlCertificateStatus.Good(crl.thisUpdate, crl.nextUpdate)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: CrlValidationException) {
        CrlCertificateStatus.Indeterminate(failure.reason)
    } catch (_: Throwable) {
        CrlCertificateStatus.Indeterminate(CrlFailure.UNAVAILABLE)
    }
}

internal class CrlValidationException(val reason: CrlFailure) : Exception(reason.name)

internal fun requireCrl(condition: Boolean, reason: CrlFailure = CrlFailure.INVALID_DER) {
    if (!condition) throw CrlValidationException(reason)
}
