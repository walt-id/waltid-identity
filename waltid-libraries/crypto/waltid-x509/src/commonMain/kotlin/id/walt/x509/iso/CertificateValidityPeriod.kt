@file:OptIn(ExperimentalTime::class)

package id.walt.x509.iso

import kotlin.time.ExperimentalTime
import kotlin.time.Instant

data class CertificateValidityPeriod(
    val notBefore: Instant,
    val notAfter: Instant,
)
