@file:OptIn(ExperimentalTime::class)

package id.walt.x509.iso.iaca.builder

import id.walt.crypto.keys.Key
import id.walt.x509.iso.CertificateValidityPeriod
import id.walt.x509.iso.IssuerAlternativeName
import id.walt.x509.iso.iaca.certificate.IACACertificateBundle
import kotlin.time.ExperimentalTime

internal actual suspend fun platformSignIACACertificate(
    country: String,
    commonName: String,
    validityPeriod: CertificateValidityPeriod,
    issuerAlternativeName: IssuerAlternativeName,
    signingKey: Key,
    stateOrProvinceName: String?,
    organizationName: String?,
    crlDistributionPointUri: String?
): IACACertificateBundle {
    TODO("Not yet implemented")
}
