package id.walt.ades.pades

import id.walt.ades.asn1.Der
import id.walt.ades.cms.CmsSignedData
import id.walt.ades.test.TestPki
import id.walt.ades.tsp.Rfc3161TimestampClient
import id.walt.ades.tsp.TimeStampToken
import id.walt.ades.tsp.TimestampSource
import id.walt.ades.tsp.TimestampException
import id.walt.ades.validation.AdesLevel
import id.walt.ades.validation.AdesValidationPolicy
import id.walt.ades.validation.Indication
import java.awt.Color
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.apache.pdfbox.Loader
import org.apache.pdfbox.cos.COSArray
import org.apache.pdfbox.cos.COSObject
import org.apache.pdfbox.cos.COSDictionary
import org.apache.pdfbox.cos.COSName
import org.apache.pdfbox.cos.COSStream
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.PDResources
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceDictionary
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceStream
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureOptions
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm
import org.apache.pdfbox.pdmodel.interactive.form.PDSignatureField
import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.DERSet
import org.bouncycastle.asn1.cms.Attribute
import org.bouncycastle.asn1.cms.CMSAttributes
import org.bouncycastle.asn1.cms.ContentInfo
import org.bouncycastle.asn1.cms.SignedData
import org.bouncycastle.asn1.cms.SignerInfo

/** Regression cases from the PDF modification review. */
class PadesModificationTest {
    private val pki = TestPki()
    private val signer = PadesSigner(Rfc3161TimestampClient(pki.tsaUrl, pki.transport))
    private val validator = PadesValidator(AdesValidationPolicy(listOf(pki.root.certificate), transport = pki.transport))

    private fun appendText(pdf: ByteArray): ByteArray = Loader.loadPDF(pdf).use { doc ->
        val page = doc.getPage(0)
        PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, false).use { cs ->
            cs.beginText(); cs.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA), 14f)
            cs.newLineAtOffset(72f, 600f); cs.showText("Pay 1000 EUR"); cs.endText()
        }
        page.cosObject.isNeedToBeUpdated = true
        ByteArrayOutputStream().also { doc.saveIncremental(it) }.toByteArray()
    }

    @Test
    fun dssReachabilityCannotHideAnAppendedContentChange() = runTest {
        val original = signer.sign(samplePdf("Original approved content"), pki.credentials())
        val control = appendText(original)
        val controlResult = validator.validate(control)
        assertEquals(Indication.TOTAL_FAILED, controlResult.indication)
        for (entry in listOf(COSName.getPDFName("ReviewPage"), COSName.VRI)) {
            val linked = Loader.loadPDF(original).use { doc ->
                val page = doc.getPage(0).cosObject
                val key = page.key ?: doc.document.xrefTable.keys.first { doc.document.getObjectFromPool(it).`object` === page }
                val dss = COSDictionary().apply { setItem(entry, doc.document.getObjectFromPool(key)) }
                doc.documentCatalog.cosObject.setItem(COSName.DSS, dss)
                doc.documentCatalog.cosObject.isNeedToBeUpdated = true
                ByteArrayOutputStream().also { doc.saveIncremental(it) }.toByteArray()
            }
            val changed = appendText(linked)
            val result = validator.validate(changed)
        assertEquals(Indication.TOTAL_FAILED, result.indication, result.toString())
        }
    }

    @Test
    fun unsignedSignatureWidgetCannotOverlaySignedPage() = runTest {
        val original = signer.sign(samplePdf("Original approved content"), pki.credentials())
        for (fakeTimestamp in listOf(false, true)) {
            val changed = Loader.loadPDF(original).use { doc ->
                val page = doc.getPage(0)
                val form = PDAcroForm(doc, doc.documentCatalog.cosObject.getDictionaryObject(COSName.ACRO_FORM) as COSDictionary)
                val field = PDSignatureField(form).apply { partialName = "UnsignedReviewOverlay" }
                val widget = field.widgets.first()
                form.fields.add(field); page.annotations.add(widget)
                val timestamp = if (fakeTimestamp) PDSignature().apply {
                    setType(COSName.DOC_TIME_STAMP); setFilter(PDSignature.FILTER_ADOBE_PPKLITE); setSubFilter(PdfNames.ETSI_RFC3161)
                } else null
                timestamp?.let { signature ->
                    field.setValue(signature)
                    SignatureOptions().use { doc.addSignature(signature, it) }
                }
                widget.rectangle = PDRectangle(50f, 650f, 500f, 100f)
                widget.setPage(page)
                val appearance = PDAppearanceStream(doc).apply { resources = PDResources(); bBox = PDRectangle(500f, 100f) }
                PDPageContentStream(doc, appearance).use { cs ->
                    cs.setNonStrokingColor(Color.WHITE); cs.addRect(0f, 0f, 500f, 100f); cs.fill()
                    cs.setNonStrokingColor(Color.BLACK); cs.beginText()
                    cs.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA), 18f)
                    cs.newLineAtOffset(20f, 50f); cs.showText("Replacement displayed content"); cs.endText()
                }
                widget.appearance = PDAppearanceDictionary().apply { setNormalAppearance(appearance) }
                form.cosObject.isNeedToBeUpdated = true; page.cosObject.isNeedToBeUpdated = true
                val out = ByteArrayOutputStream()
                if (timestamp == null) doc.saveIncremental(out)
                else doc.saveIncrementalForExternalSigning(out).setSignature(byteArrayOf(0x30, 0))
                out.toByteArray()
            }
            val result = validator.validate(changed)
            assertEquals(1, result.signatures.size)
            assertEquals(Indication.TOTAL_FAILED, result.indication, result.toString())
            assertTrue(result.signatures.single().checks.any { it.id == "format:pdf-modification" })
        }
    }

    @Test
    fun malformedMessageDigestReturnsFormatFailure() = runTest {
        val original = signer.sign(samplePdf("Original approved content"), pki.credentials())
        val changed = rewriteContents(original) { der ->
            val ci = ContentInfo.getInstance(der)
            val sd = SignedData.getInstance(ci.content)
            val si = SignerInfo.getInstance(sd.signerInfos.getObjectAt(0))
            val attrs = DERSet(si.authenticatedAttributes.toArray().map {
                val attr = Attribute.getInstance(it)
                if (attr.attrType == CMSAttributes.messageDigest) Attribute(attr.attrType, DERSet(ASN1Integer(1))) else attr
            }.toTypedArray())
            val changedSigner = SignerInfo(si.sid, si.digestAlgorithm, attrs, si.digestEncryptionAlgorithm, si.encryptedDigest, si.unauthenticatedAttributes)
            ContentInfo(ci.contentType, SignedData(sd.digestAlgorithms, sd.encapContentInfo, sd.certificates, sd.crLs, DERSet(changedSigner))).encoded
        }
        assertEquals(Indication.TOTAL_FAILED, validator.validate(changed).indication)
    }

    @Test
    fun brokenTimestampLosesTLevelAndAugmentationAddsUsableTime() = runTest {
        val original = signer.sign(samplePdf("Original approved content"), pki.credentials(), PadesSignatureParameters(level = AdesLevel.B_T))
        val broken = rewriteContents(original) { der ->
            val token = CmsSignedData(der).signerInfos.single().unsignedAttributes.first { it.oid == "1.2.840.113549.1.9.16.2.14" }.values.single().encoded
            val signature = TimeStampToken(token).signerInfo.signature
            val offset = der.asList().windowed(signature.size).indexOfFirst { it.toByteArray().contentEquals(signature) }
            check(offset >= 0)
            der.copyOf().also { it[offset + signature.lastIndex] = (it[offset + signature.lastIndex].toInt() xor 1).toByte() }
        }
        val result = validator.validate(broken)
        assertEquals(AdesLevel.B_B, result.signatures.single().level)
        assertFalse(result.signatures.single().timestamps.single().passed)
        assertEquals(Indication.TOTAL_PASSED, result.indication)
        val augmented = signer.augment(broken, AdesLevel.B_T)
        assertFalse(broken.contentEquals(augmented))
        assertEquals(AdesLevel.B_T, validator.validate(augmented).signatures.single().level)
    }

    @Test
    fun eofTextInContentIsNotARevisionAndOverflowingByteRangesFailCleanly() = runTest {
        val signed = signer.sign(samplePdf("Literal %%EOF in approved text"), pki.credentials())
        assertEquals(Indication.TOTAL_PASSED, validator.validate(signed).indication)
        val malformed = Loader.loadPDF(signed).use { doc ->
            doc.signatureDictionaries.first().byteRange = intArrayOf(0, Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE)
            doc.signatureDictionaries.first().cosObject.isNeedToBeUpdated = true
            ByteArrayOutputStream().also { doc.saveIncremental(it) }.toByteArray()
        }
        assertEquals(Indication.TOTAL_FAILED, validator.validate(malformed).indication)
    }

    @Test
    fun malformedTimestampDigestReturnsAResultInsteadOfThrowing() = runTest {
        val original = signer.sign(samplePdf(), pki.credentials(), PadesSignatureParameters(level = AdesLevel.B_T))
        val changed = rewriteContents(original) { der ->
            val content = ContentInfo.getInstance(der)
            val signed = SignedData.getInstance(content.content)
            val info = SignerInfo.getInstance(signed.signerInfos.getObjectAt(0))
            val attributes = DERSet(info.unauthenticatedAttributes.toArray().map { value ->
                val attribute = Attribute.getInstance(value)
                if (attribute.attrType.id == "1.2.840.113549.1.9.16.2.14") {
                    val token = ContentInfo.getInstance(attribute.attrValues.getObjectAt(0))
                    val tokenData = SignedData.getInstance(token.content)
                    val tokenSigner = SignerInfo.getInstance(tokenData.signerInfos.getObjectAt(0))
                    val malformedAttributes = DERSet(tokenSigner.authenticatedAttributes.toArray().map {
                        val a = Attribute.getInstance(it)
                        if (a.attrType == CMSAttributes.messageDigest) Attribute(a.attrType, DERSet(ASN1Integer(1))) else a
                    }.toTypedArray())
                    val malformedSigner = SignerInfo(tokenSigner.sid, tokenSigner.digestAlgorithm, malformedAttributes,
                        tokenSigner.digestEncryptionAlgorithm, tokenSigner.encryptedDigest, tokenSigner.unauthenticatedAttributes)
                    val malformedToken = ContentInfo(token.contentType, SignedData(tokenData.digestAlgorithms, tokenData.encapContentInfo,
                        tokenData.certificates, tokenData.crLs, DERSet(malformedSigner)))
                    Attribute(attribute.attrType, DERSet(malformedToken))
                } else attribute
            }.toTypedArray())
            val updated = SignerInfo(info.sid, info.digestAlgorithm, info.authenticatedAttributes, info.digestEncryptionAlgorithm,
                info.encryptedDigest, attributes)
            ContentInfo(content.contentType, SignedData(signed.digestAlgorithms, signed.encapContentInfo, signed.certificates, signed.crLs, DERSet(updated))).encoded
        }
        val result = validator.validate(changed)
        assertEquals(Indication.TOTAL_PASSED, result.indication)
        assertFalse(result.signatures.single().timestamps.single().passed)
        assertEquals(AdesLevel.B_B, result.signatures.single().level)
        assertEquals(AdesLevel.B_T, result.signatures.single().structuralLevel)
    }

    @Test
    fun changingAContentStreamsTypeDoesNotExemptItsNewContent() = runTest {
        val original = signer.sign(samplePdf(), pki.credentials())
        for (type in listOf(COSName.OBJ_STM, COSName.XREF)) {
            val changed = Loader.loadPDF(original).use { doc ->
                val stream = doc.getPage(0).cosObject.getDictionaryObject(COSName.CONTENTS) as COSStream
                stream.setItem(COSName.TYPE, type)
                stream.createOutputStream().use { it.write("BT /F1 14 Tf 72 700 Td (Changed content) Tj ET".encodeToByteArray()) }
                stream.isNeedToBeUpdated = true
                ByteArrayOutputStream().also { doc.saveIncremental(it) }.toByteArray()
            }
            assertEquals(Indication.TOTAL_FAILED, validator.validate(changed).indication, "content disguised as ${type.name}")
        }
    }

    @Test
    fun augmentationReusesTimeOnlyUnderTheConfiguredTrustPolicy() = runTest {
        val original = signer.sign(samplePdf(), pki.credentials(), PadesSignatureParameters(level = AdesLevel.B_T))
        val source = Rfc3161TimestampClient(pki.tsaUrl, pki.transport)
        val trusted = PadesSigner(source, timestampValidationPolicy = AdesValidationPolicy(listOf(pki.root.certificate), transport = pki.transport))
        assertTrue(original.contentEquals(trusted.augment(original, AdesLevel.B_T)))
        val untrusted = PadesSigner(source, timestampValidationPolicy = AdesValidationPolicy(transport = pki.transport))
        assertFalse(original.contentEquals(untrusted.augment(original, AdesLevel.B_T)))
    }

    @Test
    fun acceptsLinearizedBaseFilesAndTheirIncrementalSignatures() = runTest {
        val pdf = requireNotNull(javaClass.getResourceAsStream("/linearized.pdf")).use { it.readBytes() }
        val first = signer.sign(pdf, pki.credentials())
        assertEquals(Indication.TOTAL_PASSED, validator.validate(first).indication)
        val second = signer.sign(first, pki.credentials())
        assertTrue(validator.validate(second).signatures.all { it.isValid })
    }

    @Test
    fun rejectsCryptographicallyBrokenTokensFromACustomSource() = runTest {
        val source = Rfc3161TimestampClient(pki.tsaUrl, pki.transport)
        val broken = TimestampSource { algorithm, digest ->
            source.timestamp(algorithm, digest).also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        }
        for (mode in PadesTimestampMode.entries) {
            assertFailsWith<TimestampException> {
                PadesSigner(broken).sign(samplePdf(), pki.credentials(), PadesSignatureParameters(level = AdesLevel.B_T, timestampMode = mode))
            }
        }
    }

    @Test
    fun indirectPageTreeKidsCannotBeReordered() = runTest {
        val pdf = PDDocument().use { doc ->
            doc.addPage(PDPage()); doc.addPage(PDPage())
            val pages = doc.pages.cosObject
            pages.setItem(COSName.KIDS, COSObject(pages.getDictionaryObject(COSName.KIDS)))
            ByteArrayOutputStream().also { doc.save(it) }.toByteArray()
        }
        val signed = signer.sign(pdf, pki.credentials())
        assertEquals(Indication.TOTAL_PASSED, validator.validate(signed).indication)
        val reordered = Loader.loadPDF(signed).use { doc ->
            assertTrue(doc.pages.cosObject.getItem(COSName.KIDS) is COSObject)
            val kids = doc.pages.cosObject.getDictionaryObject(COSName.KIDS) as COSArray
            val first = kids.get(0)
            kids.set(0, kids.get(1)); kids.set(1, first)
            kids.isNeedToBeUpdated = true
            ByteArrayOutputStream().also { doc.saveIncremental(it) }.toByteArray()
        }
        assertEquals(Indication.TOTAL_FAILED, validator.validate(reordered).indication)
    }

    private fun rewriteContents(pdf: ByteArray, mutate: (ByteArray) -> ByteArray): ByteArray {
        val br = Loader.loadPDF(pdf).use { it.signatureDictionaries.first().byteRange }
        val start = br[1] + 1
        val hex = String(pdf, start, br[2] - 1 - start, Charsets.ISO_8859_1)
        val der = Der.parseFirst(hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()).encoded
        val updated = mutate(der).toHexString().uppercase().padEnd(hex.length, '0')
        return pdf.copyOf().also { updated.encodeToByteArray().copyInto(it, start) }
    }
}
