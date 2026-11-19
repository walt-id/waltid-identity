package id.walt.ades.pades

import java.awt.Color
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.PDResources
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType0Font
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceDictionary
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceStream

internal object PdfSignatureAppearance {
    fun apply(document: PDDocument, widget: PDAnnotationWidget, parameters: PadesSignatureAppearance) {
        val rectangle = requireNotNull(widget.rectangle) { "Signature field has no rectangle" }
        require(rectangle.width > 8 && rectangle.height > 8) { "Visible signature rectangle is too small" }
        val page = widget.page ?: document.pages.firstOrNull { p -> p.annotations.any { it.cosObject === widget.cosObject } }
        requireNotNull(page) { "Signature widget is not on a page" }
        val crop = page.cropBox
        require(rectangle.lowerLeftX >= crop.lowerLeftX && rectangle.lowerLeftY >= crop.lowerLeftY &&
            rectangle.upperRightX <= crop.upperRightX && rectangle.upperRightY <= crop.upperRightY) { "Signature rectangle is outside the page crop box" }
        val font = parameters.font?.let { bytes ->
            requireNotNull(runCatching { PDType0Font.load(document, bytes.inputStream()) }.getOrNull()) { "Invalid signature font" }
        }
            ?: PDType1Font(Standard14Fonts.FontName.HELVETICA)
        val image = parameters.image?.let { bytes ->
            requireNotNull(runCatching { PDImageXObject.createFromByteArray(document, bytes, "signature") }.getOrNull()) { "Invalid signature image" }
        }
        val width = rectangle.width - 8
        val textHeight = if (image == null) rectangle.height - 8 else (rectangle.height - 8) / 2
        var size = parameters.fontSize
        var lines: List<String>
        while (true) {
            lines = if (parameters.text.isBlank()) emptyList() else parameters.text.split('\n').flatMap { paragraph ->
                val result = mutableListOf<String>()
                var line = ""
                paragraph.split(Regex("\\s+")).filter { it.isNotEmpty() }.forEach { word ->
                    val candidate = if (line.isEmpty()) word else "$line $word"
                    if (font.getStringWidth(candidate) / 1000 * size > width && line.isNotEmpty()) {
                        result += line; line = word
                    } else line = candidate
                }
                result + line
            }
            if (lines.all { font.getStringWidth(it) / 1000 * size <= width } && lines.size * size * 1.2f <= textHeight) break
            require(size > 4) { "Signature text does not fit the rectangle" }
            size = (size - 1).coerceAtLeast(4f)
        }
        val stream = PDAppearanceStream(document).apply {
            resources = PDResources()
            bBox = PDRectangle(rectangle.width, rectangle.height)
        }
        PDPageContentStream(document, stream).use { content ->
            content.setNonStrokingColor(Color.WHITE)
            content.addRect(0f, 0f, rectangle.width, rectangle.height); content.fill()
            image?.let {
                val availableHeight = rectangle.height - 8 - if (parameters.text.isBlank()) 0f else textHeight
                val scale = minOf(width / it.width, availableHeight / it.height)
                content.drawImage(it, 4 + (width - it.width * scale) / 2, rectangle.height - 4 - it.height * scale,
                    it.width * scale, it.height * scale)
            }
            if (parameters.text.isNotBlank()) {
                content.setNonStrokingColor(Color.BLACK)
                content.beginText(); content.setFont(font, size); content.setLeading(size * 1.2f)
                content.newLineAtOffset(4f, textHeight + 4 - size)
                lines.forEach { content.showText(it); content.newLine() }
                content.endText()
            }
        }
        widget.appearance = PDAppearanceDictionary().apply { setNormalAppearance(stream) }
        widget.isPrinted = true
        widget.cosObject.isNeedToBeUpdated = true
    }
}
