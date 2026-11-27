package id.walt.mdoc.issuance

import id.walt.certificate.x509.X509Certificate
import id.walt.mdoc.objects.mso.ValidityInfo
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

fun roundedMdocValidity(
    now: Instant,
    certificateValidity: X509Certificate.Validity,
    validFrom: Instant?,
    validUntil: Instant?,
): ValidityInfo {
    val base = roundMdocTimestampDownToTwelveHours(now)
    // A newly valid certificate must not appear to have signed before its validity began.
    val signed = truncateMdocTDate(maxOf(base, certificateValidity.notBefore.ceilToSecond()))
    return ValidityInfo(
        signed = signed,
        // ISO mdoc tdates are whole UTC seconds; truncate before the issuer encodes the MSO.
        validFrom = truncateMdocTDate(validFrom ?: signed),
        validUntil = truncateMdocTDate(validUntil ?: (base + 365.days)),
    )
}

/** Round down to a 12-hour UTC boundary to reduce linkability. */
fun roundMdocTimestampDownToTwelveHours(instant: Instant): Instant {
    val windowSeconds = 43_200L
    return Instant.fromEpochSeconds(instant.epochSeconds.floorDiv(windowSeconds) * windowSeconds)
}

fun requireMdocSignedWithinCertificateValidity(signed: Instant, validity: X509Certificate.Validity) {
    require(validity.notBefore <= signed) {
        "MSO signed is before the document signer certificate notBefore"
    }
    require(signed <= validity.notAfter) {
        "MSO signed is after the document signer certificate notAfter"
    }
}

fun truncateMdocTDate(instant: Instant): Instant = Instant.fromEpochSeconds(instant.epochSeconds)

private fun Instant.ceilToSecond(): Instant =
    Instant.fromEpochSeconds(epochSeconds + if (nanosecondsOfSecond == 0) 0L else 1L)
