package id.walt.x509.iso

const val DocumentSignerEkuOID = "1.0.18013.5.1.2"

const val ISO_CERT_SERIAL_NUMBER_REQUIRED_LENGTH = 20

const val DS_CERT_MAX_VALIDITY_SECONDS = 457L * 24 * 60 * 60

/*
The following text is extracted from Table B.1 — "IACA root certificate" of
Section B.1.2 "IACA root certificate" of Annex B of the ISO/IEC 18013-5 specification:

Maximum of 20 years after “notBefore” date.
NOTE The 20-year validity period results from the possibility of
using the IACA root certificate for issuing an IDL according to
ISO/IEC 18013-3, which allows the use of DS certificates with
validity periods up to 15 years. If the IACA root certificate is only
used to issue mDLs, a maximum validity period of 9 years is
sufficient.

So, we choose a maximum of 20 years at the library level and if the application
wants, it can restrict it further.
* */
const val IACA_CERT_MAX_VALIDITY_SECONDS = 20L * 365 * 24 * 60 * 60
