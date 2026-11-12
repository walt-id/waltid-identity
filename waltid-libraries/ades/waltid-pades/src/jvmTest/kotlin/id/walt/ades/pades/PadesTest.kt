package id.walt.ades.pades

import id.walt.ades.asn1.Der
import id.walt.ades.asn1.Oids
import id.walt.ades.cms.CmsSignedData
import id.walt.ades.crypto.AdesDigestAlgorithm
import id.walt.ades.model.Commitment
import id.walt.ades.model.CommitmentType
import id.walt.ades.model.SignaturePolicyIdentifier
import id.walt.ades.test.TestPki
import id.walt.ades.tsp.Rfc3161TimestampClient
import id.walt.ades.tsp.TimeStampToken
import id.walt.ades.validation.AdesLevel
import id.walt.ades.validation.AdesValidationPolicy
import id.walt.ades.validation.CheckStatus
import id.walt.ades.validation.DocumentValidationResult
import id.walt.ades.validation.Indication
import id.walt.ades.validation.SubIndication
import id.walt.ades.validation.ValidationDataCollector
import id.walt.ades.x509.X509Certificate
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.test.runTest
import org.apache.pdfbox.Loader
import org.apache.pdfbox.cos.COSArray
import org.apache.pdfbox.cos.COSDictionary
import org.apache.pdfbox.cos.COSName
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.bouncycastle.asn1.cms.CMSAttributes
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cms.CMSProcessableByteArray
import org.bouncycastle.cms.CMSSignedData
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder
import org.bouncycastle.util.Selector

class PadesTest {
    private val pki = TestPki()
    private val tsa = Rfc3161TimestampClient(pki.tsaUrl, pki.transport)
    private val collector = ValidationDataCollector(pki.transport, listOf(pki.root.certificate))
    private val signer = PadesSigner(tsa, collector)
    private val online = AdesValidationPolicy(trustAnchors = listOf(pki.root.certificate), transport = pki.transport)
    private val offline = AdesValidationPolicy(trustAnchors = listOf(pki.root.certificate))

    private fun samplePdf(text: String = "Hello PAdES"): ByteArray = PDDocument().use { doc ->
        val page = PDPage()
        doc.addPage(page)
        PDPageContentStream(doc, page, PDPageContentStream.AppendMode.OVERWRITE, false).use { cs ->
            cs.beginText()
            cs.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA), 14f)
            cs.newLineAtOffset(72f, 700f)
            cs.showText(text)
            cs.endText()
        }
        ByteArrayOutputStream().also { doc.save(it) }.toByteArray()
    }

    private suspend fun validate(pdf: ByteArray, policy: AdesValidationPolicy = online, allowOther: Boolean = false): DocumentValidationResult =
        PadesValidator(policy, allowOther).validate(pdf)

    private fun DocumentValidationResult.describe() = buildString {
        append("$indication/$subIndication docChecks=$documentChecks\n")
        signatures.forEach { s -> append("  ${s.signatureId}: ${s.indication}/${s.subIndication} level=${s.level}\n"); s.checks.forEach { append("    $it\n") } }
    }

    private fun assertPassed(result: DocumentValidationResult, level: AdesLevel) {
        assertEquals(Indication.TOTAL_PASSED, result.indication, result.describe())
        result.signatures.forEach { assertEquals(level, it.level, result.describe()) }
    }

    private fun documentTimestampCount(pdf: ByteArray) = Loader.loadPDF(pdf).use { doc ->
        doc.signatureDictionaries.count { it.cosObject.getCOSName(COSName.TYPE) == COSName.DOC_TIME_STAMP }
    }

    private fun dssArray(pdf: ByteArray, name: COSName): Int = Loader.loadPDF(pdf).use { doc ->
        ((doc.documentCatalog.cosObject.getDictionaryObject(COSName.DSS) as? COSDictionary)?.getDictionaryObject(name) as? COSArray)?.size() ?: 0
    }

    private fun dssCertificates(pdf: ByteArray): List<X509Certificate> = Loader.loadPDF(pdf).use { DssContent.read(it).certificates }

    private fun cmsOf(pdf: ByteArray, index: Int = 0): CmsSignedData = Loader.loadPDF(pdf).use { doc ->
        CmsSignedData(Der.parseFirst(doc.signatureDictionaries.filter { it.cosObject.getCOSName(COSName.TYPE) != COSName.DOC_TIME_STAMP }[index].contents).encoded)
    }

    @Test
    fun signBaselineB() = runTest {
        val pdf = samplePdf()
        val signed = signer.sign(pdf, pki.credentials(), PadesSignatureParameters(reason = "Approval", location = "Vienna", contactInfo = "sign@walt.id", fieldName = "Approval"))
        assertTrue(signed.size > pdf.size && signed.copyOf(pdf.size).contentEquals(pdf), "incremental update keeps the original bytes")

        Loader.loadPDF(signed).use { doc ->
            val sig = doc.signatureDictionaries.single()
            assertEquals("ETSI.CAdES.detached", sig.subFilter)
            assertEquals("Adobe.PPKLite", sig.filter)
            assertEquals("Approval", sig.reason)
            assertEquals("Vienna", sig.location)
            assertNotNull(sig.signDate)
            assertEquals("Approval", doc.signatureFields.single().partialName)
        }
        val si = cmsOf(signed).signerInfos.single()
        assertNull(si.signedAttribute(Oids.ATTR_SIGNING_TIME), "PAdES uses /M instead of signing-time")
        assertNotNull(si.signedAttribute(Oids.ATTR_SIGNING_CERTIFICATE_V2))
        assertEquals(Oids.CMS_DATA, si.signedAttribute(Oids.ATTR_CONTENT_TYPE)!!.values.single().asOid())
        assertTrue(cmsOf(signed).certificates.contains(pki.signer.certificate))

        // independent check with BouncyCastle
        Loader.loadPDF(signed).use { doc ->
            val sig = doc.signatureDictionaries.single()
            val bc = CMSSignedData(CMSProcessableByteArray(sig.getSignedContent(signed)), Der.parseFirst(sig.getContents(signed)).encoded)
            val bcSigner = bc.signerInfos.signers.single()
            @Suppress("UNCHECKED_CAST")
            val cert = bc.certificates.getMatches(bcSigner.sid as Selector<X509CertificateHolder>).single()
            assertTrue(bcSigner.verify(JcaSimpleSignerInfoVerifierBuilder().build(cert)))
            assertNull(bcSigner.signedAttributes[CMSAttributes.cmsAlgorithmProtect])
        }
        val result = validate(signed)
        assertPassed(result, AdesLevel.B_B)
        assertEquals("Approval", result.signatures.single().signatureId)
        assertTrue(result.signatures.single().checks.any { it.id == "pdf:coverage" && it.status == CheckStatus.PASSED })

        // without revocation data and network access B-B cannot be validated
        assertEquals(SubIndication.TRY_LATER, validate(signed, offline).subIndication)
    }

    @Test
    fun signBaselineTWithSignatureTimestamp() = runTest {
        val signed = signer.sign(samplePdf(), pki.credentials(), PadesSignatureParameters(level = AdesLevel.B_T))
        assertEquals(0, documentTimestampCount(signed))
        assertNotNull(cmsOf(signed).signerInfos.single().unsignedAttributes.singleOrNull { it.oid == Oids.ATTR_SIGNATURE_TIME_STAMP })
        val result = validate(signed)
        assertPassed(result, AdesLevel.B_T)
        assertTrue(result.signatures.single().timestamps.single().passed)
    }

    @Test
    fun signBaselineTWithDocumentTimestamp() = runTest {
        val signed = signer.sign(samplePdf(), pki.credentials(), PadesSignatureParameters(level = AdesLevel.B_T, timestampMode = PadesTimestampMode.DOCUMENT_TIMESTAMP))
        assertEquals(1, documentTimestampCount(signed))
        Loader.loadPDF(signed).use { doc ->
            val ts = doc.signatureDictionaries.last()
            assertEquals("ETSI.RFC3161", ts.subFilter)
            assertNull(ts.signDate)
        }
        val result = validate(signed)
        assertPassed(result, AdesLevel.B_T)
        assertTrue(result.documentChecks.any { it.id.startsWith("pdf:document-timestamp") && it.status == CheckStatus.PASSED }, result.describe())
        assertTrue(result.signatures.single().checks.any { it.id == "pdf:coverage" && it.message.contains("doc_timestamp") }, result.describe())
    }

    @Test
    fun signBaselineLTValidatesOffline() = runTest {
        val signed = signer.sign(samplePdf(), pki.credentials(), PadesSignatureParameters(level = AdesLevel.B_LT))
        assertTrue(dssArray(signed, COSName.CERTS) >= 3 && dssArray(signed, COSName.OCSPS) >= 2, "DSS with chain and OCSP for signer + TSA")
        val certs = dssCertificates(signed)
        assertEquals(certs.size, certs.distinct().size, "no duplicate certificates")
        assertTrue(pki.tsa.certificate in certs && pki.root.certificate in certs)
        Loader.loadPDF(signed).use { doc ->
            val esic = (doc.documentCatalog.cosObject.getDictionaryObject(COSName.EXTENSIONS) as COSDictionary).getDictionaryObject(PdfNames.ESIC) as COSDictionary
            assertEquals(1, esic.getInt(PdfNames.EXTENSION_LEVEL))
        }
        assertPassed(validate(signed, offline), AdesLevel.B_LT)
        assertPassed(validate(signed), AdesLevel.B_LT)
    }

    @Test
    fun signBaselineLTA() = runTest {
        val signed = signer.sign(samplePdf(), pki.credentials(), PadesSignatureParameters(level = AdesLevel.B_LTA))
        assertEquals(1, documentTimestampCount(signed))
        val result = validate(signed, offline)
        assertPassed(result, AdesLevel.B_LTA)
        val sig = result.signatures.single()
        assertTrue(sig.timestamps.size == 2 && sig.timestamps.all { it.passed }, result.describe())
        assertTrue(sig.checks.none { it.status == CheckStatus.FAILED }, result.describe())
    }

    @Test
    fun augmentFromBToLtaStepByStep() = runTest {
        val bb = signer.sign(samplePdf(), pki.credentials())
        assertPassed(validate(bb), AdesLevel.B_B)

        val bt = signer.augment(bb, AdesLevel.B_T)
        assertEquals(1, documentTimestampCount(bt))
        assertPassed(validate(bt), AdesLevel.B_T)
        assertTrue(signer.augment(bt, AdesLevel.B_T).contentEquals(bt), "B-T already reached")

        val blt = signer.augment(bt, AdesLevel.B_LT)
        assertPassed(validate(blt, offline), AdesLevel.B_LT)

        val blta = signer.augment(blt, AdesLevel.B_LTA)
        assertEquals(2, documentTimestampCount(blta))
        val result = validate(blta, offline)
        assertPassed(result, AdesLevel.B_LTA)
        assertTrue(result.signatures.single().checks.none { it.id == "format:pdf-modification" }, result.describe())
    }

    @Test
    fun repeatedLtaAugmentation() = runTest {
        val tsa2Signer = PadesSigner(Rfc3161TimestampClient(pki.tsa2Url, pki.transport), collector)
        val lta1 = signer.sign(samplePdf(), pki.credentials(), PadesSignatureParameters(level = AdesLevel.B_LTA))
        val certsBefore = dssCertificates(lta1)
        assertTrue(pki.tsa2.certificate !in certsBefore)

        val lta2 = tsa2Signer.augment(lta1, AdesLevel.B_LTA)
        assertEquals(2, documentTimestampCount(lta2))
        val lta3 = signer.augment(lta2, AdesLevel.B_LTA)
        assertEquals(3, documentTimestampCount(lta3))

        val certs = dssCertificates(lta3)
        assertEquals(certs.size, certs.distinct().size, "DSS updates must not duplicate certificates")
        assertTrue(pki.tsa2.certificate in certs, "validation data of the previous document time-stamp is added before the next")
        assertTrue(certsBefore.all { it in certs }, "DSS keeps previous values")

        val result = validate(lta3, offline)
        assertPassed(result, AdesLevel.B_LTA)
        assertEquals(4, result.signatures.single().timestamps.size, "signature-time-stamp + 3 document time-stamps")
        assertTrue(result.signatures.single().timestamps.all { it.passed }, result.describe())
        assertEquals(3, result.documentChecks.count { it.id.startsWith("pdf:document-timestamp") && it.status == CheckStatus.PASSED }, result.describe())
    }

    @Test
    fun augmentInPlaceWithSignatureTimestamp() = runTest {
        val bb = signer.sign(samplePdf(), pki.credentials(), PadesSignatureParameters(contentsSize = 20_000))
        val bt = signer.augment(bb, AdesLevel.B_T, PadesAugmentationParameters(timestampMode = PadesTimestampMode.SIGNATURE_TIMESTAMP))
        assertEquals(bb.size, bt.size, "time-stamp written into the reserved /Contents space")
        assertEquals(0, documentTimestampCount(bt))
        assertPassed(validate(bt), AdesLevel.B_T)

        // two untimed signatures: the first one's /Contents is covered by the second, so a document time-stamp is used
        val twice = signer.sign(signer.sign(samplePdf(), pki.credentials()), pki.credentials(pki.secondSigner))
        val fallback = signer.augment(twice, AdesLevel.B_T, PadesAugmentationParameters(timestampMode = PadesTimestampMode.SIGNATURE_TIMESTAMP))
        assertEquals(1, documentTimestampCount(fallback))
        assertPassed(validate(fallback), AdesLevel.B_T)
    }

    @Test
    fun vriAndPolicyAttributes() = runTest {
        val policyDoc = "walt.id test signature policy".encodeToByteArray()
        val policy = SignaturePolicyIdentifier.forDocument("1.3.6.1.4.1.99999.1.1", policyDoc, uri = "https://walt.id/policy")
        val signed = signer.sign(
            samplePdf(), pki.credentials(),
            PadesSignatureParameters(level = AdesLevel.B_LTA, signaturePolicy = policy, commitment = Commitment(CommitmentType.PROOF_OF_APPROVAL.uri), includeVri = true),
        )
        val si = cmsOf(signed).signerInfos.single()
        assertNotNull(si.signedAttribute(Oids.ATTR_SIGNATURE_POLICY_ID))
        assertEquals(CommitmentType.PROOF_OF_APPROVAL.oid, si.signedAttribute(Oids.ATTR_COMMITMENT_TYPE)!!.values.single().child(0).asOid())
        Loader.loadPDF(signed).use { doc ->
            val dss = DssContent.read(doc)
            val contents = doc.signatureDictionaries.first().contents
            assertTrue(AdesDigestAlgorithm.SHA1.digest(contents).hexUpper() in dss.vriKeys, "VRI keyed by SHA-1 of /Contents")
        }
        val result = PadesValidator(offline, signaturePolicyDocuments = mapOf(policy.id to policyDoc)).validate(signed)
        assertPassed(result, AdesLevel.B_LTA)
        assertTrue(result.signatures.single().checks.any { it.id == "cades:signature-policy" && it.status == CheckStatus.PASSED })

        val wrongPolicy = PadesValidator(online, signaturePolicyDocuments = mapOf(policy.id to "other".encodeToByteArray())).validate(signed)
        assertEquals(SubIndication.POLICY_PROCESSING_ERROR, wrongPolicy.subIndication)

        assertFailsWith<IllegalArgumentException> { PadesSignatureParameters(reason = "x", commitment = Commitment(CommitmentType.PROOF_OF_ORIGIN.uri)) }
    }

    @Test
    fun twoSignaturesAndLta() = runTest {
        val first = signer.sign(samplePdf(), pki.credentials(), PadesSignatureParameters(level = AdesLevel.B_T, fieldName = "First"))
        val second = signer.sign(first, pki.credentials(pki.secondSigner), PadesSignatureParameters(level = AdesLevel.B_T, fieldName = "Second"))
        val result = validate(second)
        assertPassed(result, AdesLevel.B_T)
        assertEquals(listOf("First", "Second"), result.signatures.map { it.signatureId })
        assertTrue(result.signatures.first().checks.any { it.id == "pdf:coverage" && it.message.contains("signature") }, result.describe())

        val lta = signer.augment(second, AdesLevel.B_LTA)
        assertPassed(validate(lta, offline), AdesLevel.B_LTA)
    }

    @Test
    fun rsaSignerLta() = runTest {
        val rsa = TestPki(keyType = TestPki.KeyType.RSA_2048)
        val rsaSigner = PadesSigner(Rfc3161TimestampClient(rsa.tsaUrl, rsa.transport), ValidationDataCollector(rsa.transport, listOf(rsa.root.certificate)))
        val signed = rsaSigner.sign(samplePdf(), rsa.credentials(), PadesSignatureParameters(level = AdesLevel.B_LTA))
        assertPassed(PadesValidator(AdesValidationPolicy(trustAnchors = listOf(rsa.root.certificate))).validate(signed), AdesLevel.B_LTA)
    }

    @Test
    fun refusesToSignDocumentCertifiedWithoutChanges() = runTest {
        val certified = Loader.loadPDF(samplePdf()).use { doc ->
            val params = COSDictionary().apply { setInt(COSName.P, 1) }
            val reference = COSDictionary().apply {
                setName(COSName.getPDFName("TransformMethod"), "DocMDP")
                setItem(COSName.getPDFName("TransformParams"), params)
            }
            val certification = COSDictionary().apply { setItem(COSName.getPDFName("Reference"), COSArray(listOf(reference))) }
            doc.documentCatalog.cosObject.setItem(COSName.PERMS, COSDictionary().apply { setItem(COSName.getPDFName("DocMDP"), certification) })
            ByteArrayOutputStream().also { doc.save(it) }.toByteArray()
        }
        assertFailsWith<IllegalStateException> { signer.sign(certified, pki.credentials()) }
    }

    @Test
    fun bytesModifiedAfterSigning() = runTest {
        val signed = signer.sign(samplePdf(), pki.credentials())
        val text = "Hello PAdES".encodeToByteArray()
        val at = signed.indexOf(text)
        assertTrue(at > 0)
        val tampered = signed.copyOf().also { it[at] = 'J'.code.toByte() }
        val result = validate(tampered)
        assertEquals(Indication.TOTAL_FAILED, result.indication, result.describe())
        assertEquals(SubIndication.HASH_FAILURE, result.subIndication)
    }

    @Test
    fun incrementalModificationAfterSigningIsFlagged() = runTest {
        val signed = signer.sign(samplePdf(), pki.credentials(), PadesSignatureParameters(level = AdesLevel.B_T))
        val modified = Loader.loadPDF(signed).use { doc ->
            val page = doc.getPage(0)
            PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, false).use { cs ->
                cs.beginText(); cs.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA), 14f); cs.newLineAtOffset(72f, 600f)
                cs.showText("Pay 1000 EUR"); cs.endText()
            }
            page.cosObject.isNeedToBeUpdated = true
            ByteArrayOutputStream().also { doc.saveIncremental(it) }.toByteArray()
        }
        val result = validate(modified)
        assertEquals(Indication.TOTAL_FAILED, result.indication, result.describe())
        assertEquals(SubIndication.FORMAT_FAILURE, result.subIndication)
        assertTrue(result.signatures.single().checks.any { it.id == "format:pdf-modification" && it.message.contains("page") }, result.describe())

        val lenient = validate(modified, allowOther = true)
        assertEquals(Indication.TOTAL_PASSED, lenient.indication, lenient.describe())
        assertTrue(lenient.signatures.single().warnings.any { it.id == "pdf:modification" })

        // appended garbage outside any revision
        val garbage = validate(signed + "\n1 0 obj\n<<>>\nendobj\n".encodeToByteArray())
        assertEquals(SubIndication.FORMAT_FAILURE, garbage.subIndication, garbage.describe())
    }

    @Test
    fun wrongSigningCertificateReference() = runTest {
        val signed = signer.sign(samplePdf(), pki.credentials(), PadesSignatureParameters(), pki.secondSigner.certificate)
        val result = validate(signed)
        assertEquals(Indication.INDETERMINATE, result.indication, result.describe())
        assertEquals(SubIndication.NO_SIGNING_CERTIFICATE_FOUND, result.subIndication)
        assertTrue(result.signatures.single().checks.any { it.id == "cades:signing-certificate" && it.status == CheckStatus.FAILED })
    }

    @Test
    fun revokedSigner() = runTest {
        val signed = signer.sign(samplePdf(), pki.credentials(pki.revokedSigner), PadesSignatureParameters(level = AdesLevel.B_T))
        val result = validate(signed)
        assertEquals(Indication.INDETERMINATE, result.indication, result.describe())
        assertEquals(SubIndication.REVOKED_NO_POE, result.subIndication)
    }

    @Test
    fun expiredSigner() = runTest {
        val signed = signer.sign(samplePdf(), pki.credentials(pki.expiredSigner))
        val result = validate(signed)
        assertEquals(Indication.INDETERMINATE, result.indication, result.describe())
        assertTrue(result.subIndication in setOf(SubIndication.OUT_OF_BOUNDS_NO_POE, SubIndication.OUT_OF_BOUNDS_NOT_REVOKED), result.describe())
    }

    @Test
    fun brokenSignatureTimestamp() = runTest {
        val signed = signer.sign(samplePdf(), pki.credentials(), PadesSignatureParameters(level = AdesLevel.B_T))
        val broken = rewriteContents(signed, 0) { der ->
            val token = CmsSignedData(der).signerInfos.single().unsignedAttributes.single { it.oid == Oids.ATTR_SIGNATURE_TIME_STAMP }.values.single().encoded
            flip(der, TimeStampToken(token).signerInfo.signature)
        }
        val lenient = validate(broken)
        val sig = lenient.signatures.single()
        assertTrue(sig.timestamps.single().indication == Indication.FAILED, lenient.describe())
        assertTrue(sig.warnings.any { it.id.startsWith("timestamp:") }, lenient.describe())
        assertEquals(sig.validationTime, sig.bestSignatureTime, "a broken time-stamp provides no POE")

        val strict = validate(broken, AdesValidationPolicy(trustAnchors = listOf(pki.root.certificate), transport = pki.transport, strictTimestamps = true))
        assertEquals(Indication.TOTAL_FAILED, strict.indication, strict.describe())
        assertEquals(SubIndication.SIG_CRYPTO_FAILURE, strict.subIndication)
    }

    @Test
    fun brokenDocumentTimestamp() = runTest {
        val signed = signer.sign(samplePdf(), pki.credentials(), PadesSignatureParameters(level = AdesLevel.B_LTA))
        val broken = rewriteContents(signed, 1) { der -> flip(der, TimeStampToken(der).signerInfo.signature) }
        val result = validate(broken, offline)
        assertTrue(result.documentChecks.any { it.id.startsWith("pdf:document-timestamp") && it.status == CheckStatus.WARNING }, result.describe())
        assertTrue(result.signatures.single().timestamps.any { it.indication == Indication.FAILED }, result.describe())

        val strict = validate(broken, AdesValidationPolicy(trustAnchors = listOf(pki.root.certificate), strictTimestamps = true))
        assertEquals(Indication.TOTAL_FAILED, strict.indication, strict.describe())
    }

    @Test
    fun bestSignatureTimeFromSignatureTimestamp() = runTest {
        val signed = signer.sign(samplePdf(), pki.credentials(), PadesSignatureParameters(level = AdesLevel.B_T))
        val sig = validate(signed).signatures.single()
        assertTrue(sig.bestSignatureTime <= sig.validationTime && sig.validationTime - sig.bestSignatureTime < 5.minutes)
    }

    /** Replaces the DER object in /Contents of the [index]-th signature field (ByteRange order) without touching signed bytes. */
    private fun rewriteContents(pdf: ByteArray, index: Int, mutate: (ByteArray) -> ByteArray): ByteArray {
        val br = Loader.loadPDF(pdf).use { doc -> doc.signatureDictionaries.sortedBy { it.byteRange[2] }[index].byteRange }
        val start = br[1] + 1
        val hex = String(pdf, start, br[2] - 1 - start, Charsets.ISO_8859_1)
        val raw = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val der = Der.parseFirst(raw).encoded
        val newHex = mutate(der).hexUpper().padEnd(hex.length, '0')
        return pdf.copyOf().also { newHex.encodeToByteArray().copyInto(it, start) }
    }

    private fun flip(data: ByteArray, part: ByteArray): ByteArray {
        val at = data.indexOf(part)
        assertTrue(at >= 0)
        return data.copyOf().also { it[at + part.size - 1] = (it[at + part.size - 1].toInt() xor 0x01).toByte() }
    }

    private fun ByteArray.indexOf(needle: ByteArray): Int {
        outer@ for (i in 0..size - needle.size) {
            for (j in needle.indices) if (this[i + j] != needle[j]) continue@outer
            return i
        }
        return -1
    }
}
