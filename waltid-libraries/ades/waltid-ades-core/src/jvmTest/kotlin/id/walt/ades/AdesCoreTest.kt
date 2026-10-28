package id.walt.ades

import id.walt.ades.crypto.AdesDigestAlgorithm
import id.walt.ades.test.TestPki
import id.walt.ades.tsp.Rfc3161TimestampClient
import id.walt.ades.tsp.TimeStampToken
import id.walt.ades.validation.AdesValidationPolicy
import id.walt.ades.validation.CertificateChainValidator
import id.walt.ades.validation.Indication
import id.walt.ades.validation.SubIndication
import id.walt.ades.validation.ValidationData
import id.walt.ades.validation.ValidationDataCollector
import id.walt.ades.validation.ValidationDataResolver
import id.walt.ades.x509.KeyUsageBit
import id.walt.ades.x509.Pem
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock

class AdesCoreTest {
    private val pki = TestPki()

    @Test
    fun parsesCertificatesFromBouncyCastle() = runTest {
        val cert = pki.signer.certificate
        assertEquals("C=AT,O=walt.id,CN=Test Signer", cert.subject.toString()) // RFC 4514: reverse of DER order
        assertEquals(pki.issuingCa.certificate.subject, cert.issuer)
        assertEquals(setOf(KeyUsageBit.DIGITAL_SIGNATURE, KeyUsageBit.NON_REPUDIATION), cert.keyUsage)
        assertEquals(listOf("http://pki.test.walt.id/ocsp"), cert.ocspResponderUrls)
        assertEquals(listOf("http://pki.test.walt.id/crl/issuing.crl"), cert.crlDistributionPoints)
        assertTrue(cert.isIssuedBy(pki.issuingCa.certificate))
        assertTrue(pki.issuingCa.certificate.isCa)
        assertTrue(pki.ocspResponder.certificate.hasOcspNoCheck)
    }

    @Test
    fun parsesRealWorldPlugtestCertificates() = runTest {
        val ca = Pem.certificates(resource("etsi-plugtest-2026/infocert-ec-ca-4-cl.cer")).single()
        assertTrue(ca.isSelfIssued && ca.isCa)
        assertTrue(ca.isIssuedBy(ca), "self-signature with P-384 ECDSA must verify")
        assertEquals("10e583f100e523d8e78b3a4e7da67a1ee7e623915da0ce98000f1a661c93734f", ca.digest().toHexString())
        val tsa = Pem.certificates(resource("etsi-plugtest-2026/infocert-tsa-ec-4-cl.cer")).single()
        // the "Time Stamping Authority EC 4 CL" certificate is the issuing CA of the InfoCert TSA units
        assertTrue(tsa.isCa && tsa.isSelfIssued && tsa.isIssuedBy(tsa))
    }

    @Test
    fun timestampRoundTrip() = runTest {
        val client = Rfc3161TimestampClient(pki.tsaUrl, pki.transport)
        val data = "hello".encodeToByteArray()
        val token = TimeStampToken(client.timestamp(AdesDigestAlgorithm.SHA256, AdesDigestAlgorithm.SHA256.digest(data)))
        val v = token.verify(data = data)
        assertTrue(v.isValid, v.problems.toString())
        assertEquals(pki.tsa.certificate, v.signerCertificate)
        assertTrue(!token.verify(data = "other".encodeToByteArray()).isValid)
    }

    @Test
    fun chainValidationWithOnlineRevocation() = runTest {
        val policy = AdesValidationPolicy(trustAnchors = listOf(pki.root.certificate), transport = pki.transport)
        fun validator() = CertificateChainValidator(ValidationDataResolver(ValidationData(), pki.transport, policy.trustAnchors), policy)
        val now = Clock.System.now()

        val ok = validator().validate(pki.signer.certificate, now)
        assertEquals(Indication.PASSED, ok.indication, ok.message)
        assertEquals(3, ok.chain.size)

        val revoked = validator().validate(pki.revokedSigner.certificate, now)
        assertEquals(SubIndication.REVOKED_NO_POE, revoked.subIndication, revoked.message)

        val expired = validator().validate(pki.expiredSigner.certificate, now)
        assertTrue(expired.subIndication == SubIndication.OUT_OF_BOUNDS_NOT_REVOKED || expired.subIndication == SubIndication.OUT_OF_BOUNDS_NO_POE, expired.message)

        val untrusted = CertificateChainValidator(ValidationDataResolver(ValidationData(), pki.transport, listOf(pki.tsa.certificate)), policy)
            .validate(pki.signer.certificate, now)
        assertEquals(SubIndication.NO_CERTIFICATE_CHAIN_FOUND, untrusted.subIndication)
    }

    @Test
    fun collectsValidationDataForSignerAndTsa() = runTest {
        val collected = ValidationDataCollector(pki.transport, listOf(pki.root.certificate))
            .collect(listOf(pki.signer.certificate, pki.tsa.certificate), ValidationData())
        assertTrue(collected.missing.isEmpty(), collected.missing.toString())
        val certs = collected.required.certificates
        assertTrue(pki.issuingCa.certificate in certs && pki.root.certificate in certs && pki.ocspResponder.certificate in certs)
        assertTrue(collected.required.ocsps.size >= 2, "OCSP for signer and TSA")
        assertTrue(collected.required.crls.isNotEmpty(), "CRL for the issuing CA (issued by root)")
        assertNotNull(collected.required.ocsps.first().producedAt)
    }

    private fun resource(name: String): ByteArray = javaClass.classLoader.getResourceAsStream(name)!!.readBytes()
}
