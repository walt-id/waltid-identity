package id.walt.certificate.x509.validation

import id.walt.certificate.x509.SignatureValidator
import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.extension.AuthorityKeyIdentifierExtension.Companion.extensionAuthorityKeyIdentifier
import id.walt.certificate.x509.extension.SubjectKeyIdentifierExtension.Companion.extensionSubjectKeyIdentifier
import id.walt.crypto2.CryptoRuntime

/**
 * The outcome of choosing the issuer of a certificate among trusted certificates that share the
 * certificate's issuer DN.
 */
sealed interface IssuerSelection {

    /** No trusted certificate has the issuer DN. */
    data object NotFound : IssuerSelection

    /** Exactly one issuer was identified. */
    data class Selected(val issuer: X509Certificate) : IssuerSelection

    /**
     * There are trusted certificates with the issuer DN, but none of them is the issuer: none
     * verifies the signature of the certificate, or (for a self signed certificate) none is the
     * same certificate.
     */
    data class NoMatch(val candidates: List<X509Certificate>) : IssuerSelection

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
 *    "first wins"). This includes two certificates for the same key; that case is deliberately not
 *    resolved here.
 *
 * Without a [signatureValidator], several candidates can only be told apart by the first two rules.
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
            val samePinned = candidates.filter { it.fingerprintSha256 == certificate.fingerprintSha256 }
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

        for (group in listOf(preferred, others)) {
            if (group.isEmpty()) continue
            when (val result = selectBySignature(certificate, group)) {
                is IssuerSelection.NoMatch -> continue
                else -> return result
            }
        }
        return IssuerSelection.NoMatch(candidates)
    }

    private suspend fun selectBySignature(
        certificate: X509Certificate,
        group: List<X509Certificate>
    ): IssuerSelection {
        if (signatureValidator == null) {
            return if (group.size == 1) IssuerSelection.Selected(group.first()) else IssuerSelection.Ambiguous(group)
        }
        val verifying = group.filter {
            signatureValidator.validateCertificateSignature(
                cryptoRuntime,
                it.data.subjectPublicKeyInfo,
                certificate
            )
        }
        return when (verifying.size) {
            0 -> IssuerSelection.NoMatch(group)
            1 -> IssuerSelection.Selected(verifying.first())
            else -> IssuerSelection.Ambiguous(verifying)
        }
    }
}
