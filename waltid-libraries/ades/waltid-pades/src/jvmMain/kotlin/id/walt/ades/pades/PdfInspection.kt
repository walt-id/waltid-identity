package id.walt.ades.pades

import id.walt.ades.asn1.Der
import id.walt.ades.asn1.Oids
import id.walt.ades.cms.CmsSignedData
import id.walt.ades.tsp.TimeStampToken
import id.walt.ades.validation.ValidationData
import id.walt.ades.x509.OcspResponse
import id.walt.ades.x509.X509Certificate
import id.walt.ades.x509.X509Crl
import java.io.Closeable
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone
import kotlin.time.Instant
import kotlin.time.toKotlinInstant
import org.apache.pdfbox.cos.COSArray
import org.apache.pdfbox.cos.COSBase
import org.apache.pdfbox.cos.COSDictionary
import org.apache.pdfbox.cos.COSName
import org.apache.pdfbox.cos.COSNull
import org.apache.pdfbox.cos.COSNumber
import org.apache.pdfbox.cos.COSObject
import org.apache.pdfbox.cos.COSObjectKey
import org.apache.pdfbox.cos.COSStream
import org.apache.pdfbox.cos.COSString
import org.apache.pdfbox.io.RandomAccessReadBuffer
import org.apache.pdfbox.pdfparser.PDFParser
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature

internal object PdfNames {
    val DATA: COSName = COSName.getPDFName("Data")
    val ETSI_CADES_DETACHED: COSName = COSName.getPDFName("ETSI.CAdES.detached")
    val ETSI_RFC3161: COSName = COSName.getPDFName("ETSI.RFC3161")
    val ADBE_PKCS7_DETACHED: COSName = COSName.getPDFName("adbe.pkcs7.detached")
    val ESIC: COSName = COSName.getPDFName("ESIC")
    val BASE_VERSION: COSName = COSName.getPDFName("BaseVersion")
    val EXTENSION_LEVEL: COSName = COSName.getPDFName("ExtensionLevel")
    val CERT: COSName = COSName.getPDFName("Cert")
    val CRL: COSName = COSName.getPDFName("CRL")
    val OCSP: COSName = COSName.getPDFName("OCSP")
    val DSS_TYPE: COSName = COSName.getPDFName("DSS")
}

internal fun Instant.toCalendar(): Calendar = GregorianCalendar(TimeZone.getTimeZone("UTC")).apply {
    timeInMillis = (epochSeconds * 1000)
}

internal fun ByteArray.hexUpper(): String = toHexString().uppercase()

/** One signature field value: a PAdES signature (Type Sig) or a document time-stamp (Type DocTimeStamp). */
internal class PdfSignatureField(
    val name: String,
    val dictionary: PDSignature,
    private val pdf: ByteArray,
) {
    val subFilter: String? = dictionary.subFilter
    val isDocumentTimestamp: Boolean =
        dictionary.cosObject.getCOSName(COSName.TYPE) == COSName.DOC_TIME_STAMP || subFilter == PdfNames.ETSI_RFC3161.name

    val byteRange: IntArray? = runCatching { dictionary.byteRange }.getOrNull()?.takeIf { it.size == 4 }

    /** Raw /Contents value, including the zero padding of the reserved space. */
    val contents: ByteArray = (dictionary.cosObject.getDictionaryObject(COSName.CONTENTS) as? COSString)?.bytes ?: ByteArray(0)

    /** /Contents without padding: the DER object. */
    val der: ByteArray? = runCatching { Der.parseFirst(contents).encoded }.getOrNull()

    /** End offset (exclusive) of the revision signed by this signature. */
    val revisionEnd: Int = byteRange?.let { (it[2].toLong() + it[3]).takeIf { end -> end in 0..pdf.size.toLong() }?.toInt() ?: Int.MAX_VALUE } ?: Int.MAX_VALUE

    val gapStart: Int get() = byteRange?.get(1) ?: -1
    val gapEnd: Int get() = byteRange?.get(2) ?: -1

    val signedContent: ByteArray? = byteRange?.let { br ->
        if (br[0] < 0 || br[1] < 0 || br[2] < 0 || br[3] < 0 || br[0].toLong() + br[1] > br[2] || br[2].toLong() + br[3] > pdf.size) null
        else pdf.copyOfRange(br[0], br[0] + br[1]) + pdf.copyOfRange(br[2], br[2] + br[3])
    }

    val cms: CmsSignedData? by lazy { der?.takeIf { !isDocumentTimestamp }?.let { runCatching { CmsSignedData(it) }.getOrNull() } }
    val timestampToken: TimeStampToken? by lazy { der?.takeIf { isDocumentTimestamp }?.let { runCatching { TimeStampToken(it) }.getOrNull() } }

    /** signature-time-stamp unsigned attributes of the (single) SignerInfo, as encoded tokens. */
    val signatureTimestampTokens: List<ByteArray> by lazy {
        cms?.signerInfos?.firstOrNull()?.unsignedAttributes
            ?.filter { it.oid == Oids.ATTR_SIGNATURE_TIME_STAMP }?.flatMap { a -> a.values.map { it.encoded } } ?: emptyList()
    }

    val signingTime: Instant? get() = runCatching { dictionary.signDate?.toInstant()?.toKotlinInstant() }.getOrNull()

    override fun toString() = "PdfSignatureField($name, docTs=$isDocumentTimestamp, byteRange=${byteRange?.toList()})"
}

/** Contents of a Document Security Store (EN 319 142-1 5.4.2). */
internal class DssContent(
    val certificates: List<X509Certificate>,
    val crls: List<X509Crl>,
    val ocsps: List<OcspResponse>,
    val vriKeys: Set<String>,
    val problems: List<String>,
) {
    val present: Boolean get() = certificates.isNotEmpty() || crls.isNotEmpty() || ocsps.isNotEmpty() || vriKeys.isNotEmpty()
    fun toValidationData() = ValidationData(certificates, crls, ocsps)

    companion object {
        val EMPTY = DssContent(emptyList(), emptyList(), emptyList(), emptySet(), emptyList())

        fun streamBytes(array: COSArray?): List<ByteArray> = (0 until (array?.size() ?: 0)).mapNotNull { i ->
            (array!!.getObject(i) as? COSStream)?.createInputStream()?.use { it.readAllBytes() }
        }

        fun read(document: PDDocument): DssContent {
            val dss = document.documentCatalog.cosObject.getDictionaryObject(COSName.DSS) as? COSDictionary ?: return EMPTY
            val problems = mutableListOf<String>()
            val certs = LinkedHashSet<X509Certificate>()
            val crls = LinkedHashSet<X509Crl>()
            val ocsps = LinkedHashSet<OcspResponse>()
            fun collect(dict: COSDictionary, certKey: COSName, crlKey: COSName, ocspKey: COSName, where: String) {
                fun bytes(key: COSName): List<ByteArray> = runCatching { streamBytes(dict.getDictionaryObject(key) as? COSArray) }
                    .getOrElse { problems += "$where: unreadable ${key.name}: ${it.message}"; emptyList() }
                bytes(certKey).forEach { b ->
                    runCatching { X509Certificate(b) }.onSuccess(certs::add).onFailure { problems += "$where: invalid certificate: ${it.message}" }
                }
                bytes(crlKey).forEach { b ->
                    runCatching { X509Crl(b) }.onSuccess(crls::add).onFailure { problems += "$where: invalid CRL: ${it.message}" }
                }
                bytes(ocspKey).forEach { b ->
                    runCatching { OcspResponse(b) }.onSuccess(ocsps::add).onFailure { problems += "$where: invalid OCSP response: ${it.message}" }
                }
            }
            collect(dss, COSName.CERTS, COSName.CRLS, COSName.OCSPS, "DSS")
            val vri = dss.getDictionaryObject(COSName.VRI) as? COSDictionary
            val vriKeys = vri?.keySet()?.map { it.name.uppercase() }?.toSet() ?: emptySet()
            vri?.keySet()?.forEach { key ->
                (vri.getDictionaryObject(key) as? COSDictionary)?.let { collect(it, PdfNames.CERT, PdfNames.CRL, PdfNames.OCSP, "VRI ${key.name}") }
            }
            return DssContent(certs.toList(), crls.toList(), ocsps.toList(), vriKeys, problems)
        }
    }
}

/** A parsed PDF with its signature fields and (lazily loaded) earlier revisions. */
internal class PdfInspection private constructor(val pdf: ByteArray, val document: PDDocument) : Closeable {

    val fields: List<PdfSignatureField> = document.signatureFields.mapNotNull { field ->
        field.signature?.let { PdfSignatureField(field.fullyQualifiedName ?: field.partialName ?: "Signature", it, pdf) }
    }.sortedBy { it.revisionEnd }

    val signatures: List<PdfSignatureField> get() = fields.filter { !it.isDocumentTimestamp }
    val documentTimestamps: List<PdfSignatureField> get() = fields.filter { it.isDocumentTimestamp }

    val dss: DssContent by lazy { DssContent.read(document) }

    /** DocMDP permission P (1..3) of a certification signature, null when the document is not certified (ISO 32000-1 12.8.2.2). */
    val docMdpPermission: Int? by lazy { PdfPermissions.docMdpPermission(document) }

    private val revisionDocuments = HashMap<Int, PDDocument>()

    /** Revisions as [start of "%%EOF", end of the line following it), linked by the trailer /Prev chain. */
    val revisions: List<IntRange> = try {
        linkedRevisions()
    } catch (e: Exception) {
        revisionDocuments.values.forEach { it.close() }
        throw e
    }

    /** Index of the revision that ends at [offset] (end of a ByteRange), or -1. */
    fun revisionIndexEndingAt(offset: Int): Int = revisions.indexOfFirst { offset >= it.first + EOF.size && offset <= it.last + 1 }

    fun revisionDocument(end: Int): PDDocument =
        if (end >= pdf.size) document else revisionDocuments.getOrPut(end) { loadStrict(pdf.copyOf(end)) }

    fun dssAt(end: Int): DssContent = if (end >= pdf.size) dss else DssContent.read(revisionDocument(end))

    private fun linkedRevisions(): List<IntRange> {
        val text = pdf.toString(Charsets.ISO_8859_1)
        val markers = Regex("startxref[\\t\\r\\n ]+([0-9]+)[\\t\\r\\n ]+%%EOF(?:\\r\\n|\\r|\\n)?")
            .findAll(text).map { match ->
                match.groupValues[1].toLong() to (text.indexOf("%%EOF", match.range.first) until match.range.last + 1)
            }.toList()
        val result = mutableListOf<IntRange>()
        val visited = HashSet<Long>()
        var xref = document.document.startXref
        while (true) {
            require(xref >= 0 && visited.add(xref)) { "Invalid or cyclic revision xref chain" }
            val marker = requireNotNull(markers.firstOrNull { it.first == xref && it.second.first > xref }?.second) {
                "No revision boundary for xref $xref"
            }
            val revision = revisionDocument(marker.last + 1)
            require(revision.document.startXref == xref) { "Revision startxref mismatch" }
            result += marker
            val previous = revision.document.trailer.getLong(COSName.PREV, -1)
            if (previous < 0) break
            // A linearized base file has two xref sections in one revision; the first points forward to the main section.
            if (previous > xref && previous < marker.first &&
                revision.document.linearizedDictionary?.getLong(COSName.L) == (marker.last + 1).toLong()) break
            require(previous < xref) { "Non-monotonic revision xref chain" }
            xref = previous
        }
        return result.reversed()
    }

    /** Check the original signed rules against every later revision, including changes subsequently reverted. */
    fun fieldPermissionProblemsAfter(end: Int): List<String> {
        val original = revisionDocument(end)
        val index = revisionIndexEndingAt(end)
        return revisions.drop(index + 1).flatMap { boundary -> PdfPermissions.changes(original, revisionDocument(boundary.last + 1)) }.distinct()
    }

    fun requireSignable(name: String) {
        PdfPermissions.requireSignable(document, name)
        signatures.forEach { PdfPermissions.requireSignable(revisionDocument(it.revisionEnd), name) }
    }

    private val changeCache = HashMap<Int, RevisionChanges>()

    /** Changes introduced by each revision after the one ending at [end]. */
    fun changesAfter(end: Int): List<RevisionChanges> {
        val index = revisionIndexEndingAt(end)
        if (index < 0) return emptyList()
        val result = mutableListOf<RevisionChanges>()
        for (i in index + 1 until revisions.size) {
            val oldEnd = revisions[i - 1].last + 1
            val newEnd = revisions[i].last + 1
            result += changeCache.getOrPut(newEnd) {
                runCatching { PdfRevisionDiff.diff(revisionDocument(oldEnd), revisionDocument(newEnd), i, newEnd) }
                    .getOrElse { RevisionChanges(i, setOf(ChangeKind.OTHER), listOf("revision $i could not be compared: ${it.message}")) }
            }
        }
        val lastEnd = revisions.lastOrNull()?.let { it.last + 1 } ?: 0
        if (pdf.size > lastEnd && (lastEnd until pdf.size).any { !pdf[it].isWhitespace() }) {
            result += RevisionChanges(revisions.size, setOf(ChangeKind.OTHER), listOf("${pdf.size - lastEnd} bytes after the last %%EOF"))
        }
        return result
    }

    override fun close() {
        revisionDocuments.values.forEach { runCatching { it.close() } }
        document.close()
    }

    companion object {
        private val EOF = "%%EOF".encodeToByteArray()

        fun open(pdf: ByteArray): PdfInspection {
            val document = loadStrict(pdf)
            return try { PdfInspection(pdf, document) } catch (e: Exception) { document.close(); throw e }
        }

        private fun loadStrict(pdf: ByteArray): PDDocument {
            val source = RandomAccessReadBuffer(pdf)
            return try { PDFParser(source).parse(false) } catch (e: Exception) { source.close(); throw e }
        }

        private fun Byte.isWhitespace() = this == 0x20.toByte() || this == 0x0a.toByte() || this == 0x0d.toByte() || this == 0x09.toByte() || this == 0x00.toByte() || this == 0x0c.toByte()


    }
}

internal enum class ChangeKind { DSS, DOC_TIMESTAMP, SIGNATURE, FORM_FILL, ANNOTATION, OTHER }

internal class RevisionChanges(val revision: Int, val kinds: Set<ChangeKind>, val details: List<String>)

/** Object-level comparison of two consecutive revisions, classifying what an incremental update changed. */
internal object PdfRevisionDiff {

    fun diff(old: PDDocument, new: PDDocument, revision: Int, revisionEnd: Int): RevisionChanges {
        val kinds = mutableSetOf<ChangeKind>()
        val details = mutableListOf<String>()
        fun other(message: String) {
            kinds += ChangeKind.OTHER; details += message
        }

        val update = SignatureUpdate(revisionEnd, new, PdfPermissions.docMdpPermission(old))
        val oldXref = old.document.xrefTable
        val newXref = new.document.xrefTable
        val oldRoot = old.document.trailer.getItem(COSName.ROOT) as? COSObject
        val newRoot = new.document.trailer.getItem(COSName.ROOT) as? COSObject
        if (oldRoot == null || newRoot == null || !same(oldRoot, newRoot)) other("document catalog replaced")
        if (!same(old.document.trailer.getItem(COSName.INFO), new.document.trailer.getItem(COSName.INFO))) other("document information dictionary replaced")

        val protected = documentObjects(old) + documentObjects(new)
        val oldDss = dssObjects(old.documentCatalog.cosObject.getItem(COSName.DSS))
        val newDss = dssObjects(new.documentCatalog.cosObject.getItem(COSName.DSS))

        for (key in oldXref.keys) if (key !in newXref) other("object ${key.number} ${key.generation} removed")
        for ((key, offset) in newXref) {
            val previous = oldXref[key]
            if (previous == offset && offset >= 0) continue
            val newObject = new.document.getObjectFromPool(key).`object`
            if (previous == null) {
                when {
                    key in newDss -> kinds += ChangeKind.DSS
                    update.isAnnotation(newObject) -> kinds += ChangeKind.ANNOTATION
                    newObject is COSDictionary && newObject.getCOSName(COSName.TYPE) == COSName.DOC_TIME_STAMP -> kinds += ChangeKind.DOC_TIMESTAMP
                    newObject is COSDictionary && newObject.getCOSName(COSName.TYPE) == COSName.SIG -> kinds += ChangeKind.SIGNATURE
                }
                continue
            }
            val oldObject = old.document.getObjectFromPool(key).`object`
            if (same(oldObject, newObject)) continue
            if (key in newDss && key in oldDss && key !in protected && newObject !is COSStream) {
                kinds += ChangeKind.DSS; continue
            }
            val annotationArray = key in update.annotationArrays
            val problem = changeProblem(oldObject, newObject, update, annotationArray)
            if (problem == null && update.isFormField(newObject)) kinds += ChangeKind.FORM_FILL
            if (problem == null && update.isAnnotation(newObject)) kinds += ChangeKind.ANNOTATION
            if (problem != null) other("object ${key.number} ${key.generation}: $problem")
            else if (newObject is COSDictionary && newObject.getCOSName(COSName.TYPE) == COSName.CATALOG &&
                !same(oldObject.asDict()?.getItem(COSName.DSS), newObject.getItem(COSName.DSS))
            ) kinds += ChangeKind.DSS
        }
        return RevisionChanges(revision, kinds, details)
    }

    private class SignatureUpdate(val end: Int, document: PDDocument, val permission: Int?) {
        val fields = document.signatureFields.map { it.cosObject }.toSet()
        val formFields = PdfPermissions.formFields(document).values.map { it.cosObject }.toSet() - fields
        val annotations = document.pages.flatMap { it.annotations }.map { it.cosObject }.toSet()
        val annotationArrays = document.pages.mapNotNull { (it.cosObject.getItem(COSName.ANNOTS) as? COSObject)?.reference() }.toSet()
        fun isFormField(item: COSBase?) = permission in 2..3 && item in formFields
        fun isAnnotation(item: COSBase?) = permission == 3 && item in annotations &&
            item.asDict()?.getCOSName(COSName.SUBTYPE) in setOf(COSName.getPDFName("Text"), COSName.getPDFName("Highlight"), COSName.getPDFName("Underline"), COSName.getPDFName("StrikeOut"), COSName.getPDFName("Squiggly"), COSName.getPDFName("Ink"), COSName.getPDFName("Square"), COSName.getPDFName("Circle"), COSName.getPDFName("FreeText"), COSName.getPDFName("Stamp"))
    }

    @Suppress("DEPRECATION")
    private fun COSObject.reference(): COSObjectKey = key ?: COSObjectKey(objectNumber, generationNumber)

    private fun COSBase?.asDict(): COSDictionary? = (if (this is COSObject) `object` else this) as? COSDictionary
    private fun COSBase?.deref(): COSBase? = if (this is COSObject) `object` else this

    private fun differingKeys(a: COSDictionary, b: COSDictionary): Set<COSName> =
        (a.keySet() + b.keySet()).filter { !same(a.getItem(it), b.getItem(it)) }.toSet()

    /** Null when the modification of an existing object is one of those allowed after signing. */
    private fun changeProblem(old: COSBase?, new: COSBase?, update: SignatureUpdate, annotations: Boolean = false): String? {
        if (old is COSArray && new is COSArray) return arrayGrowthProblem(old, new, update, annotations)
        if (old !is COSDictionary || new !is COSDictionary || old is COSStream || new is COSStream) return "content modified"
        val diff = differingKeys(old, new)
        val type = new.getCOSName(COSName.TYPE)
        return when {
            update.isFormField(new) -> if ((diff - setOf(COSName.V, COSName.AP, COSName.AS)).isEmpty()) null else "non-value form field entries modified"
            update.isAnnotation(new) -> if ((diff - setOf(COSName.CONTENTS, COSName.RECT, COSName.AP, COSName.C, COSName.CA, COSName.M, COSName.F,
                COSName.getPDFName("QuadPoints"), COSName.getPDFName("InkList"))).isEmpty()) null else "unsupported annotation entries modified"
            type == COSName.CATALOG -> {
                val extra = diff - setOf(COSName.DSS, COSName.EXTENSIONS, COSName.ACRO_FORM)
                if (extra.isNotEmpty()) "catalog entries ${extra.joinToString { it.name }} modified"
                else {
                    val formProblem = if (COSName.ACRO_FORM in diff) acroFormProblem(old.getItem(COSName.ACRO_FORM), new.getItem(COSName.ACRO_FORM), update) else null
                    formProblem ?: if (COSName.EXTENSIONS in diff) extensionProblem(old.getDictionaryObject(COSName.EXTENSIONS).asDict(), new.getDictionaryObject(COSName.EXTENSIONS).asDict()) else null
                }
            }

            type == COSName.PAGE -> {
                val extra = diff - COSName.ANNOTS
                if (extra.isNotEmpty()) "page entries ${extra.joinToString { it.name }} modified"
                else arrayItemProblem(old.getItem(COSName.ANNOTS), new.getItem(COSName.ANNOTS), update, true)
            }

            new.getCOSName(COSName.FT) == COSName.SIG || old.getCOSName(COSName.FT) == COSName.SIG ||
                new.getCOSName(COSName.SUBTYPE) == COSName.WIDGET && new.getDictionaryObject(COSName.PARENT).asDict()?.getCOSName(COSName.FT) == COSName.SIG -> {
                val extra = diff - setOf(COSName.V, COSName.AP, COSName.AS, COSName.F)
                val oldField = if (old.getCOSName(COSName.FT) == COSName.SIG) old else old.getDictionaryObject(COSName.PARENT).asDict()
                when {
                    extra.isNotEmpty() -> "signature field entries ${extra.joinToString { it.name }} modified"
                    oldField?.getDictionaryObject(COSName.V) != null -> "existing signature value or appearance modified"
                    COSName.F in diff && (old.getInt(COSName.F) xor new.getInt(COSName.F)) and 4.inv() != 0 -> "signature widget flags modified"
                    !isSignatureFieldOrWidget(new, update) -> "signature field is not signed in this revision"
                    else -> null
                }
            }

            new.containsKey(COSName.FIELDS) || old.containsKey(COSName.FIELDS) -> acroFormProblem(old, new, update)
            else -> "entries ${diff.joinToString { it.name }} modified"
        }
    }

    private fun extensionProblem(old: COSDictionary?, new: COSDictionary?): String? {
        new ?: return "extensions removed"
        val changed = (old?.keySet().orEmpty() + new.keySet()).filter { !same(old?.getItem(it), new.getItem(it)) }
        if (changed.any { it != PdfNames.ESIC }) return "non-DSS extensions modified"
        val esic = new.getDictionaryObject(PdfNames.ESIC) as? COSDictionary ?: return "DSS extension removed"
        return if (esic.keySet() == setOf(PdfNames.BASE_VERSION, PdfNames.EXTENSION_LEVEL) &&
            esic.getCOSName(PdfNames.BASE_VERSION)?.name == "1.7" && esic.getInt(PdfNames.EXTENSION_LEVEL) == 1) null
        else "unsupported DSS extension"
    }

    private fun acroFormProblem(oldItem: COSBase?, newItem: COSBase?, update: SignatureUpdate): String? {
        if (oldItem is COSObject && newItem is COSObject && same(oldItem, newItem)) return null // compared as its own object
        val old = oldItem.asDict()
        val new = newItem.asDict() ?: return "AcroForm removed"
        if (old == null) {
            if ((new.keySet() - setOf(COSName.FIELDS, COSName.SIG_FLAGS)).isNotEmpty()) return "unsupported new AcroForm entries"
            val fields = new.getDictionaryObject(COSName.FIELDS) as? COSArray ?: return null
            return if ((0 until fields.size()).all { isSignatureFieldOrWidget(fields.getObject(it), update) }) null else "new non-signature form fields"
        }
        val extra = differingKeys(old, new) - setOf(COSName.FIELDS, COSName.SIG_FLAGS)
        if (extra.isNotEmpty()) return "AcroForm entries ${extra.joinToString { it.name }} modified"
        return arrayItemProblem(old.getItem(COSName.FIELDS), new.getItem(COSName.FIELDS), update)
    }

    private fun arrayItemProblem(oldItem: COSBase?, newItem: COSBase?, update: SignatureUpdate, annotations: Boolean = false): String? {
        if (oldItem is COSObject && newItem is COSObject && same(oldItem, newItem)) return null
        val new = newItem.deref() as? COSArray ?: return if (oldItem == null) null else "array removed"
        return arrayGrowthProblem(oldItem.deref() as? COSArray ?: COSArray(), new, update, annotations)
    }

    private fun arrayGrowthProblem(old: COSArray, new: COSArray, update: SignatureUpdate, annotations: Boolean = false): String? {
        if (new.size() < old.size() || (0 until old.size()).any { !same(old.get(it), new.get(it)) }) {
            return "existing array entry removed, reordered or replaced"
        }
        return if ((old.size() until new.size()).all { isSignatureFieldOrWidget(new.get(it), update) || annotations && update.isAnnotation(new.getObject(it)) }) null
        else "unsigned or non-signature entries added"
    }

    private fun isSignatureFieldOrWidget(item: COSBase?, update: SignatureUpdate): Boolean {
        val dict = item.asDict() ?: return false
        val field = if (dict.getCOSName(COSName.FT) == COSName.SIG) dict
        else if (dict.getCOSName(COSName.SUBTYPE) == COSName.WIDGET) dict.getDictionaryObject(COSName.PARENT) as? COSDictionary
        else null
        if (field?.getCOSName(COSName.FT) != COSName.SIG || field !in update.fields) return false
        val signature = field.getDictionaryObject(COSName.V) as? COSDictionary ?: return false
        // A leniently ignored, invalid document time-stamp must not authorize a visible overlay.
        if (signature.getCOSName(COSName.TYPE) == COSName.DOC_TIME_STAMP || signature.getNameAsString(COSName.SUB_FILTER) == PdfNames.ETSI_RFC3161.name) {
            val rectangle = dict.getDictionaryObject(COSName.RECT) as? COSArray
            if (rectangle != null) {
                val values = runCatching { rectangle.toFloatArray() }.getOrNull() ?: return false
                if (values.size != 4 || values[0] != values[2] && values[1] != values[3]) return false
            } else if (dict.containsKey(COSName.AP)) return false
        }
        val range = signature.getDictionaryObject(COSName.BYTERANGE) as? COSArray ?: return false
        val values = runCatching { IntArray(range.size()) { (range.getObject(it) as COSNumber).intValue() } }.getOrNull() ?: return false
        return values.size == 4 && values[0] == 0 && values.all { it >= 0 } &&
            values[2].toLong() + values[3] == update.end.toLong()
    }

    /** DSS references cannot grant an exemption to an object also used by the document or its appearances. */
    private fun documentObjects(document: PDDocument): Set<COSObjectKey> {
        val catalog = document.documentCatalog.cosObject
        val keys = HashSet<COSObjectKey>()
        fun walk(value: COSBase?) {
            when (value) {
                is COSObject -> if (keys.add(value.reference())) walk(value.`object`)
                is COSDictionary -> (value.keySet() - if (value === catalog) setOf(COSName.DSS) else emptySet())
                    .forEach { walk(value.getItem(it)) }
                is COSArray -> (0 until value.size()).forEach { walk(value.get(it)) }
                else -> Unit
            }
        }
        (catalog.keySet() - COSName.DSS).forEach { walk(catalog.getItem(it)) }
        walk(document.document.trailer.getItem(COSName.INFO))
        return keys
    }

    /** Only the DSS schema grants an exemption, never arbitrary objects reachable from an unknown entry. */
    private fun dssObjects(start: COSBase?): Set<COSObjectKey> {
        val keys = HashSet<COSObjectKey>()
        fun record(value: COSBase?) { if (value is COSObject) keys += value.reference() }
        fun streams(value: COSBase?) {
            val array = value.deref() as? COSArray ?: return
            if ((0 until array.size()).any { array.getObject(it) !is COSStream }) return
            record(value)
            for (i in 0 until array.size()) {
                if (array.getObject(i) is COSStream) record(array.get(i))
            }
        }
        fun entry(value: COSBase?, root: Boolean) {
            val dict = value.asDict()?.takeUnless { it is COSStream } ?: return
            val arrays = if (root) listOf(COSName.CERTS, COSName.CRLS, COSName.OCSPS)
                else listOf(PdfNames.CERT, PdfNames.CRL, PdfNames.OCSP)
            val allowed = arrays.toSet() + if (root) setOf(COSName.TYPE, COSName.VRI) else emptySet()
            if ((dict.keySet() - allowed).isNotEmpty()) return
            record(value)
            arrays.forEach { streams(dict.getItem(it)) }
            if (root) {
                val vriItem = dict.getItem(COSName.VRI)
                val vri = vriItem.asDict() ?: return
                record(vriItem)
                vri.keySet().forEach { entry(vri.getItem(it), false) }
            }
        }
        entry(start, true)
        return keys
    }

    /** Structural equality; indirect references are equal when they point to the same object number. */
    fun same(a: COSBase?, b: COSBase?): Boolean {
        if (a === b) return true
        val an = a ?: COSNull.NULL
        val bn = b ?: COSNull.NULL
        if (an is COSObject || bn is COSObject) {
            return an is COSObject && bn is COSObject && an.reference() == bn.reference()
        }
        return when (an) {
            is COSStream -> bn is COSStream && sameDictionary(an, bn) &&
                an.createRawInputStream().use { it.readAllBytes() }.contentEquals(bn.createRawInputStream().use { it.readAllBytes() })

            is COSDictionary -> bn is COSDictionary && bn !is COSStream && sameDictionary(an, bn)
            is COSArray -> bn is COSArray && an.size() == bn.size() && (0 until an.size()).all { same(an.get(it), bn.get(it)) }
            is COSString -> bn is COSString && an.bytes.contentEquals(bn.bytes)
            else -> an == bn
        }
    }

    private fun sameDictionary(a: COSDictionary, b: COSDictionary): Boolean =
        a.keySet() == b.keySet() && a.keySet().all { same(a.getItem(it), b.getItem(it)) }
}
