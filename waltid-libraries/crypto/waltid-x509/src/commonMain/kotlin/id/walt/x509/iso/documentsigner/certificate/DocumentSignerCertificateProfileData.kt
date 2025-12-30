@file:OptIn(ExperimentalTime::class)

package id.walt.x509.iso.documentsigner.certificate

import id.walt.x509.X509ValidityPeriod
import kotlin.time.ExperimentalTime

/**
 * ISO profile input data used to build a Document Signer X.509 certificate.
 *
 * This is the minimal, platform-independent set of fields required by the
 * specification's profile.
 *
 * @param principalName X.500 subject name for the Document Signer X.509 certificate.
 * @param validityPeriod Validity window of the X.509 certificate.
 * @param crlDistributionPointUri CRL distribution point URI.
 */
data class DocumentSignerCertificateProfileData(
    val principalName: DocumentSignerPrincipalName,
    val validityPeriod: X509ValidityPeriod,
    val crlDistributionPointUri: String,
)
