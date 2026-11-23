package id.walt.ades

import id.walt.ades.crypto.AdesDigestAlgorithm
import id.walt.ades.crypto.AdesSignatureAlgorithm
import id.walt.ades.test.TestEntity
import id.walt.ades.test.TestPki
import id.walt.ades.tsp.Rfc3161TimestampClient
import id.walt.ades.validation.AdesSignatureValidator
import id.walt.ades.validation.AdesValidationPolicy
import id.walt.ades.validation.CertificateChainValidator
import id.walt.ades.validation.Indication
import id.walt.ades.validation.RevocationStatus
import id.walt.ades.validation.SignatureEvidence
import id.walt.ades.validation.TimestampEvidence
import id.walt.ades.validation.TimestampType
import id.walt.ades.validation.ValidationData
import id.walt.ades.validation.ValidationDataCollector
import id.walt.ades.validation.ValidationDataResolver
import id.walt.ades.x509.OcspCertId
import id.walt.ades.x509.OcspResponse
import id.walt.ades.x509.X509Crl
import kotlinx.coroutines.test.runTest
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.IssuingDistributionPoint
import org.bouncycastle.asn1.x509.KeyPurposeId
import org.bouncycastle.asn1.x509.ReasonFlags
import org.bouncycastle.cert.X509CRLHolder
import org.bouncycastle.cert.X509v2CRLBuilder
import org.bouncycastle.cert.ocsp.BasicOCSPRespBuilder
import org.bouncycastle.cert.ocsp.CertificateStatus
import org.bouncycastle.cert.ocsp.OCSPReq
import org.bouncycastle.cert.ocsp.RespID
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlin.time.toJavaInstant

class RevocationEvidenceTest {
    private val pki = TestPki()
    private val anchors = listOf(pki.root.certificate)

    @Test
    fun respectsCrlCertificateClassAndRejectsPartialReasons() = runTest {
        val resolver = ValidationDataResolver(ValidationData(), null)
        fun scoped(caOnly: Boolean = false, userOnly: Boolean = false, partial: Boolean = false): X509Crl {
            val builder = X509v2CRLBuilder(X509CRLHolder(pki.crl(pki.issuingCa)))
            builder.addExtension(Extension.issuingDistributionPoint, true,
                IssuingDistributionPoint(null, userOnly, caOnly, if (partial) ReasonFlags(ReasonFlags.keyCompromise) else null, false, false))
            return X509Crl(builder.build(JcaContentSignerBuilder("SHA256withECDSA").build(pki.issuingCa.keyPair.private)).encoded)
        }
        assertIs<RevocationStatus.Good>(resolver.crlStatus(scoped(userOnly = true), pki.signer.certificate, pki.issuingCa.certificate))
        assertNull(resolver.crlStatus(scoped(caOnly = true), pki.signer.certificate, pki.issuingCa.certificate))
        assertNull(resolver.crlStatus(scoped(partial = true), pki.signer.certificate, pki.issuingCa.certificate))
        assertNull(resolver.crlStatus(scoped(userOnly = true), pki.issuingCa.certificate, pki.root.certificate))
        assertIs<RevocationStatus.Good>(resolver.crlStatus(X509Crl(pki.crl(pki.issuingCa)), pki.signer.certificate, pki.issuingCa.certificate))
    }

    @Test
    fun delegatedResponderMustBeValidAndNotRevokedAtProduction() = runTest {
        val expired = pki.issue("CN=Expired Responder", pki.issuingCa, eku = KeyPurposeId.id_kp_OCSPSigning,
            ocspNoCheck = true, notBefore = pki.now - 30.days, notAfter = pki.now - 1.days)
        val checked = pki.issue("CN=Checked Responder", pki.issuingCa, eku = KeyPurposeId.id_kp_OCSPSigning)
        val goodCrl = X509Crl(pki.crl(pki.issuingCa, pki.now - 1.hours))
        val resolver = ValidationDataResolver(ValidationData(crls = listOf(goodCrl)), null)
        assertNull(resolver.ocspStatus(response(expired), pki.signer.certificate, pki.issuingCa.certificate))
        assertIs<RevocationStatus.Good>(resolver.ocspStatus(response(checked), pki.signer.certificate, pki.issuingCa.certificate))
        assertNull(ValidationDataResolver(ValidationData(), null).ocspStatus(response(checked), pki.signer.certificate, pki.issuingCa.certificate))
        pki.revoke(checked, pki.now - 2.hours)
        val revoked = ValidationDataResolver(ValidationData(crls = listOf(X509Crl(pki.crl(pki.issuingCa)))), null)
        assertNull(revoked.ocspStatus(response(checked), pki.signer.certificate, pki.issuingCa.certificate))
    }

    @Test
    fun currentEvidenceCannotBeExpiredOrFromTheFuture() {
        val policy = AdesValidationPolicy(anchors)
        val fresh = CertificateChainValidator(ValidationDataResolver(ValidationData(), null, anchors), policy).freshAt(pki.now)
        assertTrue(fresh(RevocationStatus.Good("response", pki.now - 1.hours, pki.now + 1.hours)))
        assertFalse(fresh(RevocationStatus.Good("response", pki.now - 1.hours, pki.now - 1.hours)))
        assertFalse(fresh(RevocationStatus.Good("response", pki.now + 1.hours, pki.now + 1.days)))
        assertFalse(fresh(RevocationStatus.Good("response", pki.now - 2.days, null)))
    }

    @Test
    fun followsAKeyRolloverWithTheSameCaName() = runTest {
        val rollover = pki.issue(pki.root.name, pki.root, ca = true)
        val leaf = pki.issue("CN=Rollover signer", rollover)
        val data = ValidationData(certificates = listOf(leaf.certificate, rollover.certificate),
            crls = listOf(X509Crl(pki.crl(rollover)), X509Crl(pki.crl(pki.root))))
        val resolver = ValidationDataResolver(data, null, anchors)
        assertEquals(listOf(leaf.certificate, rollover.certificate, pki.root.certificate), resolver.buildChains(leaf.certificate).single())
        assertTrue(CertificateChainValidator(resolver, AdesValidationPolicy(anchors)).validate(leaf.certificate, pki.now).passed)
        val collected = ValidationDataCollector(null, anchors).collect(listOf(leaf.certificate), data)
        assertTrue(collected.missing.isEmpty(), collected.missing.toString())
    }

    @Test
    fun revokedLeafDoesNotHideMissingOrRevokedIntermediateEvidence() = runTest {
        val time = pki.revocationTime - 1.days
        pki.tsaClock = { time }
        val bytes = "proof".encodeToByteArray()
        val token = Rfc3161TimestampClient(pki.tsaUrl, pki.transport).timestamp(AdesDigestAlgorithm.SHA256, AdesDigestAlgorithm.SHA256.digest(bytes))
        // Timestamp chain has complete evidence supplied separately; omit the root CRL only from the signature pool.
        val evidence = SignatureEvidence("signature", emptyList(), pki.revokedSigner.certificate, AdesSignatureAlgorithm.ES256, true,
            certificates = listOf(pki.issuingCa.certificate), crls = listOf(X509Crl(pki.crl(pki.issuingCa))),
            signatureTimestamps = listOf(TimestampEvidence(TimestampType.SIGNATURE, token, bytes)))
        val unavailable = AdesSignatureValidator(AdesValidationPolicy(anchors)).validate(evidence)
        assertFalse(unavailable.isValid)
        assertTrue(unavailable.timestamps.none { it.passed })
        val valid = AdesSignatureValidator(AdesValidationPolicy(anchors, transport = pki.transport)).validate(evidence)
        assertEquals(Indication.TOTAL_PASSED, valid.indication)
        pki.revoke(pki.issuingCa, time - 1.hours)
        val revoked = AdesSignatureValidator(AdesValidationPolicy(anchors, transport = pki.transport)).validate(evidence)
        assertFalse(revoked.isValid)
    }

    @Test
    fun intermediateExpiryCanUseAValidHistoricalTimestamp() = runTest {
        val ca = pki.issue("CN=Short-lived CA", pki.root, ca = true, notBefore = pki.now - 30.days, notAfter = pki.now + 1.hours)
        val leaf = pki.issue("CN=Long-lived signer", ca)
        val bytes = "proof".encodeToByteArray()
        val token = Rfc3161TimestampClient(pki.tsaUrl, pki.transport).timestamp(AdesDigestAlgorithm.SHA256, AdesDigestAlgorithm.SHA256.digest(bytes))
        val data = ValidationData(certificates = listOf(ca.certificate, pki.issuingCa.certificate),
            crls = listOf(X509Crl(pki.crl(ca)), X509Crl(pki.crl(pki.root))))
        val evidence = SignatureEvidence("signature", emptyList(), leaf.certificate, AdesSignatureAlgorithm.ES256, true,
            certificates = data.certificates, crls = data.crls,
            signatureTimestamps = listOf(TimestampEvidence(TimestampType.SIGNATURE, token, bytes)))
        val result = AdesSignatureValidator(AdesValidationPolicy(anchors, validationTime = pki.now + 2.hours, transport = pki.transport)).validate(evidence)
        assertTrue(result.isValid, result.toString())
        assertTrue(result.timestamps.single().passed)
    }

    private suspend fun response(responder: TestEntity, produced: Instant = pki.now): OcspResponse {
        val request = OCSPReq(OcspResponse.request(OcspCertId.of(pki.signer.certificate, pki.issuingCa.certificate)))
        val builder = BasicOCSPRespBuilder(RespID(responder.holder.subject))
        builder.addResponse(request.requestList.single().certID, CertificateStatus.GOOD,
            Date.from(produced.toJavaInstant()), Date.from((produced + 1.days).toJavaInstant()))
        return OcspResponse(builder.build(JcaContentSignerBuilder("SHA256withECDSA").build(responder.keyPair.private),
            arrayOf(responder.holder), Date.from(produced.toJavaInstant())).encoded)
    }
}
