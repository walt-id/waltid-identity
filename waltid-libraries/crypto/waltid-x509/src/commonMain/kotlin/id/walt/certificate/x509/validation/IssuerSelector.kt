package id.walt.certificate.x509.validation

import id.walt.certificate.x509.SignatureValidator
import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.extension.AuthorityKeyIdentifierExtension.Companion.extensionAuthorityKeyIdentifier
import id.walt.certificate.x509.extension.SubjectKeyIdentifierExtension.Companion.extensionSubjectKeyIdentifier
import id.walt.crypto2.CryptoRuntime
import kotlin.coroutines.cancellation.CancellationException

/**
 * The outcome of choosing the issuer of a certificate among trusted certificates that share the
 * certificate's issuer DN.
 */
sealed interface IssuerSelection {

    /** No trusted certificate has the issuer DN. */
    data object NotFound : IssuerSelection

    /**
     * Exactly one issuer was identified.
     *
     * [signatureVerified] is true if the selection already verified the certificate's signature with
     * the issuer's key (several candidates were told apart that way), so the caller need not repeat it.
     */
    data class Selected(val issuer: X509Certificate, val signatureVerified: Boolean = false) : IssuerSelection

    /**
     * There are trusted certificates with the issuer DN, but none of them is the issuer: none
     * verifies the signature of the certificate, or (for a self signed certificate) none is the
     * same certificate.
     *
     * [verificationErrors] holds, per candidate whose signature check threw instead of returning a
     * result, the candidate's fingerprint and the error, so the cause is not lost when the real issuer
     * could not be checked (e.g. an algorithm the platform does not support).
     */
    data class NoMatch(
        val candidates: List<X509Certificate>,
        val verificationErrors: List<String> = emptyList()
    ) : IssuerSelection

    /**
     * More than one trusted certificate qualifies as the issuer. Callers must fail closed instead
     * of picking one of them.
     */
    data class Ambiguous(val candidates: List<X509Certificate>) : IssuerSelection
}

/**
 * Selects the issuer of a certificate when several trusted certificates share its issuer DN, e.g.
 * two CAs of a key rollover (RFC 5280 section 4.2.1.1 identifies the signing key by AKI/SKI for
 * exactly this reason).
 *
 * With zero or one certificate for the issuer DN the result is the same as before multiple CAs were
 * supported: nothing is checked here, the caller's own signature validation decides.
 *
 * With several candidates:
 * 1. A self signed certificate in the chain is matched by fingerprint (it must *be* the trusted
 *    certificate).
 * 2. Candidates whose Subject Key Identifier equals the certificate's Authority Key Identifier are
 *    tried first. This is only a preference: a candidate without a Subject Key Identifier, or with a
 *    different one, is still tried if no preferred candidate verifies, because the signature - not
 *    the key identifier metadata - is what proves the issuer.
 * 3. A group of candidates is accepted if exactly one of them verifies the certificate's signature.
 *    If more than one verifies, the result is [IssuerSelection.Ambiguous] (fail closed, never
 *    "first wins").
 * 4. Whichever way a candidate was selected, any other candidate for the *same public key* (e.g. a CA
 *    re-issued with a new serial number) makes the result [IssuerSelection.Ambiguous] too: both
 *    would verify the signature, and they may differ in constraints. This is checked across both
 *    groups of rule 2, so it does not depend on which candidates carry a Subject Key Identifier.
 *    That case is deliberately not resolved here.
 *
 * Without a [signatureValidator], several candidates can only be told apart by the first two rules.
 *
 * Known limitations (all fail closed with [IssuerSelection.Ambiguous], none selects a wrong issuer):
 * - Trusted certificates with the same subject DN and the same public key are never told apart, even
 *   if they also have the same serial number (e.g. a CA re-issued with identical name and serial but
 *   different validity or extensions). They differ only in their fingerprint, and the selection does
 *   not judge which of them is the better anchor. Pin only one of them.
 * - Rollover link certificates (the new CA key certified by the old CA key, with the same subject DN
 *   and public key as the new self signed CA) are not supported as trust anchors next to the new CA
 *   for the same reason. Pin the self signed CAs of the rollover instead.
 */
class IssuerSelector(
    private val cryptoRuntime: CryptoRuntime,
    private val signatureValidator: SignatureValidator?
) {

    suspend fun select(
        certificate: X509Certificate,
        candidates: List<X509Certificate>
    ): IssuerSelection {
        if (candidates.isEmpty()) return IssuerSelection.NotFound
        if (candidates.size == 1) return IssuerSelection.Selected(candidates.first())

        if (certificate.data.subjectDn == certificate.data.issuerDn) {
            val fingerprint = certificate.fingerprintSha256
            val samePinned = candidates.filter { it.fingerprintSha256 == fingerprint }
            return when (samePinned.size) {
                0 -> IssuerSelection.NoMatch(candidates)
                else -> IssuerSelection.Selected(samePinned.first())
            }
        }

        val authorityKeyId = certificate.data.extensionAuthorityKeyIdentifier?.keyIdentifier
            ?.takeIf { it.size > 0 }
        val (preferred, others) = if (authorityKeyId == null) {
            emptyList<X509Certificate>() to candidates
        } else {
            candidates.partition { it.data.extensionSubjectKeyIdentifier?.keyIdentifier == authorityKeyId }
        }

        val verificationErrors = mutableListOf<String>()
        for (group in listOf(preferred, others)) {
            if (group.isEmpty()) continue
            when (val result = selectBySignature(certificate, group)) {
                is IssuerSelection.NoMatch -> verificationErrors += result.verificationErrors
                is IssuerSelection.Selected -> return rejectSameKeyDuplicates(result, candidates)
                else -> return result
            }
        }
        return IssuerSelection.NoMatch(candidates, verificationErrors)
    }

    private fun rejectSameKeyDuplicates(
        selected: IssuerSelection.Selected,
        candidates: List<X509Certificate>
    ): IssuerSelection {
        val keyId = selected.issuer.data.subjectPublicKeyInfo.keyId
        val sameKey = candidates.filter { it.data.subjectPublicKeyInfo.keyId == keyId }
        return if (sameKey.size > 1) IssuerSelection.Ambiguous(sameKey) else selected
    }

    /**
     * Whether [certificate] is signed with the key of [candidate]. A candidate whose key cannot verify
     * the signature at all - typically one of another key type, e.g. an RSA CA that rolled over to an EC
     * CA with the same subject DN - makes the signature validator throw; for the selection that is
     * simply "not the issuer", and it can only ever remove a candidate, never add one. The error is
     * recorded in [verificationErrors] so that it can be reported if no candidate verifies.
     */
    private suspend fun verifies(
        signatureValidator: SignatureValidator,
        candidate: X509Certificate,
        certificate: X509Certificate,
        verificationErrors: MutableList<String>
    ): Boolean = try {
        signatureValidator.validateCertificateSignature(cryptoRuntime, candidate.data.subjectPublicKeyInfo, certificate)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        verificationErrors += "${candidate.fingerprintSha256Hex}: ${e::class.simpleName}: ${e.message}"
        false
    }

    private suspend fun selectBySignature(
        certificate: X509Certificate,
        group: List<X509Certificate>
    ): IssuerSelection {
        if (signatureValidator == null) {
            return if (group.size == 1) IssuerSelection.Selected(group.first()) else IssuerSelection.Ambiguous(group)
        }
        val verificationErrors = mutableListOf<String>()
        val verifying = group.filter { verifies(signatureValidator, it, certificate, verificationErrors) }
        return when (verifying.size) {
            0 -> IssuerSelection.NoMatch(group, verificationErrors)
            1 -> IssuerSelection.Selected(verifying.first(), signatureVerified = true)
            else -> IssuerSelection.Ambiguous(verifying)
        }
    }
}
