package id.walt.ades.pades

import id.walt.ades.test.TestPki
import id.walt.ades.tsp.Rfc3161TimestampClient
import id.walt.ades.validation.AdesLevel
import id.walt.ades.validation.ValidationDataCollector
import id.walt.ades.validation.AdesValidationPolicy
import id.walt.ades.validation.Indication
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.apache.pdfbox.Loader
import org.apache.pdfbox.cos.COSArray
import org.apache.pdfbox.cos.COSObject
import org.apache.pdfbox.cos.COSDictionary
import org.apache.pdfbox.cos.COSName
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationText
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm
import org.apache.pdfbox.pdmodel.interactive.form.PDNonTerminalField
import org.apache.pdfbox.pdmodel.interactive.form.PDSignatureField
import org.apache.pdfbox.pdmodel.interactive.form.PDTextField
import org.apache.pdfbox.rendering.PDFRenderer

class PadesSigningTest {
    private val pki = TestPki()
    private val signer = PadesSigner()
    private val policy = AdesValidationPolicy(listOf(pki.root.certificate), transport = pki.transport)
    private val validator = PadesValidator(policy)

    @Test
    fun visibleTextAndImageAreSignedAndRenderedOnTheRequestedPage() = runTest {
        val image = BufferedImage(20, 10, BufferedImage.TYPE_INT_RGB).apply {
            createGraphics().let { graphics -> graphics.color = Color.RED; graphics.fillRect(0, 0, 20, 10); graphics.dispose() }
        }
        val png = ByteArrayOutputStream().also { assertTrue(ImageIO.write(image, "png", it)) }.toByteArray()
        val pdf = Loader.loadPDF(samplePdf()).use { doc ->
            doc.addPage(PDPage(doc.getPage(0).mediaBox))
            ByteArrayOutputStream().also { doc.save(it) }.toByteArray()
        }
        val signed = signer.sign(pdf, pki.credentials(), PadesSignatureParameters(fieldName = "Visible", pageIndex = 1,
            rectangle = PadesSignatureRectangle(72f, 100f, 220f, 90f),
            appearance = PadesSignatureAppearance("Approved by Example Signer\nDocument approval", image = png)))
        assertEquals(Indication.TOTAL_PASSED, validator.validate(signed).indication)
        Loader.loadPDF(signed).use { doc ->
            val field = doc.signatureFields.single()
            assertEquals("Visible", field.fullyQualifiedName)
            assertEquals(72f, field.widgets.single().rectangle.lowerLeftX)
            assertEquals(1, doc.pages.indexOf(field.widgets.single().page))
            val rendered = PDFRenderer(doc).renderImage(1)
            val artifacts = File("build/pades-test-artifacts").apply { check(isDirectory || mkdirs()) }
            File(artifacts, "visible-signature.pdf").writeBytes(signed)
            assertTrue(ImageIO.write(rendered, "png", File(artifacts, "visible-signature.png")))
            assertTrue((100 until 180).any { y -> (75 until 290).any { x -> rendered.getRGB(x, rendered.height - y) == Color.RED.rgb } })
            val appearance = field.widgets.single().normalAppearanceStream
            assertNotNull(appearance)
            assertTrue(appearance.contents.use { it.readBytes().decodeToString() }.contains("Approved"))
        }
        val twice = signer.sign(signed, pki.credentials(), PadesSignatureParameters(fieldName = "Second",
            rectangle = PadesSignatureRectangle(72f, 250f, 200f, 50f), appearance = PadesSignatureAppearance("Second approval")))
        assertTrue(validator.validate(twice).signatures.all { it.isValid })
    }

    @Test
    fun signsAnExistingFieldAndPreservesItsPlacement() = runTest {
        val pdf = formPdf()
        val signed = signer.sign(pdf, pki.credentials(), PadesSignatureParameters(existingFieldName = "Approval",
            appearance = PadesSignatureAppearance("Existing field approval")))
        assertEquals(Indication.TOTAL_PASSED, validator.validate(signed).indication)
        Loader.loadPDF(signed).use { doc ->
            assertEquals(2, doc.signatureFields.size)
            val field = doc.signatureFields.single { it.fullyQualifiedName == "Approval" }
            assertNotNull(field.signature)
            assertEquals(200f, field.widgets.single().rectangle.width)
        }
        assertFailsWith<IllegalArgumentException> { signer.sign(signed, pki.credentials(), PadesSignatureParameters(existingFieldName = "Approval")) }
        assertFailsWith<IllegalArgumentException> { signer.sign(pdf, pki.credentials(), PadesSignatureParameters(existingFieldName = "Absent")) }
        assertFailsWith<IllegalArgumentException> { signer.sign(pdf, pki.credentials(), PadesSignatureParameters(existingFieldName = "Name")) }
        val readOnly = Loader.loadPDF(pdf).use { doc ->
            val form = PDAcroForm(doc, doc.documentCatalog.cosObject.getDictionaryObject(COSName.ACRO_FORM) as COSDictionary)
            form.getField("Approval").isReadOnly = true
            ByteArrayOutputStream().also { doc.save(it) }.toByteArray()
        }
        assertFailsWith<IllegalArgumentException> { signer.sign(readOnly, pki.credentials(), PadesSignatureParameters(existingFieldName = "Approval")) }
    }

    @Test
    fun rejectsInvalidAppearanceParameters() = runTest {
        suspend fun attempt(parameters: PadesSignatureParameters) = signer.sign(samplePdf(), pki.credentials(), parameters)
        assertFailsWith<IllegalArgumentException> { attempt(PadesSignatureParameters(pageIndex = 9)) }
        assertFailsWith<IllegalArgumentException> { attempt(PadesSignatureParameters(rectangle = PadesSignatureRectangle(600f, 700f, 100f, 100f), appearance = PadesSignatureAppearance("Approval"))) }
        assertFailsWith<IllegalArgumentException> { attempt(PadesSignatureParameters(rectangle = PadesSignatureRectangle(10f, 10f, 100f, 40f), appearance = PadesSignatureAppearance("Approval", image = byteArrayOf(1, 2)))) }
        assertFailsWith<IllegalArgumentException> { attempt(PadesSignatureParameters(rectangle = PadesSignatureRectangle(10f, 10f, 100f, 40f), appearance = PadesSignatureAppearance("Approval", font = byteArrayOf(1, 2)))) }
        assertFailsWith<IllegalArgumentException> { PadesSignatureRectangle(Float.NaN, 0f, 100f, 50f) }
        assertFailsWith<IllegalArgumentException> { PadesSignatureParameters(fieldName = "New", existingFieldName = "Old") }
    }

    @Test
    fun certificationPermissionsCannotBeBypassedByAllowOtherModifications() = runTest {
        val certified = signer.sign(samplePdf(), pki.credentials(), PadesSignatureParameters(certificationPermission = PadesCertificationPermission.NO_CHANGES))
        assertEquals(Indication.TOTAL_PASSED, validator.validate(certified).indication)
        assertFailsWith<IllegalStateException> { signer.sign(certified, pki.credentials()) }
        val changed = Loader.loadPDF(certified).use { doc ->
            doc.getPage(0).rotation = 90; doc.getPage(0).cosObject.isNeedToBeUpdated = true
            ByteArrayOutputStream().also { doc.saveIncremental(it) }.toByteArray()
        }
        assertEquals(Indication.TOTAL_FAILED, PadesValidator(policy, allowOtherModifications = true).validate(changed).indication)
        val approval = signer.sign(samplePdf(), pki.credentials())
        assertFailsWith<IllegalArgumentException> { signer.sign(approval, pki.credentials(), PadesSignatureParameters(certificationPermission = PadesCertificationPermission.FORM_FILLING_AND_SIGNING)) }
    }

    @Test
    fun includeExcludeAndAllLocksControlSubsequentSignatures() = runTest {
        for ((action, fields, blocked) in listOf(
            Triple(PadesFieldLockAction.INCLUDE, listOf("Second"), true),
            Triple(PadesFieldLockAction.EXCLUDE, listOf("Second"), false),
            Triple(PadesFieldLockAction.ALL, emptyList(), true),
        )) {
            val locked = signer.sign(formPdf(), pki.credentials(), PadesSignatureParameters(existingFieldName = "Approval", fieldLock = PadesFieldLock(action, fields)))
            Loader.loadPDF(locked).use { doc ->
                val refs = doc.signatureDictionaries.single().cosObject.getDictionaryObject(COSName.getPDFName("Reference")) as COSArray
                val transform = refs.getObject(0) as COSDictionary
                val data = transform.getItem(PdfNames.DATA) as COSObject
                val root = doc.document.trailer.getItem(COSName.ROOT) as COSObject
                assertEquals(root.key, data.key)
            }
            if (blocked) assertFailsWith<IllegalArgumentException> { signer.sign(locked, pki.credentials(), PadesSignatureParameters(existingFieldName = "Second")) }
            else assertTrue(validator.validate(signer.sign(locked, pki.credentials(), PadesSignatureParameters(existingFieldName = "Second"))).signatures.all { it.isValid })
        }
    }

    @Test
    fun signedFieldMdpStillEnforcesLocksWhenUnsignedLockEntryIsRemoved() = runTest {
        val locked = signer.sign(formPdf(), pki.credentials(), PadesSignatureParameters(existingFieldName = "Approval",
            fieldLock = PadesFieldLock(PadesFieldLockAction.INCLUDE, listOf("Name"))))
        val changed = Loader.loadPDF(locked).use { doc ->
            val form = PDAcroForm(doc, doc.documentCatalog.cosObject.getDictionaryObject(COSName.ACRO_FORM) as COSDictionary)
            form.getField("Approval").cosObject.removeItem(COSName.getPDFName("Lock"))
            form.getField("Approval").cosObject.isNeedToBeUpdated = true
            val signature = form.getField("Approval").cosObject.getDictionaryObject(COSName.V) as COSDictionary
            signature.removeItem(COSName.getPDFName("Reference"))
            signature.isNeedToBeUpdated = true
            ByteArrayOutputStream().also { doc.saveIncremental(it) }.toByteArray()
        }
        val changedAgain = Loader.loadPDF(changed).use { doc ->
            val form = PDAcroForm(doc, doc.documentCatalog.cosObject.getDictionaryObject(COSName.ACRO_FORM) as COSDictionary)
            form.getField("Name").cosObject.setString(COSName.V, "Changed")
            form.getField("Name").cosObject.isNeedToBeUpdated = true
            ByteArrayOutputStream().also { doc.saveIncremental(it) }.toByteArray()
        }
        val result = PadesValidator(policy, allowOtherModifications = true).validate(changedAgain)
        assertEquals(Indication.TOTAL_FAILED, result.indication)
        assertTrue(result.signatures.first().checks.any { it.id == "format:fieldmdp" })
    }

    @Test
    fun docMdpAllowsFormValuesAtP2AndMarkupAnnotationsOnlyAtP3() = runTest {
        for (permission in PadesCertificationPermission.entries) {
            val certified = signer.sign(formPdf(), pki.credentials(), PadesSignatureParameters(existingFieldName = "Approval", certificationPermission = permission))
            val filled = Loader.loadPDF(certified).use { doc ->
                val form = PDAcroForm(doc, doc.documentCatalog.cosObject.getDictionaryObject(COSName.ACRO_FORM) as COSDictionary)
                form.getField("Name").cosObject.setString(COSName.V, "New value")
                form.getField("Name").cosObject.isNeedToBeUpdated = true
                ByteArrayOutputStream().also { doc.saveIncremental(it) }.toByteArray()
            }
            assertEquals(if (permission.value >= 2) Indication.TOTAL_PASSED else Indication.TOTAL_FAILED,
                validator.validate(filled).indication, "form filling at $permission")
            val annotated = Loader.loadPDF(certified).use { doc ->
                val annotation = PDAnnotationText().apply { rectangle = PDRectangle(20f, 20f, 20f, 20f); contents = "Review note" }
                doc.getPage(0).annotations.add(annotation)
                doc.getPage(0).cosObject.isNeedToBeUpdated = true
                ByteArrayOutputStream().also { doc.saveIncremental(it) }.toByteArray()
            }
            assertEquals(if (permission.value == 3) Indication.TOTAL_PASSED else Indication.TOTAL_FAILED,
                validator.validate(annotated).indication, "annotations at $permission")
        }
    }

    @Test
    fun excludingAChildDoesNotLockItThroughItsParent() = runTest {
        val nested = Loader.loadPDF(formPdf()).use { doc ->
            val form = PDAcroForm(doc, doc.documentCatalog.cosObject.getDictionaryObject(COSName.ACRO_FORM) as COSDictionary)
            val child = form.getField("Second")
            val group = PDNonTerminalField(form).apply { partialName = "Group"; children = listOf(child) }
            child.cosObject.setItem(COSName.PARENT, group)
            form.fields.remove(child); form.fields.add(group)
            ByteArrayOutputStream().also { doc.save(it) }.toByteArray()
        }
        val first = signer.sign(nested, pki.credentials(), PadesSignatureParameters(existingFieldName = "Approval",
            fieldLock = PadesFieldLock(PadesFieldLockAction.EXCLUDE, listOf("Group.Second"))))
        val second = signer.sign(first, pki.credentials(), PadesSignatureParameters(existingFieldName = "Group.Second"))
        assertTrue(validator.validate(second).signatures.all { it.isValid })
        val parentLocked = signer.sign(nested, pki.credentials(), PadesSignatureParameters(existingFieldName = "Approval",
            fieldLock = PadesFieldLock(PadesFieldLockAction.INCLUDE, listOf("Group"))))
        assertFailsWith<IllegalArgumentException> { signer.sign(parentLocked, pki.credentials(), PadesSignatureParameters(existingFieldName = "Group.Second")) }
    }

    @Test
    fun embedsAUnicodeFontOnARotatedPage() = runTest {
        val font = requireNotNull(PDDocument::class.java.getResourceAsStream("/org/apache/pdfbox/resources/ttf/LiberationSans-Regular.ttf")).use { it.readBytes() }
        val pdf = Loader.loadPDF(samplePdf()).use { doc ->
            doc.getPage(0).rotation = 90
            doc.getPage(0).cropBox = PDRectangle(40f, 60f, 400f, 600f)
            ByteArrayOutputStream().also { doc.save(it) }.toByteArray()
        }
        val signed = signer.sign(pdf, pki.credentials(), PadesSignatureParameters(rectangle = PadesSignatureRectangle(80f, 100f, 200f, 70f),
            appearance = PadesSignatureAppearance("Signed by Zoë Müller - Εγκρίθηκε", font = font)))
        assertEquals(Indication.TOTAL_PASSED, validator.validate(signed).indication)
        Loader.loadPDF(signed).use { doc ->
            val appearance = doc.signatureFields.single().widgets.single().normalAppearanceStream
            assertTrue(appearance.resources.getFont(appearance.resources.fontNames.first()).isEmbedded)
            val image = PDFRenderer(doc).renderImage(0)
            assertEquals(600, image.width)
            assertEquals(400, image.height)
        }
    }

    @Test
    fun certificationAndAllFieldsLockStillAllowLongTermValidationUpdates() = runTest {
        val longTermSigner = PadesSigner(Rfc3161TimestampClient(pki.tsaUrl, pki.transport),
            ValidationDataCollector(pki.transport, listOf(pki.root.certificate)))
        val signed = longTermSigner.sign(samplePdf(), pki.credentials(), PadesSignatureParameters(level = AdesLevel.B_LTA,
            certificationPermission = PadesCertificationPermission.NO_CHANGES, fieldLock = PadesFieldLock()))
        val result = validator.validate(signed)
        assertEquals(Indication.TOTAL_PASSED, result.indication, result.toString())
        assertEquals(AdesLevel.B_LTA, result.signatures.single().level)
    }

    private fun formPdf(): ByteArray = Loader.loadPDF(samplePdf()).use { doc ->
        val form = PDAcroForm(doc)
        doc.documentCatalog.acroForm = form
        for ((index, name) in listOf("Approval", "Second").withIndex()) {
            val field = PDSignatureField(form).apply { partialName = name }
            field.widgets.single().apply { rectangle = PDRectangle(72f, 100f + 100 * index, 200f, 60f); setPage(doc.getPage(0)) }
            form.fields.add(field); doc.getPage(0).annotations.add(field.widgets.single())
        }
        form.fields.add(PDTextField(form).apply { partialName = "Name"; cosObject.setString(COSName.V, "Original") })
        ByteArrayOutputStream().also { doc.save(it) }.toByteArray()
    }
}
