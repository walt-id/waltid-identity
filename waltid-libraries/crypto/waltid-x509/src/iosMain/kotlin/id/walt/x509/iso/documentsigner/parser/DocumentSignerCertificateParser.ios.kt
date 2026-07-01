package id.walt.x509.iso.documentsigner.parser

import id.walt.x509.CertificateDer
import id.walt.x509.iso.documentsigner.certificate.DocumentSignerDecodedCertificate

internal actual suspend fun platformParseDocumentSignerCertificate(
    certificate: CertificateDer,
): DocumentSignerDecodedCertificate {
    // TODO(iOS): Implement ISO document signer certificate parsing, then remove the guarded ISO X.509 tests.
    TODO("Not yet implemented")
}
