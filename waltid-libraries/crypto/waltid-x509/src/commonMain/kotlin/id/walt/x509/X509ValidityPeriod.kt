package id.walt.x509

import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * Validity window for X.509 certificates.
 *
 * @param notBefore Inclusive lower bound of the X.509 certificate's validity window.
 * @param notAfter Exclusive upper bound of the X.509 certificate's validity window.
 */
@Serializable
data class X509ValidityPeriod(
    val notBefore: Instant,
    val notAfter: Instant,
)
