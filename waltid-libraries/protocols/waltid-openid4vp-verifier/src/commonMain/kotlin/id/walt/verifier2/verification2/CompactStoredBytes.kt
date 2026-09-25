package id.walt.verifier2.verification2

import id.walt.credentials.formats.DigitalCredential
import id.walt.credentials.formats.MdocsCredential
import id.walt.credentials.presentations.formats.MsoMdocPresentation
import id.walt.credentials.presentations.formats.VerifiablePresentation
import id.walt.crypto.utils.withByteArraysAsBase64
import kotlinx.serialization.json.JsonObject

/**
 * Rewrites bulk byte arrays in a decoded credential as base64url before it is stored.
 *
 * A CBOR byte string decodes to one JSON number per byte, and every store pays per element. For a 250 KB
 * portrait the two decoded copies a session keeps were 3,230,756 and 3,230,732 bytes, giving a 6.8 MB
 * session; the same image as base64url is 333,354 bytes of BSON, an 8.7x reduction. Measured, including
 * the negative result that a Kotlin `ByteArray` through the standard codec costs exactly as much as the
 * array of numbers, because the codec writes an array of ints rather than binary.
 *
 * Why it matters beyond storage: with byte-string portraits the verifier spent 7.5 cores serialising
 * those copies and throughput was flat at about 8 sessions/s from concurrency 8 to 32, the signature of a
 * bandwidth-bound path. Credentials issued with base64 text claims instead ran 5 to 7 times faster, which
 * is the effect this aims to reproduce for credentials that must use a byte string on the wire - ISO
 * 18013-5 requires one for the portrait element, so the issuer cannot simply switch.
 *
 * Nothing about the wire format or the signature changes. The encoded credential is retained verbatim in
 * the same session, and the conversion is lossless and reversible: only arrays whose every element is an
 * integer in the signed byte range qualify, and the result records the original length.
 */
internal fun DigitalCredential.withCompactByteArrays(): DigitalCredential = when (this) {
    is MdocsCredential -> copy(credentialData = credentialData.withByteArraysAsBase64() as JsonObject)
    else -> this
}

/** The presentation equivalent: an mdoc presentation is a wrapper around one credential. */
internal fun VerifiablePresentation.withCompactByteArrays(): VerifiablePresentation = when (this) {
    is MsoMdocPresentation -> copy(mdoc = mdoc.withCompactByteArrays() as MdocsCredential)
    else -> this
}
