package id.walt.ades.pades

import org.apache.pdfbox.cos.COSArray
import org.apache.pdfbox.cos.COSBase
import org.apache.pdfbox.cos.COSDictionary
import org.apache.pdfbox.cos.COSName
import org.apache.pdfbox.cos.COSObject
import org.apache.pdfbox.cos.COSStream
import org.apache.pdfbox.cos.COSString
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm
import org.apache.pdfbox.pdmodel.interactive.form.PDSignatureField

/** Signed FieldMDP transforms and signature-field locks (ISO 32000-1 12.8.2.4 and 12.7.4.5). */
internal object PdfPermissions {
    private val reference = COSName.getPDFName("Reference")
    private val method = COSName.getPDFName("TransformMethod")
    private val params = COSName.getPDFName("TransformParams")
    private val lock = COSName.getPDFName("Lock")
    private val action = COSName.getPDFName("Action")

    private data class Rule(val action: String, val fields: List<String>) {
        fun locks(name: String): Boolean {
            val listed = fields.any { name == it || name.startsWith("$it.") }
            return when (action) {
                "All" -> true
                "Include" -> listed
                "Exclude" -> !listed
                else -> true // An unsupported transform cannot grant permission to change fields.
            }
        }
    }

    fun formFields(document: PDDocument) =
        (document.documentCatalog.cosObject.getDictionaryObject(COSName.ACRO_FORM) as? COSDictionary)?.let { dictionary ->
            val fields = PDAcroForm(document, dictionary).fieldTree.toList()
            val names = fields.associateBy { requireNotNull(it.fullyQualifiedName) { "Unnamed form field" } }
            require(names.size == fields.size) { "Duplicate form field names" }
            names
        }.orEmpty()

    /** Certification permissions must be read from the signed revision, never from an unsigned replacement. */
    fun docMdpPermission(document: PDDocument): Int? {
        val perms = document.documentCatalog.cosObject.getDictionaryObject(COSName.PERMS) as? COSDictionary
        val certification = perms?.getDictionaryObject(COSName.getPDFName("DocMDP")) as? COSDictionary ?: return null
        val refs = certification.getDictionaryObject(reference) as? COSArray
        return (0 until (refs?.size() ?: 0)).firstNotNullOfOrNull { i ->
            (refs!!.getObject(i) as? COSDictionary)?.takeIf { it.getNameAsString(method) == "DocMDP" }
                ?.let { (it.getDictionaryObject(params) as? COSDictionary)?.getInt(COSName.P, 2) ?: 2 }
        } ?: 2
    }

    private fun rules(document: PDDocument): List<Rule> = buildList {
        fun addRule(dictionary: COSDictionary) {
            val names = dictionary.getDictionaryObject(COSName.FIELDS) as? COSArray
            val selected = (0 until (names?.size() ?: 0)).mapNotNull { (names!!.getObject(it) as? COSString)?.string }
            val kind = dictionary.getNameAsString(action) ?: "All"
            add(Rule(if (kind != "All" && (names == null || selected.size != names.size())) "Invalid" else kind, selected))
        }
        formFields(document).values.forEach { field ->
            val signature = field.cosObject.getDictionaryObject(COSName.V) as? COSDictionary ?: return@forEach
            if (field !is PDSignatureField) return@forEach
            (field.cosObject.getDictionaryObject(lock) as? COSDictionary)?.let(::addRule)
            val refs = signature.getDictionaryObject(reference) as? COSArray
            for (i in 0 until (refs?.size() ?: 0)) {
                val ref = refs!!.getObject(i) as? COSDictionary ?: continue
                if (ref.getNameAsString(method) == "FieldMDP") {
                    val transform = ref.getDictionaryObject(params) as? COSDictionary
                    if (transform == null) add(Rule("Invalid", emptyList())) else addRule(transform)
                }
            }
        }
    }

    fun configure(document: PDDocument, field: PDSignatureField, signature: PDSignature, parameters: PadesSignatureParameters) {
        val references = COSArray()
        fun transform(name: String, values: COSDictionary) {
            values.setItem(COSName.TYPE, COSName.getPDFName("TransformParams"))
            values.setName(COSName.V, "1.2")
            references.add(COSDictionary().apply {
                setItem(COSName.TYPE, COSName.getPDFName("SigRef"))
                setName(method, name)
                setItem(params, values)
                // ISO 32000-1 Table 253: FieldMDP requires an indirect Data reference to the catalog.
                if (name == "FieldMDP") setItem(PdfNames.DATA, document.document.trailer.getItem(COSName.ROOT))
            })
        }
        parameters.certificationPermission?.let { permission ->
            require(document.signatureDictionaries.none { it.cosObject !== signature.cosObject }) { "Only the first signature can certify a PDF" }
            transform("DocMDP", COSDictionary().apply { setInt(COSName.P, permission.value) })
            val catalog = document.documentCatalog.cosObject
            val perms = catalog.getDictionaryObject(COSName.PERMS) as? COSDictionary ?: COSDictionary().also { catalog.setItem(COSName.PERMS, it) }
            perms.setItem(COSName.getPDFName("DocMDP"), signature)
            perms.isNeedToBeUpdated = true
            catalog.isNeedToBeUpdated = true
        }
        parameters.fieldLock?.let { rule ->
            field.cosObject.setItem(lock, COSDictionary().apply {
                setItem(COSName.TYPE, COSName.getPDFName("SigFieldLock"))
                setName(action, rule.action.name.lowercase().replaceFirstChar { it.uppercaseChar() })
                if (rule.fields.isNotEmpty()) setItem(COSName.FIELDS, COSArray(rule.fields.map(::COSString)))
            })
        }
        (field.cosObject.getDictionaryObject(lock) as? COSDictionary)?.let { transform("FieldMDP", COSDictionary(it)) }
        if (references.size() > 0) signature.cosObject.setItem(reference, references)
    }

    fun requireSignable(document: PDDocument, name: String) {
        require(rules(document).none { it.locks(name) }) { "Field $name is locked by a signed FieldMDP transform or signature-field Lock" }
    }

    fun changes(old: PDDocument, new: PDDocument): List<String> {
        val rules = rules(old)
        if (rules.isEmpty()) return emptyList()
        val before = formFields(old)
        val after = formFields(new)
        return (before.keys + after.keys).filter { name ->
            val addedTimestamp = before[name] == null && (after[name] as? PDSignatureField)?.let { field ->
                val signature = field.signature
                (signature?.cosObject?.getCOSName(COSName.TYPE) == COSName.DOC_TIME_STAMP || signature?.subFilter == PdfNames.ETSI_RFC3161.name) &&
                    field.widgets.all { widget -> widget.rectangle?.let { it.width == 0f || it.height == 0f } ?: (widget.appearance == null) }
            } == true
            !addedTimestamp && rules.any { it.locks(name) } && !deepSame(before[name]?.cosObject, after[name]?.cosObject, HashSet())
        }.map { "locked field $it changed" }
    }

    // Follow references as well: changing an appearance stream in place also changes the locked field.
    private fun deepSame(a: COSBase?, b: COSBase?, visited: MutableSet<Pair<COSBase, COSBase>>): Boolean {
        if (a == null || b == null) return a == b
        if (!visited.add(a to b)) return true
        val left = if (a is COSObject) a.`object` else a
        val right = if (b is COSObject) b.`object` else b
        if (left is COSDictionary && right is COSDictionary) {
            val keys = (left.keySet() + right.keySet()) - setOf(COSName.P, COSName.PARENT, COSName.KIDS)
            return keys.all {
                // FieldMDP Data points to the document, not to a component of the field value or appearance.
                if (it == PdfNames.DATA && left.getNameAsString(method) == "FieldMDP") PdfRevisionDiff.same(left.getItem(it), right.getItem(it))
                else deepSame(left.getItem(it), right.getItem(it), visited)
            } &&
                (left !is COSStream && right !is COSStream || PdfRevisionDiff.same(left, right))
        }
        if (left is COSArray && right is COSArray) return left.size() == right.size() &&
            (0 until left.size()).all { deepSame(left.get(it), right.get(it), visited) }
        return PdfRevisionDiff.same(left, right)
    }
}
