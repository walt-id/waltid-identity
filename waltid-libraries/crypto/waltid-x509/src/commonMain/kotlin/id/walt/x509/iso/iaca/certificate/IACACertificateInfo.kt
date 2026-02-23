@file:OptIn(ExperimentalTime::class)

package id.walt.x509.iso.iaca.certificate

import id.walt.x509.X509CertificateHandle
import okio.ByteString
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Snapshot of IACA certificate information aligned with VICAL certificate info fields.
 */
data class IACACertificateInfo(
    val certificate: ByteString,
    val serialNumber: ByteString,
    val ski: ByteString,
    val issuingAuthority: String,
    val issuingCountry: String,
    val stateOrProvinceName: String?,
    val issuer: ByteString?,
    val subject: ByteString?,
    val notBefore: Instant?,
    val notAfter: Instant?,
)

internal data class IACACertificateInfoExtras(
    val issuingAuthority: String,
    val issuer: ByteString,
    val subject: ByteString,
)

internal expect suspend fun platformExtractIACACertificateInfoExtras(
    certificateHandle: X509CertificateHandle,
): IACACertificateInfoExtras
