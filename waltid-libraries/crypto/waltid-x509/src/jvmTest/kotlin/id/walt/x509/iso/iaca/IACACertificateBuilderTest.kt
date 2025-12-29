@file:OptIn(ExperimentalTime::class)

package id.walt.x509.iso.iaca

import com.nimbusds.jose.util.X509CertUtils
import id.walt.x509.iso.IsoSharedTestHarnessValidResources
import id.walt.x509.iso.assertBuildersSerialNoCompliance
import id.walt.x509.iso.assertIACABuilderDataEqualsCertificateData
import id.walt.x509.iso.iaca.builder.IACACertificateBuilder
import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.toByteString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime

/*
The "main" purpose of this test is to check that the data that was input in the
builder actually end up in the DER-encoded certificate
* */

class IACACertificateBuilderTest {

    @Test
    fun `builder generates valid IACA root certificate`() = runTest {

        val iacaCertBuilder = IACACertificateBuilder()
        val iacaCertBundle = iacaCertBuilder.build(
            profileData = IsoSharedTestHarnessValidResources.iacaProfileData,
            signingKey = IsoSharedTestHarnessValidResources.iacaSecp256r1SigningKey(),
        )

        assertIACABuilderDataEqualsCertificateData(
            profileData = IsoSharedTestHarnessValidResources.iacaProfileData,
            decodedCert = iacaCertBundle.decodedCertificate,
        )

        val cert = X509CertUtils.parse(iacaCertBundle.certificateDer.bytes)

        assertBuildersSerialNoCompliance(
            serialNo = cert.serialNumber.toByteArray().toByteString(),
        )


        assertEquals(IsoSharedTestHarnessValidResources.iacaProfileData.principalName.country, cert.issuerX500Principal.name.substringAfter("C=").take(2))
        assertEquals(cert.subjectX500Principal, cert.issuerX500Principal) // self-signed
        assertEquals(cert.basicConstraints, 0) // Is a CA
        assertTrue(cert.keyUsage[5]) // keyCertSign
        assertTrue(cert.keyUsage[6]) // cRLSign
        assertNotNull(cert.issuerAlternativeNames)
        assertEquals(cert.issuerAlternativeNames.size, 2)
        assertTrue(cert.issuerAlternativeNames.any { it[1] == IsoSharedTestHarnessValidResources.iacaProfileData.issuerAlternativeName.uri })
        assertTrue(cert.issuerAlternativeNames.any { it[1] == IsoSharedTestHarnessValidResources.iacaProfileData.issuerAlternativeName.email })

//        // === CRL Distribution Point URI check ===
//        val crlBytes = cert.getExtensionValue(Extension.cRLDistributionPoints.id)
//        val crlOctet = ASN1OctetString.getInstance(crlBytes).octets
//        val crlDist = CRLDistPoint.getInstance(ASN1Primitive.fromByteArray(crlOctet))
//        val distPoints = crlDist.distributionPoints
//        assertTrue(
//            actual = distPoints.isNotEmpty(),
//            message = "CRL distribution point must be present",
//        )
//        assertEquals(distPoints.size, 1)
//        val uri = distPoints[0].distributionPoint.name as GeneralNames
//        val uriName = uri.names!!.find { it.tagNo == GeneralName.uniformResourceIdentifier }
//        val crlUriValue = (uriName!!.name as DERIA5String).string
//        assertEquals(
//            expected = iacaCertBundle.decodedCertificate.crlDistributionPointUri,
//            actual = crlUriValue,
//            message = "CRL distribution point URI must match expected value",
//        )
    }

}
