@file:OptIn(ExperimentalTime::class)

package id.walt.x509

import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Validity window for X.509 certificates.
 *
 * @param notBefore Inclusive lower bound of the X.509 certificate's validity window.
 * @param notAfter Exclusive upper bound of the X.509 certificate's validity window.
 */
data class X509ValidityPeriod(
    val notBefore: Instant,
    val notAfter: Instant,
)