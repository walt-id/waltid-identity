package id.walt.x509.iso

/**
 * Extended Key Usage OID for ISO Document Signer X.509 certificates.
 */
const val DocumentSignerEkuOID = "1.0.18013.5.1.2"

/**
 * Maximum allowed length of ISO certificate serial numbers, in octets.
 */
const val ISO_CERT_SERIAL_NUMBER_REQUIRED_LENGTH = 20

/**
 * Maximum validity window for Document Signer X.509 certificates in seconds (457 days).
 */
const val DS_CERT_MAX_VALIDITY_SECONDS = 457L * 24 * 60 * 60

/**
 * Maximum validity window for IACA certificates in seconds (20 years).
 *
 * As stated in the specification:
 *
 * The 20-year validity period results from the possibility of
 * using the IACA root certificate for issuing an IDL according to
 * ISO/IEC 18013-3, which allows the use of DS certificates with
 * validity periods up to 15 years. If the IACA root certificate is only
 * used to issue mDLs, a maximum validity period of 9 years is
 * sufficient.
 */
const val IACA_CERT_MAX_VALIDITY_SECONDS = 20L * 365 * 24 * 60 * 60
