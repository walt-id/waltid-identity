package id.walt.openid4vci.handlers.credential

import id.walt.certificate.x509.X509Certificate
import id.walt.mdoc.objects.mso.ValidityInfo
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

internal fun roundedMdocValidity(
    now: Instant,
    certificateValidity: X509Certificate.Validity,
    validFrom: Instant?,
    validUntil: Instant?,
): ValidityInfo {
    // Round down to 12-hour UTC windows to reduce linkability.
    val windowSeconds = 43_200L
    val base = Instant.fromEpochSeconds(now.epochSeconds.floorDiv(windowSeconds) * windowSeconds)
    // A newly valid certificate must not appear to have signed before its validity began.
    val signed = maxOf(base, certificateValidity.notBefore.ceilToSecond())
    return ValidityInfo(
        signed = signed,
        validFrom = validFrom ?: signed,
        validUntil = validUntil ?: (base + 365.days),
    )
}

private fun Instant.ceilToSecond(): Instant =
    Instant.fromEpochSeconds(epochSeconds + if (nanosecondsOfSecond == 0) 0L else 1L)
