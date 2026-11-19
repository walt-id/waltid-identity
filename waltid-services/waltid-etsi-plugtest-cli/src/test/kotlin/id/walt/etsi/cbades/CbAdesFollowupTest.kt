package id.walt.etsi.cbades

import com.github.ajalt.clikt.core.parse
import id.walt.ades.cbades.CbAdesComponents
import id.walt.ades.cbades.CbAdesLabels
import id.walt.ades.cbades.CbAdesSignatureParameters
import id.walt.ades.cbades.CbAdesSigner
import id.walt.ades.cbades.CbAdesSigningContext
import id.walt.ades.cbades.CbAdesValidator
import id.walt.ades.cbades.CoseMessage
import id.walt.ades.cbades.UHeaderElement
import id.walt.ades.cbades.cbor.asArray
import id.walt.ades.cbades.cbor.asBytes
import id.walt.ades.cbades.cbor.asMap
import id.walt.ades.cbades.cbor.asUri
import id.walt.ades.crypto.AdesDigestAlgorithm
import id.walt.ades.validation.AdesLevel
import id.walt.ades.validation.AdesValidationPolicy
import id.walt.ades.validation.Indication
import id.walt.ades.validation.SubIndication
import kotlinx.coroutines.test.runTest
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.nio.file.Files
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

class CbAdesFollowupTest {
    @Test
    fun verifyCommandWiresStrictTimestampsAndAllowsExplicitBasicValidation() = runTest {
        val dir = Files.createTempDirectory("cbades-verify-test").toFile()
        try {
            val pki = LocalPlugtestPki(File(dir, "pki"), "TEST")
            val (anchors, extra) = loadTrust(listOf(File(dir, "pki/trust")))
            val signer = CbAdesSigner(CbAdesSigningContext(timestampSource = pki.timestampSource))
            val credentials = pki.credentials(pki.signer, "test")
            val good = signer.signSign1("payload".encodeToByteArray(), credentials, CbAdesSignatureParameters(level = AdesLevel.B_T))
            val wrong = pki.timestampSource.timestamp(AdesDigestAlgorithm.SHA256, AdesDigestAlgorithm.SHA256.digest("other".encodeToByteArray()))
            val broken = good.withHeaders(good.headers.withUHeaders(listOf(UHeaderElement.of(CbAdesLabels.SIG_TST, CbAdesComponents.tstContainer(listOf(wrong))))))
            val folder = File(dir, "package/TEST").apply { mkdirs() }
            File(folder, "good.cbor").writeBytes(good.encode())
            File(folder, "broken.cbor").writeBytes(broken.encode())
            for (strict in listOf(true, false)) {
                val output = File(dir, "reports-$strict")
                CbAdesVerifyCommand().parse(listOf("--package", folder.parent, "--participant", "REPORTER", "--offline",
                    "--trust", File(dir, "pki/trust").path, "--output", output.path) +
                    if (strict) emptyList() else listOf("--no-strict-timestamps"))
                fun root(code: String) = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                    .parse(File(output, "REPORTER/Verification_of_TEST_$code.xml")).documentElement
                assertEquals("VALID", root("good").tagName)
                assertEquals(if (strict) "INVALID" else "VALID", root("broken").tagName)
                assertTrue(root("broken").textContent.contains("HASH_FAILURE"))
            }
            // Offline validation is driven by the same explicit anchors/CRLs as the command.
            assertEquals(Indication.TOTAL_PASSED, CbAdesValidator(AdesValidationPolicy(anchors, additionalValidationData = extra)).validate(good.encode()).indication)
        } finally {
            dir.walkBottomUp().forEach { Files.delete(it.toPath()) }
        }
    }

    @Test
    fun certifiedAttributeMustBelongToSigner() = runTest {
        val dir = Files.createTempDirectory("cbades-attributes-test").toFile()
        try {
            val pki = LocalPlugtestPki(dir, "TEST")
            val (anchors, extra) = loadTrust(listOf(File(dir, "trust")))
            val validator = CbAdesValidator(AdesValidationPolicy(anchors, additionalValidationData = extra))
            for (holder in listOf(pki.signer, pki.signer2)) {
                val certificate = pki.attributeCertificate(holder.certificate)
                val message = CbAdesSigner().signSign1("payload".encodeToByteArray(), pki.credentials(pki.signer, "test"),
                    CbAdesSignatureParameters(attributeCertificates = listOf(certificate)))
                val result = validator.validate(message.encode())
                if (holder === pki.signer) assertEquals(Indication.TOTAL_PASSED, result.indication)
                else {
                    assertEquals(Indication.INDETERMINATE, result.indication)
                    assertEquals(SubIndication.SIG_CONSTRAINTS_FAILURE, result.subIndication)
                }
            }
            val malformed = CbAdesSigner().signSign1("payload".encodeToByteArray(), pki.credentials(pki.signer, "test"),
                CbAdesSignatureParameters(attributeCertificates = listOf(byteArrayOf(0x30, 0))))
            assertEquals(SubIndication.SIG_CONSTRAINTS_FAILURE, validator.validate(malformed.encode()).subIndication)
        } finally {
            dir.walkBottomUp().forEach { Files.delete(it.toPath()) }
        }
    }

    @Test
    fun selfIssuedNonCaDoesNotBecomeAGeneralTrustAnchor() {
        val dir = Files.createTempDirectory("cbades-trust-test").toFile()
        try {
            val pki = LocalPlugtestPki(File(dir, "pki"), "TEST")
            val aa = X509v3CertificateBuilder(pki.root.holder)
                .replaceExtension(Extension.basicConstraints, true, BasicConstraints(false))
                .build(JcaContentSignerBuilder("SHA256withECDSA").build(pki.root.keyPair.private))
            val file = File(dir, "aa.cer").apply { writeBytes(aa.encoded) }
            val (anchors, extra) = loadTrust(listOf(file, File(dir, "pki/trust")))
            assertEquals(listOf(pki.root.certificate), anchors)
            assertTrue(extra.certificates.any { it.encoded.contentEquals(aa.encoded) })
        } finally {
            dir.walkBottomUp().forEach { Files.delete(it.toPath()) }
        }
    }

    @Test
    fun portalDataUsesCorrectedPolicyOid() = runTest {
        val data = CbAdesPlugtestGenerator.loadData(File("testcases/cbades/Data"))
        assertEquals("urn:oid:0.4.0.19102.1.1.1", data.signaturePolicyId)
        val dir = Files.createTempDirectory("cbades-policy-test").toFile()
        try {
            val pki = LocalPlugtestPki(dir, "TEST")
            val generator = CbAdesPlugtestGenerator(CbAdesSigner(), pki.credentials(pki.signer, "test"), pki, data)
            for (code in listOf("CB-B-B-17", "CB-B-B-18")) {
                val testcase = CbAdesTestCases.parse(File("testcases/cbades/B-B/$code.xml"))
                val message = generator.generate(testcase).message as CoseMessage.Sign1
                val policy = message.headers.protectedMap[CbAdesLabels.SIG_PID]!!.asMap!!
                assertEquals(data.signaturePolicyId, policy[1]!!.asMap!![1]!!.asUri)
                assertContentEquals(AdesDigestAlgorithm.SHA256.digest(data.signaturePolicy), policy[2]!!.asArray!![1].asBytes)
            }
        } finally {
            dir.walkBottomUp().forEach { Files.delete(it.toPath()) }
        }
    }
}
