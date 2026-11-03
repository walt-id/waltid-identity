package id.walt.ades

import id.walt.ades.crypto.AdesDigestAlgorithm
import id.walt.ades.crypto.AdesSignatureAlgorithm
import id.walt.ades.test.TestEntity
import id.walt.ades.test.TestPki
import id.walt.ades.tsp.Rfc3161TimestampClient
import id.walt.ades.tsp.TimeStampToken
import id.walt.ades.validation.AdesSignatureValidator
import id.walt.ades.validation.AdesValidationPolicy
import id.walt.ades.validation.Indication
import id.walt.ades.validation.CertificateChainValidator
import id.walt.ades.validation.ValidationData
import id.walt.ades.validation.ValidationDataResolver
import id.walt.ades.x509.X509Certificate
import id.walt.ades.x509.OcspCertId
import id.walt.ades.x509.OcspResponse
import id.walt.ades.validation.RevocationStatus
import id.walt.ades.validation.SignatureEvidence
import id.walt.ades.validation.SubIndication
import id.walt.ades.validation.TimestampEvidence
import id.walt.ades.validation.TimestampType
import kotlinx.coroutines.test.runTest
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.DERSet
import org.bouncycastle.asn1.cms.Attribute
import org.bouncycastle.asn1.cms.AttributeTable
import org.bouncycastle.asn1.ess.ESSCertIDv2
import org.bouncycastle.asn1.ess.SigningCertificateV2
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.ExtendedKeyUsage
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.KeyPurposeId
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaCertStore
import org.bouncycastle.cms.CMSProcessableByteArray
import org.bouncycastle.cms.CMSSignedDataGenerator
import org.bouncycastle.cms.DefaultSignedAttributeTableGenerator
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder
import org.bouncycastle.cert.ocsp.OCSPResp
import org.bouncycastle.cert.ocsp.BasicOCSPResp
import org.bouncycastle.asn1.ocsp.BasicOCSPResponse
import org.bouncycastle.asn1.DERSequence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

class AdesSecurityTest {
    private val pki = TestPki()
    private val data = "signature value".encodeToByteArray()

    @Test
    fun rejectsNonTsaProofOfExistenceIncludingRevokedSelfTimestamp() = runTest {
        pki.tsaClock = { pki.revocationTime - 1.days }
        val legitimate = TimeStampToken(Rfc3161TimestampClient(pki.tsaUrl, pki.transport)
            .timestamp(AdesDigestAlgorithm.SHA256, AdesDigestAlgorithm.SHA256.digest(data)))
        val policy = AdesValidationPolicy(trustAnchors = listOf(pki.root.certificate), transport = pki.transport)
        for (authority in listOf(pki.signer, pki.revokedSigner)) {
            val token = forgeToken(legitimate, authority)
            assertTrue(TimeStampToken(token).verify(data).signatureValid)
            assertFalse(TimeStampToken(token).verify(data).isValid)
            val result = AdesSignatureValidator(policy).validate(evidence(authority, token))
            assertEquals(SubIndication.CHAIN_CONSTRAINTS_FAILURE, result.timestamps.single().subIndication)
            assertFalse(result.timestamps.single().passed)
            assertTrue(legitimate.genTime < result.bestSignatureTime)
            if (authority == pki.revokedSigner) assertEquals(SubIndication.REVOKED_NO_POE, result.subIndication)
        }
    }

    @Test
    fun requiresCriticalExclusiveTimestampingEku() = runTest {
        val original = TimeStampToken(Rfc3161TimestampClient(pki.tsaUrl, pki.transport)
            .timestamp(AdesDigestAlgorithm.SHA256, AdesDigestAlgorithm.SHA256.digest(data)))
        assertTrue(original.verify(data).isValid)
        for ((critical, purposes) in listOf(
            false to arrayOf(KeyPurposeId.id_kp_timeStamping),
            true to arrayOf(KeyPurposeId.id_kp_timeStamping, KeyPurposeId.id_kp_clientAuth),
        )) {
            val holder = X509v3CertificateBuilder(pki.tsa.holder)
                .replaceExtension(Extension.extendedKeyUsage, critical, ExtendedKeyUsage(purposes))
                .build(JcaContentSignerBuilder("SHA256withECDSA").build(pki.issuingCa.keyPair.private))
            val authority = TestEntity("malformed TSA", pki.tsa.keyPair, holder)
            val verification = TimeStampToken(forgeToken(original, authority)).verify(data)
            assertTrue(verification.signatureValid)
            assertFalse(verification.isValid)
        }
    }

    @Test
    fun defaultPolicyDoesNotTrustSelfSignedSigner() = runTest {
        val result = AdesSignatureValidator(AdesValidationPolicy()).validate(evidence(pki.root))
        assertEquals(SubIndication.NO_CERTIFICATE_CHAIN_FOUND, result.subIndication)
        val trusted = AdesSignatureValidator(AdesValidationPolicy(trustAnchors = listOf(pki.root.certificate)))
            .validate(evidence(pki.root))
        assertEquals(Indication.TOTAL_PASSED, trusted.indication)
    }

    @Test
    fun rejectsNonCaIntermediate() = runTest {
        val holder = X509v3CertificateBuilder(pki.issuingCa.holder)
            .replaceExtension(Extension.basicConstraints, true, BasicConstraints(false))
            .build(JcaContentSignerBuilder("SHA256withECDSA").build(pki.root.keyPair.private))
        val policy = AdesValidationPolicy(trustAnchors = listOf(pki.root.certificate))
        val pool = ValidationData(certificates = listOf(pki.signer.certificate, X509Certificate(holder.encoded), pki.root.certificate))
        val result = CertificateChainValidator(ValidationDataResolver(pool, null, policy.trustAnchors), policy)
            .validate(pki.signer.certificate, pki.now)
        assertEquals(SubIndication.CERTIFICATE_CHAIN_GENERAL_FAILURE, result.subIndication)
    }

    @Test
    fun ignoresOcspResponderWithoutSigningEku() = runTest {
        val request = OcspResponse.request(OcspCertId.of(pki.signer.certificate, pki.issuingCa.certificate, AdesDigestAlgorithm.SHA256))
        val response = OcspResponse(pki.transport.post("http://pki.test.walt.id/ocsp", "application/ocsp-request", request))
        val authorized = ValidationDataResolver(ValidationData(), null)
        assertTrue(authorized.ocspStatus(response, pki.signer.certificate, pki.issuingCa.certificate) is RevocationStatus.Good)
        val holder = X509v3CertificateBuilder(pki.ocspResponder.holder).removeExtension(Extension.extendedKeyUsage)
            .build(JcaContentSignerBuilder("SHA256withECDSA").build(pki.issuingCa.keyPair.private))
        // Keep the response signature and responder key intact, but replace its embedded responder certificate.
        val basic = OCSPResp(response.encoded).responseObject as BasicOCSPResp
        val raw = BasicOCSPResponse.getInstance(basic.encoded)
        val changed = BasicOCSPResponse(raw.tbsResponseData, raw.signatureAlgorithm, raw.signature,
            DERSequence(holder.toASN1Structure())).getEncoded("DER")
        val pool = ValidationData(certificates = listOf(pki.signer.certificate, pki.issuingCa.certificate, pki.root.certificate), ocsps = listOf(OcspResponse(changed)))
        val policy = AdesValidationPolicy(trustAnchors = listOf(pki.root.certificate))
        val resolver = ValidationDataResolver(pool, null, policy.trustAnchors)
        assertNull(resolver.ocspStatus(OcspResponse(changed), pki.signer.certificate, pki.issuingCa.certificate))
        assertEquals(SubIndication.TRY_LATER, CertificateChainValidator(resolver, policy).validate(pki.signer.certificate, pki.now).subIndication)
    }

    private fun evidence(signer: TestEntity, token: ByteArray? = null) = SignatureEvidence(
        signatureId = "signer", checks = emptyList(), signingCertificate = signer.certificate,
        signatureAlgorithm = AdesSignatureAlgorithm.ES256, signatureValueValid = true,
        certificates = listOf(signer.certificate, pki.issuingCa.certificate, pki.root.certificate),
        signatureTimestamps = token?.let { listOf(TimestampEvidence(TimestampType.SIGNATURE, it, data)) } ?: emptyList(),
    )

    // Bypass BouncyCastle's TSA certificate checks deliberately to model a malicious token issuer.
    private suspend fun forgeToken(original: TimeStampToken, authority: TestEntity): ByteArray {
        val attribute = Attribute(PKCSObjectIdentifiers.id_aa_signingCertificateV2,
            DERSet(SigningCertificateV2(ESSCertIDv2(AdesDigestAlgorithm.SHA256.digest(authority.der)))))
        val signer = JcaSignerInfoGeneratorBuilder(JcaDigestCalculatorProviderBuilder().build())
            .setSignedAttributeGenerator(DefaultSignedAttributeTableGenerator(AttributeTable(attribute)))
            .build(JcaContentSignerBuilder("SHA256withECDSA").build(authority.keyPair.private), authority.holder)
        return CMSSignedDataGenerator().apply {
            addSignerInfoGenerator(signer)
            addCertificates(JcaCertStore(listOf(authority.holder)))
        }.generate(CMSProcessableByteArray(ASN1ObjectIdentifier("1.2.840.113549.1.9.16.1.4"), original.tstInfo.encoded), true).toASN1Structure().getEncoded("DER")
    }
}
