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
import org.apache.pdfbox.Loader
import org.apache.pdfbox.cos.COSArray
import org.apache.pdfbox.cos.COSBase
import org.apache.pdfbox.cos.COSDictionary
import org.apache.pdfbox.cos.COSName
import org.apache.pdfbox.cos.COSNull
import org.apache.pdfbox.cos.COSObject
import org.apache.pdfbox.cos.COSObjectKey
import org.apache.pdfbox.cos.COSStream
import org.apache.pdfbox.cos.COSString
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature

internal object PdfNames {
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
    val revisionEnd: Int = byteRange?.let { it[2] + it[3] } ?: Int.MAX_VALUE

    val gapStart: Int get() = byteRange?.get(1) ?: -1
    val gapEnd: Int get() = byteRange?.get(2) ?: -1

    val signedContent: ByteArray? = byteRange?.let { br ->
        if (br[0] < 0 || br[1] < 0 || br[2] < 0 || br[3] < 0 || br[0] + br[1] > br[2] || br[2] + br[3] > pdf.size) null
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
                streamBytes(dict.getDictionaryObject(certKey) as? COSArray).forEach { b ->
                    runCatching { X509Certificate(b) }.onSuccess(certs::add).onFailure { problems += "$where: invalid certificate: ${it.message}" }
                }
                streamBytes(dict.getDictionaryObject(crlKey) as? COSArray).forEach { b ->
                    runCatching { X509Crl(b) }.onSuccess(crls::add).onFailure { problems += "$where: invalid CRL: ${it.message}" }
                }
                streamBytes(dict.getDictionaryObject(ocspKey) as? COSArray).forEach { b ->
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
    val docMdpPermission: Int? by lazy {
        val perms = document.documentCatalog.cosObject.getDictionaryObject(COSName.PERMS) as? COSDictionary
        val certification = perms?.getDictionaryObject(COSName.getPDFName("DocMDP")) as? COSDictionary ?: return@lazy null
        val references = certification.getDictionaryObject(COSName.getPDFName("Reference")) as? COSArray
        (0 until (references?.size() ?: 0)).firstNotNullOfOrNull { i ->
            (references!!.getObject(i) as? COSDictionary)
                ?.takeIf { it.getCOSName(COSName.getPDFName("TransformMethod"))?.name == "DocMDP" }
                ?.let { (it.getDictionaryObject(COSName.getPDFName("TransformParams")) as? COSDictionary)?.getInt(COSName.P, 2) ?: 2 }
        } ?: 2
    }

    /** Revisions as [start of "%%EOF", end of the line following it). */
    val revisions: List<IntRange> = findEofMarkers(pdf)

    private val revisionDocuments = HashMap<Int, PDDocument>()

    /** Index of the revision that ends at [offset] (end of a ByteRange), or -1. */
    fun revisionIndexEndingAt(offset: Int): Int = revisions.indexOfFirst { offset >= it.first + EOF.size && offset <= it.last + 1 }

    fun revisionDocument(end: Int): PDDocument =
        if (end >= pdf.size) document else revisionDocuments.getOrPut(end) { Loader.loadPDF(pdf.copyOf(end)) }

    fun dssAt(end: Int): DssContent = if (end >= pdf.size) dss else DssContent.read(revisionDocument(end))

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
                runCatching { PdfRevisionDiff.diff(revisionDocument(oldEnd), revisionDocument(newEnd), i) }
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

        fun open(pdf: ByteArray) = PdfInspection(pdf, Loader.loadPDF(pdf))

        private fun Byte.isWhitespace() = this == 0x20.toByte() || this == 0x0a.toByte() || this == 0x0d.toByte() || this == 0x09.toByte() || this == 0x00.toByte() || this == 0x0c.toByte()

        fun findEofMarkers(pdf: ByteArray): List<IntRange> {
            val out = mutableListOf<IntRange>()
            var i = 0
            while (i <= pdf.size - EOF.size) {
                if (pdf[i] == '%'.code.toByte() && (0 until EOF.size).all { pdf[i + it] == EOF[it] }) {
                    var end = i + EOF.size
                    if (end < pdf.size && pdf[end] == '\r'.code.toByte()) end++
                    if (end < pdf.size && pdf[end] == '\n'.code.toByte()) end++
                    out += i until end
                    i = end
                } else i++
            }
            return out
        }
    }
}

internal enum class ChangeKind { DSS, DOC_TIMESTAMP, SIGNATURE, OTHER }

internal class RevisionChanges(val revision: Int, val kinds: Set<ChangeKind>, val details: List<String>)

/** Object-level comparison of two consecutive revisions, classifying what an incremental update changed. */
internal object PdfRevisionDiff {

    fun diff(old: PDDocument, new: PDDocument, revision: Int): RevisionChanges {
        val kinds = mutableSetOf<ChangeKind>()
        val details = mutableListOf<String>()
        fun other(message: String) {
            kinds += ChangeKind.OTHER; details += message
        }

        val oldXref = old.document.xrefTable
        val newXref = new.document.xrefTable
        val oldRoot = old.document.trailer.getItem(COSName.ROOT) as? COSObject
        val newRoot = new.document.trailer.getItem(COSName.ROOT) as? COSObject
        if (oldRoot == null || newRoot == null || !same(oldRoot, newRoot)) other("document catalog replaced")
        if (!same(old.document.trailer.getItem(COSName.INFO), new.document.trailer.getItem(COSName.INFO))) other("document information dictionary replaced")

        val oldDss = reachable(old.documentCatalog.cosObject.getItem(COSName.DSS))
        val newDss = reachable(new.documentCatalog.cosObject.getItem(COSName.DSS))

        for (key in oldXref.keys) if (key !in newXref) other("object ${key.number} ${key.generation} removed")
        for ((key, offset) in newXref) {
            val previous = oldXref[key]
            if (previous == offset) continue
            val newObject = new.document.getObjectFromPool(key).`object`
            if (newObject is COSStream && newObject.getCOSName(COSName.TYPE).let { it == COSName.XREF || it == COSName.OBJ_STM }) continue
            if (previous == null) {
                when {
                    key in newDss -> kinds += ChangeKind.DSS
                    newObject is COSDictionary && newObject.getCOSName(COSName.TYPE) == COSName.DOC_TIME_STAMP -> kinds += ChangeKind.DOC_TIMESTAMP
                    newObject is COSDictionary && newObject.getCOSName(COSName.TYPE) == COSName.SIG -> kinds += ChangeKind.SIGNATURE
                }
                continue
            }
            val oldObject = old.document.getObjectFromPool(key).`object`
            if (same(oldObject, newObject)) continue
            if (key in newDss && key in oldDss && newObject !is COSStream) {
                kinds += ChangeKind.DSS; continue
            }
            val problem = changeProblem(oldObject, newObject)
            if (problem != null) other("object ${key.number} ${key.generation}: $problem")
            else if (newObject is COSDictionary && newObject.getCOSName(COSName.TYPE) == COSName.CATALOG &&
                !same(oldObject.asDict()?.getItem(COSName.DSS), newObject.getItem(COSName.DSS))
            ) kinds += ChangeKind.DSS
        }
        return RevisionChanges(revision, kinds, details)
    }

    @Suppress("DEPRECATION")
    private fun COSObject.reference(): COSObjectKey = key ?: COSObjectKey(objectNumber, generationNumber)

    private fun COSBase?.asDict(): COSDictionary? = (if (this is COSObject) `object` else this) as? COSDictionary
    private fun COSBase?.deref(): COSBase? = if (this is COSObject) `object` else this

    private fun differingKeys(a: COSDictionary, b: COSDictionary): Set<COSName> =
        (a.keySet() + b.keySet()).filter { !same(a.getItem(it), b.getItem(it)) }.toSet()

    /** Null when the modification of an existing object is one of those allowed after signing. */
    private fun changeProblem(old: COSBase?, new: COSBase?): String? {
        if (old is COSArray && new is COSArray) return arrayGrowthProblem(old, new)
        if (old !is COSDictionary || new !is COSDictionary || old is COSStream || new is COSStream) return "content modified"
        val diff = differingKeys(old, new)
        val type = new.getCOSName(COSName.TYPE)
        return when {
            type == COSName.CATALOG -> {
                val extra = diff - setOf(COSName.DSS, COSName.EXTENSIONS, COSName.ACRO_FORM)
                if (extra.isNotEmpty()) "catalog entries ${extra.joinToString { it.name }} modified"
                else if (COSName.ACRO_FORM in diff) acroFormProblem(old.getItem(COSName.ACRO_FORM), new.getItem(COSName.ACRO_FORM))
                else null
            }

            type == COSName.PAGE -> {
                val extra = diff - COSName.ANNOTS
                if (extra.isNotEmpty()) "page entries ${extra.joinToString { it.name }} modified"
                else arrayItemProblem(old.getItem(COSName.ANNOTS), new.getItem(COSName.ANNOTS))
            }

            new.getCOSName(COSName.FT) == COSName.SIG || old.getCOSName(COSName.FT) == COSName.SIG -> {
                val extra = diff - setOf(COSName.V, COSName.AP, COSName.AS)
                when {
                    extra.isNotEmpty() -> "signature field entries ${extra.joinToString { it.name }} modified"
                    COSName.V in diff && old.getItem(COSName.V) != null -> "existing signature value replaced"
                    else -> null
                }
            }

            new.containsKey(COSName.FIELDS) || old.containsKey(COSName.FIELDS) -> acroFormProblem(old, new)
            else -> "entries ${diff.joinToString { it.name }} modified"
        }
    }

    private fun acroFormProblem(oldItem: COSBase?, newItem: COSBase?): String? {
        if (oldItem is COSObject && newItem is COSObject && same(oldItem, newItem)) return null // compared as its own object
        val old = oldItem.asDict()
        val new = newItem.asDict() ?: return "AcroForm removed"
        if (old == null) {
            val fields = new.getDictionaryObject(COSName.FIELDS) as? COSArray ?: return null
            return if ((0 until fields.size()).all { isSignatureFieldOrWidget(fields.getObject(it)) }) null else "new non-signature form fields"
        }
        val extra = differingKeys(old, new) - setOf(COSName.FIELDS, COSName.SIG_FLAGS)
        if (extra.isNotEmpty()) return "AcroForm entries ${extra.joinToString { it.name }} modified"
        return arrayItemProblem(old.getItem(COSName.FIELDS), new.getItem(COSName.FIELDS))
    }

    private fun arrayItemProblem(oldItem: COSBase?, newItem: COSBase?): String? {
        if (oldItem is COSObject && newItem is COSObject && same(oldItem, newItem)) return null
        val new = newItem.deref() as? COSArray ?: return if (oldItem == null) null else "array removed"
        return arrayGrowthProblem(oldItem.deref() as? COSArray ?: COSArray(), new)
    }

    private fun arrayGrowthProblem(old: COSArray, new: COSArray): String? {
        val remaining = (0 until new.size()).map { new.get(it) }.toMutableList()
        for (i in 0 until old.size()) {
            val idx = remaining.indexOfFirst { same(old.get(i), it) }
            if (idx < 0) return "existing array entry removed or replaced"
            remaining.removeAt(idx)
        }
        return if (remaining.all { isSignatureFieldOrWidget(it.deref()) }) null else "non-signature entries added"
    }

    private fun isSignatureFieldOrWidget(item: COSBase?): Boolean {
        val dict = item.deref() as? COSDictionary ?: return false
        if (dict.getCOSName(COSName.FT) == COSName.SIG) return true
        val parent = dict.getDictionaryObject(COSName.PARENT) as? COSDictionary
        return dict.getCOSName(COSName.SUBTYPE) == COSName.WIDGET && parent?.getCOSName(COSName.FT) == COSName.SIG
    }

    private fun reachable(start: COSBase?): Set<COSObjectKey> {
        val keys = HashSet<COSObjectKey>()
        fun walk(b: COSBase?) {
            when (b) {
                is COSObject -> if (keys.add(b.reference())) walk(b.`object`)
                is COSDictionary -> b.values.forEach(::walk)
                is COSArray -> (0 until b.size()).forEach { walk(b.get(it)) }
                else -> {}
            }
        }
        walk(start)
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
