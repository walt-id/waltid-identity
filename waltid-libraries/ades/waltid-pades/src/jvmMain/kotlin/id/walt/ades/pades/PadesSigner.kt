package id.walt.ades.pades

import id.walt.ades.crypto.AdesDigestAlgorithm
import id.walt.ades.crypto.AdesSigningCredentials
import id.walt.ades.crypto.sha1
import id.walt.ades.tsp.TimeStampToken
import id.walt.ades.tsp.TimestampException
import id.walt.ades.tsp.TimestampSource
import id.walt.ades.validation.AdesLevel
import id.walt.ades.validation.ValidationData
import id.walt.ades.validation.ValidationDataCollector
import id.walt.ades.x509.X509Certificate
import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.pdfbox.Loader
import org.apache.pdfbox.cos.COSArray
import org.apache.pdfbox.cos.COSDictionary
import org.apache.pdfbox.cos.COSInteger
import org.apache.pdfbox.cos.COSName
import org.apache.pdfbox.cos.COSStream
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureOptions
import org.apache.pdfbox.pdmodel.interactive.form.PDSignatureField
import org.bouncycastle.asn1.ASN1Encoding
import org.bouncycastle.cms.CMSSignedData
import java.io.ByteArrayOutputStream
import kotlin.time.Clock

private val log = KotlinLogging.logger {}

/**
 * Creates and augments PAdES baseline signatures (ETSI EN 319 142-1) with Apache PDFBox incremental updates.
 *
 * @param timestampSource RFC 3161 time-stamps for B-T (signature-time-stamp or document time-stamp) and B-LTA
 * @param validationDataCollector certificates/CRLs/OCSP responses for the DSS (B-LT, before each archive time-stamp)
 */
class PadesSigner(
    private val timestampSource: TimestampSource? = null,
    private val validationDataCollector: ValidationDataCollector? = null,
) {

    /** Signs [pdf] with a new invisible signature field and returns the signed document (at [PadesSignatureParameters.level]). */
    suspend fun sign(
        pdf: ByteArray,
        credentials: AdesSigningCredentials,
        parameters: PadesSignatureParameters = PadesSignatureParameters(),
    ): ByteArray = sign(pdf, credentials, parameters, credentials.signingCertificate)

    internal suspend fun sign(
        pdf: ByteArray,
        credentials: AdesSigningCredentials,
        parameters: PadesSignatureParameters,
        referencedCertificate: X509Certificate,
    ): ByteArray {
        val level = parameters.level
        if (level >= AdesLevel.B_T) requireNotNull(timestampSource) { "A TimestampSource is required for ${level.code}" }
        if (level >= AdesLevel.B_LT) requireNotNull(validationDataCollector) { "A ValidationDataCollector is required for ${level.code}" }
        val signatureTimestamp = level >= AdesLevel.B_T && parameters.timestampMode == PadesTimestampMode.SIGNATURE_TIMESTAMP
        PdfInspection.open(pdf).use { check(it.docMdpPermission != 1) { "Document is certified with DocMDP P=1: no further signatures allowed" } }

        var size = parameters.contentsSize ?: estimateContentsSize(credentials, signatureTimestamp)
        var signed: ByteArray? = null
        for (attempt in 0 until 3) {
            val result = signOnce(pdf, credentials, parameters, size, signatureTimestamp, referencedCertificate)
            if (result.first != null) {
                signed = result.first; break
            }
            log.debug { "CMS of ${result.second} bytes does not fit into $size reserved bytes, retrying" }
            size = result.second + 4096
        }
        signed ?: error("Could not reserve enough space for the signature")
        if (level == AdesLevel.B_B || (level == AdesLevel.B_T && signatureTimestamp)) return signed
        return augment(
            signed, level,
            PadesAugmentationParameters(PadesTimestampMode.DOCUMENT_TIMESTAMP, parameters.timestampDigest, parameters.includeVri),
        )
    }

    private suspend fun signOnce(
        pdf: ByteArray,
        credentials: AdesSigningCredentials,
        parameters: PadesSignatureParameters,
        size: Int,
        signatureTimestamp: Boolean,
        referencedCertificate: X509Certificate,
    ): Pair<ByteArray?, Int> = Loader.loadPDF(pdf).use { doc ->
        val signature = PDSignature().apply {
            setFilter(PDSignature.FILTER_ADOBE_PPKLITE)
            setSubFilter(PdfNames.ETSI_CADES_DETACHED)
            parameters.signerName?.let(::setName)
            parameters.reason?.let(::setReason)
            parameters.location?.let(::setLocation)
            parameters.contactInfo?.let(::setContactInfo)
            signDate = (parameters.signingTime ?: Clock.System.now()).toCalendar()
        }
        SignatureOptions().use { options ->
            options.preferredSignatureSize = size
            doc.addSignature(signature, options)
            parameters.fieldName?.let { renameField(doc, signature, it) }
            val out = ByteArrayOutputStream()
            val external = doc.saveIncrementalForExternalSigning(out)
            val content = external.content.use { it.readAllBytes() }
            val cms = PadesCms.sign(
                content, credentials, parameters.signaturePolicy, parameters.commitment,
                timestampSource.takeIf { signatureTimestamp }, parameters.timestampDigest, referencedCertificate,
            )
            if (cms.size > size) return@use null to cms.size
            external.setSignature(cms)
            out.toByteArray() to cms.size
        }
    }

    private fun renameField(doc: PDDocument, signature: PDSignature, name: String) {
        val fields = doc.documentCatalog.acroForm?.fieldTree?.toList() ?: emptyList()
        require(fields.none { it.fullyQualifiedName == name }) { "A field named $name already exists" }
        fields.filterIsInstance<PDSignatureField>()
            .firstOrNull { it.cosObject.getDictionaryObject(COSName.V) === signature.cosObject }
            ?.partialName = name
    }

    /** Adds a document time-stamp (SubFilter ETSI.RFC3161, EN 319 142-1 5.4.3) covering the whole document. */
    suspend fun addDocumentTimestamp(
        pdf: ByteArray,
        digest: AdesDigestAlgorithm = AdesDigestAlgorithm.SHA256,
        contentsSize: Int? = null,
    ): ByteArray {
        val source = requireNotNull(timestampSource) { "A TimestampSource is required for document time-stamps" }
        var size = contentsSize ?: DEFAULT_TIMESTAMP_SIZE
        repeat(3) {
            val result = Loader.loadPDF(pdf).use { doc ->
                val signature = PDSignature().apply {
                    setType(COSName.DOC_TIME_STAMP)
                    setFilter(PDSignature.FILTER_ADOBE_PPKLITE)
                    setSubFilter(PdfNames.ETSI_RFC3161)
                }
                SignatureOptions().use { options ->
                    options.preferredSignatureSize = size
                    doc.addSignature(signature, options)
                    val out = ByteArrayOutputStream()
                    val external = doc.saveIncrementalForExternalSigning(out)
                    val imprint = digest.digest(external.content.use { it.readAllBytes() })
                    val token = source.timestamp(digest, imprint)
                    if (!TimeStampToken(token).tstInfo.messageImprint.contentEquals(imprint)) throw TimestampException("Document time-stamp does not cover the document")
                    if (token.size > size) return@use null to token.size
                    external.setSignature(token)
                    out.toByteArray() to token.size
                }
            }
            result.first?.let { return it }
            size = result.second + 2048
        }
        error("Could not reserve enough space for the document time-stamp")
    }

    /**
     * Adds a DSS incremental update (EN 319 142-1 5.4.2) with the certificates, CRLs and OCSP responses needed to
     * validate every signature, signature-time-stamp and document time-stamp, excluding values already in the DSS.
     * Returns [pdf] unchanged when nothing is missing.
     */
    suspend fun addValidationData(pdf: ByteArray, includeVri: Boolean = false): ByteArray {
        val collector = requireNotNull(validationDataCollector) { "A ValidationDataCollector is required for B-LT/B-LTA" }
        val plan = PdfInspection.open(pdf).use { inspection ->
            val existing = inspection.dss.toValidationData()
            val available = existing.copy()
            inspection.fields.forEach { f ->
                f.cms?.certificates?.forEach(available::add)
                f.timestampToken?.certificates?.forEach(available::add)
                f.signatureTimestampTokens.forEach { t -> runCatching { TimeStampToken(t).certificates }.getOrDefault(emptyList()).forEach(available::add) }
            }
            val targetsByField = inspection.fields.associateWith { targetsOf(it, available.certificates) }
            val required = collector.collect(targetsByField.values.flatten().distinct(), available).required
            val newData = required.minus(existing)
            if (!required.isEmpty) available.addAll(required)
            val vri = if (!includeVri) emptyMap() else targetsByField.mapNotNull { (field, targets) ->
                val key = field.contents.sha1().hexUpper()
                if (key in inspection.dss.vriKeys || targets.isEmpty()) null else key to collector.collect(targets, available).required
            }.toMap()
            if (newData.isEmpty && vri.isEmpty()) null else newData to vri
        } ?: return pdf
        return writeDss(pdf, plan.first, plan.second)
    }

    private suspend fun targetsOf(field: PdfSignatureField, pool: List<X509Certificate>): List<X509Certificate> {
        val out = mutableListOf<X509Certificate>()
        field.cms?.let { cms -> cms.signerInfos.firstOrNull()?.let { si -> cms.findSigner(si, pool)?.let(out::add) } }
        field.timestampToken?.findSigner(pool)?.let(out::add)
        field.signatureTimestampTokens.forEach { t -> runCatching { TimeStampToken(t).findSigner(pool) }.getOrNull()?.let(out::add) }
        return out
    }

    private fun writeDss(pdf: ByteArray, newData: ValidationData, vri: Map<String, ValidationData>): ByteArray = Loader.loadPDF(pdf).use { doc ->
        val catalog = doc.documentCatalog.cosObject
        val dss = catalog.getDictionaryObject(COSName.DSS) as? COSDictionary ?: COSDictionary().also { catalog.setItem(COSName.DSS, it) }
        dss.setItem(COSName.TYPE, PdfNames.DSS_TYPE)

        // existing streams by content, so VRI entries can reference them instead of duplicating values
        val streams = HashMap<String, COSStream>()
        fun index(name: COSName) {
            val array = dss.getDictionaryObject(name) as? COSArray ?: return
            for (i in 0 until array.size()) (array.getObject(i) as? COSStream)?.let { s ->
                streams[s.createInputStream().use { it.readAllBytes() }.contentHashCode().toString() + name.name] = s
            }
        }
        listOf(COSName.CERTS, COSName.CRLS, COSName.OCSPS).forEach(::index)

        fun streamFor(name: COSName, bytes: ByteArray): COSStream = streams.getOrPut(bytes.contentHashCode().toString() + name.name) {
            val stream = doc.document.createCOSStream()
            stream.createOutputStream(COSName.FLATE_DECODE).use { it.write(bytes) }
            val array = dss.getDictionaryObject(name) as? COSArray ?: COSArray().also { dss.setItem(name, it) }
            array.add(stream)
            array.isNeedToBeUpdated = true
            stream
        }

        fun add(data: ValidationData): Triple<List<COSStream>, List<COSStream>, List<COSStream>> = Triple(
            data.certificates.map { streamFor(COSName.CERTS, it.encoded) },
            data.crls.map { streamFor(COSName.CRLS, it.encoded) },
            data.ocsps.map { streamFor(COSName.OCSPS, it.encoded) },
        )
        add(newData)

        if (vri.isNotEmpty()) {
            val vriDict = dss.getDictionaryObject(COSName.VRI) as? COSDictionary ?: COSDictionary().also { dss.setItem(COSName.VRI, it) }
            for ((key, data) in vri) {
                val (certs, crls, ocsps) = add(data)
                val entry = COSDictionary()
                fun put(name: COSName, items: List<COSStream>) {
                    if (items.isNotEmpty()) entry.setItem(name, COSArray(items))
                }
                put(PdfNames.CERT, certs); put(PdfNames.CRL, crls); put(PdfNames.OCSP, ocsps)
                vriDict.setItem(COSName.getPDFName(key), entry)
            }
            vriDict.isNeedToBeUpdated = true
        }

        // EN 319 142-1 5.6.1: declare the DSS extension for ISO 32000-1 documents
        if (doc.version < 2.0f) {
            val extensions = catalog.getDictionaryObject(COSName.EXTENSIONS) as? COSDictionary ?: COSDictionary().also { catalog.setItem(COSName.EXTENSIONS, it) }
            if (!extensions.containsKey(PdfNames.ESIC)) {
                extensions.setItem(PdfNames.ESIC, COSDictionary().apply {
                    setItem(PdfNames.BASE_VERSION, COSName.getPDFName("1.7"))
                    setItem(PdfNames.EXTENSION_LEVEL, COSInteger.get(1))
                })
                extensions.isNeedToBeUpdated = true
            }
        }
        dss.isNeedToBeUpdated = true
        catalog.isNeedToBeUpdated = true
        val out = ByteArrayOutputStream()
        doc.saveIncremental(out)
        out.toByteArray()
    }

    /**
     * Augments all signatures of [pdf] to [targetLevel]: adds trusted time (B-T), a DSS with the missing validation
     * data (B-LT) and a new document time-stamp (B-LTA). Calling it again with B-LTA on a B-LTA document adds the
     * validation data of the previous document time-stamp and a new document time-stamp (EN 319 142-1 6.3 w, x).
     */
    suspend fun augment(
        pdf: ByteArray,
        targetLevel: AdesLevel,
        parameters: PadesAugmentationParameters = PadesAugmentationParameters(),
    ): ByteArray {
        var doc = pdf
        if (targetLevel >= AdesLevel.B_T) doc = ensureTrustedTime(doc, parameters)
        if (targetLevel >= AdesLevel.B_LT) doc = addValidationData(doc, parameters.includeVri)
        if (targetLevel >= AdesLevel.B_LTA) doc = addDocumentTimestamp(doc, parameters.timestampDigest, parameters.timestampContentsSize)
        return doc
    }

    private suspend fun ensureTrustedTime(pdf: ByteArray, parameters: PadesAugmentationParameters): ByteArray {
        val source = requireNotNull(timestampSource) { "A TimestampSource is required for B-T" }
        val untimed = PdfInspection.open(pdf).use { inspection ->
            inspection.signatures.filter { sig ->
                sig.signatureTimestampTokens.isEmpty() && inspection.documentTimestamps.none { it.revisionEnd > sig.revisionEnd }
            }.map { Triple(it.gapStart, it.gapEnd, it.der) }
        }
        if (untimed.isEmpty()) return pdf
        val single = untimed.singleOrNull()
        if (parameters.timestampMode == PadesTimestampMode.SIGNATURE_TIMESTAMP && single?.third != null) {
            // Only the latest signature can be extended in place: any later signature/time-stamp covers its /Contents.
            timestampInPlace(pdf, single.first, single.second, single.third!!, source, parameters.timestampDigest)?.let { return it }
        }
        return addDocumentTimestamp(pdf, parameters.timestampDigest, parameters.timestampContentsSize)
    }

    /** Rewrites /Contents with an added signature-time-stamp; null when it is covered by later revisions or too small. */
    private suspend fun timestampInPlace(pdf: ByteArray, gapStart: Int, gapEnd: Int, der: ByteArray, source: TimestampSource, digest: AdesDigestAlgorithm): ByteArray? {
        val later = PdfInspection.open(pdf).use { i -> i.fields.any { it.gapStart != gapStart && it.revisionEnd > gapStart } }
        if (later) return null
        val cms = PadesCms.addSignatureTimestamp(CMSSignedData(der), source, digest).getEncoded(ASN1Encoding.DER)
        val hex = cms.hexUpper()
        val capacity = gapEnd - gapStart - 2
        if (hex.length > capacity) {
            log.debug { "reserved space of ${capacity / 2} bytes too small for ${cms.size} bytes, using a document time-stamp" }
            return null
        }
        val out = pdf.copyOf()
        val padded = hex.padEnd(capacity, '0').encodeToByteArray()
        padded.copyInto(out, gapStart + 1)
        return out
    }

    private fun estimateContentsSize(credentials: AdesSigningCredentials, signatureTimestamp: Boolean): Int =
        4096 + credentials.certificateChain.sumOf { it.encoded.size } + (if (signatureTimestamp) DEFAULT_TIMESTAMP_SIZE else 0)

    companion object {
        private const val DEFAULT_TIMESTAMP_SIZE = 12_000
    }
}
