package id.walt.openid4vp.conformance

import com.nimbusds.jose.jwk.ECKey
import id.walt.cose.coseCompliantCbor
import id.walt.mdoc.objects.document.IssuerSigned
import id.walt.openid4vp.conformance.wallet.WalletCredentialIssuer
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.ExperimentalSerializationApi
import java.util.Base64
import org.bouncycastle.asn1.x509.GeneralNames
import id.walt.openid4vp.conformance.fixtures.IssuerTestPki
import id.walt.openid4vp.conformance.testplans.keys.TestKeyMaterial
import org.bouncycastle.asn1.x509.CRLDistPoint
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils
import java.security.cert.CertificateFactory
import java.security.cert.X509CRL
import java.security.cert.X509Certificate
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IssuerTestPkiTest {
    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun issuedMdocMsoDoesNotOutliveItsDocumentSigner() = runTest {
        val fixture = IssuerTestPki("https://fixture.example/issuer.crl")
        val raw = WalletCredentialIssuer().issueMdl(fixture.signerCertificate)
        val signed = coseCompliantCbor.decodeFromByteArray(
            IssuerSigned.serializer(),
            Base64.getUrlDecoder().decode(raw),
        )
        val certificate = CertificateFactory.getInstance("X.509")
            .generateCertificate(fixture.signerCertificate.inputStream()) as X509Certificate
        assertTrue(signed.decodeMobileSecurityObject().validityInfo.validUntil.toEpochMilliseconds() < certificate.notAfter.time)
    }

    @Test
    fun mdocFixtureHasAnAuthenticatedFreshCrlAndDocumentSignerCertificate() {
        val uri = "https://fixture.example/conformance/issuer.crl"
        val fixture = IssuerTestPki(uri)
        val factory = CertificateFactory.getInstance("X.509")
        val root = factory.generateCertificate(fixture.trustAnchorPem.byteInputStream()) as X509Certificate
        val signer = factory.generateCertificate(fixture.signerCertificate.inputStream()) as X509Certificate
        val crl = factory.generateCRL(fixture.crl.inputStream()) as X509CRL

        // JCA checks the signatures independently of the fixture's Bouncy Castle builders.
        root.verify(root.publicKey)
        assertTrue(root.issuerAlternativeNames.isNotEmpty())
        signer.verify(root.publicKey)
        signer.checkValidity()
        crl.verify(root.publicKey)
        assertContentEquals(ECKey.parse(TestKeyMaterial.CREDENTIAL_ISSUER_KEY_WITH_X5C).toECPublicKey().encoded, signer.publicKey.encoded)
        assertEquals(root.subjectX500Principal, signer.issuerX500Principal)
        assertEquals(signer.issuerX500Principal, crl.issuerX500Principal)
        assertEquals(setOf("2.5.29.15", "2.5.29.37"), signer.criticalExtensionOIDs)
        assertEquals(listOf("1.0.18013.5.1.2"), signer.extendedKeyUsage)
        assertTrue(signer.keyUsage[0])
        assertEquals(setOf("2.5.29.20", "2.5.29.35"), crl.nonCriticalExtensionOIDs)
        assertEquals(2, crl.version)
        assertFalse(crl.isRevoked(signer))
        assertTrue(crl.thisUpdate.time <= System.currentTimeMillis())
        assertTrue(crl.nextUpdate.time > System.currentTimeMillis())
        assertTrue(Duration.between(crl.thisUpdate.toInstant(), crl.nextUpdate.toInstant()).toDays() <= 90)

        val points = CRLDistPoint.getInstance(JcaX509ExtensionUtils.parseExtensionValue(signer.getExtensionValue("2.5.29.31")))
        val names = GeneralNames.getInstance(points.distributionPoints.single().distributionPoint.name).names
        assertEquals(GeneralName.uniformResourceIdentifier, names.single().tagNo)
        assertEquals(uri, names.single().name.toString())
    }
}
