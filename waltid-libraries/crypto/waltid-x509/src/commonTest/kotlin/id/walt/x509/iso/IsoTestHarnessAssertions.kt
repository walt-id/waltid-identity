package id.walt.x509.iso

import id.walt.x509.X509KeyUsage
import id.walt.x509.iso.iaca.certificate.IACACertificateProfileData
import id.walt.x509.iso.iaca.certificate.IACADecodedCertificate
import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.toHexString
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue


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

