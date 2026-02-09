@file:OptIn(ExperimentalTime::class)

package id.walt.x509.iso

import id.walt.x509.X509KeyUsage
import id.walt.x509.iso.iaca.certificate.IACACertificateProfileData
import id.walt.x509.iso.iaca.certificate.IACADecodedCertificate
import okio.ByteString
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime

fun assertIACABuilderDataEqualsCertificateData(
    profileData: IACACertificateProfileData,
    decodedCert: IACADecodedCertificate,
) {

    assertEquals(
        expected = profileData.principalName,
        actual = decodedCert.principalName,
    )

    assertEquals(
        expected = profileData.validityPeriod.notAfter.epochSeconds,
        actual = decodedCert.validityPeriod.notAfter.epochSeconds,
    )

    assertEquals(
        expected = profileData.validityPeriod.notBefore.epochSeconds,
        actual = decodedCert.validityPeriod.notBefore.epochSeconds,
    )

    assertEquals(
        expected = profileData.issuerAlternativeName,
        actual = decodedCert.issuerAlternativeName,
    )

    assertEquals(
        expected = profileData.crlDistributionPointUri,
        actual = decodedCert.crlDistributionPointUri,
    )

    assertEquals(
        expected = true,
        actual = decodedCert.basicConstraints.isCA,
    )

    assertEquals(
        expected = 0,
        actual = decodedCert.basicConstraints.pathLengthConstraint,
    )

    assertEquals(
        expected = setOf(X509KeyUsage.CRLSign, X509KeyUsage.KeyCertSign),
        actual = decodedCert.keyUsage,
    )
}

val ByteString.bitLength
    get() = this.size * 8

fun assertBuildersSerialNoCompliance(
    serialNo: ByteString,
) {

    assertTrue(isBigIntegerPositive(serialNo), "Serial number must be positive")

    assertFalse(isBigIntegerZero(serialNo), "Serial number must not be zero")

    assertTrue(serialNo.bitLength <= 160, "Serial number must not exceed 20 bytes (160 bits)")

    assertTrue(serialNo.bitLength >= 63, "Serial number must contain at least 63 bits of entropy")

}