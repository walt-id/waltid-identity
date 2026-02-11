package id.walt.x509.iso.iaca.parser

import id.walt.x509.CertificateDer
import id.walt.x509.iso.blockingBridge
import id.walt.x509.iso.iaca.certificate.IACADecodedCertificate

/**
 * Parser for IACA X.509 certificates.
 *
 * Parsing does not enforce ISO profile validations, it decodes the relevant data from the X.509 certificate.
 *
 * Use [id.walt.x509.iso.iaca.validate.IACAValidator] if you
 * need to enforce profile compliance and proper validation of decoded X.509 certificate's data fields.
 */
class IACACertificateParser {

    /**
     * Parse a DER-encoded IACA X.509 certificate into a decoded representation.
     */
    suspend fun parse(certificate: CertificateDer) = platformParseIACACertificate(certificate)

    /**
     * Blocking variant of [parse].
     */
    fun parseBlocking(certificate: CertificateDer): IACADecodedCertificate = blockingBridge {
        parse(certificate)
    }
}

/**
 * Platform-specific parsing implementation for IACA X.509 certificates.
 */
internal expect suspend fun platformParseIACACertificate(
    certificate: CertificateDer,
): IACADecodedCertificate
