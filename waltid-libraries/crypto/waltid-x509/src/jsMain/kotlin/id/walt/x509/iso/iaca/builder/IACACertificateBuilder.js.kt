@file:OptIn(ExperimentalTime::class)

package id.walt.x509.iso.iaca.builder

import id.walt.crypto.keys.Key
import id.walt.x509.iso.IssuerAlternativeName
import id.walt.x509.iso.iaca.certificate.IACACertificateBundle
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

internal actual suspend fun platformSignIACACertificate(
    country: String,
    commonName: String,
    notBefore: Instant,
    notAfter: Instant,
    issuerAlternativeName: IssuerAlternativeName,
    signingKey: Key,
    stateOrProvinceName: String?,
    organizationName: String?,
    crlDistributionPointUri: String?
): IACACertificateBundle {
    TODO("Not yet implemented")
}