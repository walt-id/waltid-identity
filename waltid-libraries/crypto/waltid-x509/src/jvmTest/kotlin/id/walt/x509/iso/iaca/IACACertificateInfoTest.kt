@file:OptIn(ExperimentalTime::class)

package id.walt.x509.iso.iaca

import id.walt.x509.iso.IsoSharedTestHarnessValidResources
import id.walt.x509.iso.iaca.certificate.toJcaX500Name
import id.walt.x509.toJcaX509Certificate
import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.decodeHex
import okio.ByteString.Companion.toByteString
import org.bouncycastle.cert.jcajce.JcaX500NameUtil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.ExperimentalTime

class IACACertificateInfoTest {

    @Test
    fun `blocking and suspend certificate info helpers map the same data`() = runTest {
        val profileData = IsoSharedTestHarnessValidResources.iacaProfileData
        val signingKey = IsoSharedTestHarnessValidResources.iacaSecp256r1SigningKey()

        val bundle = IsoSharedTestHarnessValidResources.iacaBuilder.build(
            profileData = profileData,
            signingKey = signingKey,
        )

        val decodedCertificate = bundle.decodedCertificate
        val infoBlocking = decodedCertificate.toIACACertificateInfoBlocking()
        val infoSuspend = decodedCertificate.toIACACertificateInfo()

        assertEquals(
            expected = infoBlocking,
            actual = infoSuspend,
        )

        assertEquals(
            expected = bundle.certificateDer.bytes,
            actual = infoSuspend.certificate,
        )
        assertEquals(
            expected = decodedCertificate.serialNumber,
            actual = infoSuspend.serialNumber,
        )
        assertEquals(
            expected = decodedCertificate.skiHex.decodeHex(),
            actual = infoSuspend.ski,
        )
        assertEquals(
            expected = decodedCertificate.principalName.toJcaX500Name().toString(),
            actual = infoSuspend.issuingAuthority,
        )
        assertEquals(
            expected = decodedCertificate.principalName.country,
            actual = infoSuspend.issuingCountry,
        )
        assertEquals(
            expected = decodedCertificate.principalName.stateOrProvinceName,
            actual = infoSuspend.stateOrProvinceName,
        )
        assertEquals(
            expected = decodedCertificate.validityPeriod.notBefore,
            actual = infoSuspend.notBefore,
        )
        assertEquals(
            expected = decodedCertificate.validityPeriod.notAfter,
            actual = infoSuspend.notAfter,
        )

        val certificate = bundle.certificateDer.toJcaX509Certificate()
        val issuer = requireNotNull(infoSuspend.issuer)
        val subject = requireNotNull(infoSuspend.subject)

        assertEquals(
            expected = JcaX500NameUtil.getIssuer(certificate).encoded.toByteString(),
            actual = issuer,
        )
        assertEquals(
            expected = JcaX500NameUtil.getSubject(certificate).encoded.toByteString(),
            actual = subject,
        )
    }
}
