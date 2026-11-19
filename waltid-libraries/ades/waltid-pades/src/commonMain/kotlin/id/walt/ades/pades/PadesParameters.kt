package id.walt.ades.pades

import id.walt.ades.crypto.AdesDigestAlgorithm
import id.walt.ades.model.Commitment
import id.walt.ades.model.SignaturePolicyIdentifier
import id.walt.ades.validation.AdesLevel
import kotlin.time.Instant

/** How the trusted time required for PAdES-B-T is provided (EN 319 142-1 Table 1, requirement n). */
enum class PadesTimestampMode {
    /** signature-time-stamp unsigned attribute inside the CMS SignerInfo. */
    SIGNATURE_TIMESTAMP,

    /** Document time-stamp (SubFilter ETSI.RFC3161) in a separate incremental update. */
    DOCUMENT_TIMESTAMP,
}

/** Parameters of a new PAdES baseline signature. */
class PadesSignatureParameters(
    val level: AdesLevel = AdesLevel.B_B,
    /** Value of the signature dictionary entry M (claimed signing time); defaults to the current time. */
    val signingTime: Instant? = null,
    /** Signature dictionary /Reason; not allowed together with [commitment] or [signaturePolicy] (Table 1 m). */
    val reason: String? = null,
    val location: String? = null,
    val contactInfo: String? = null,
    /** Signature dictionary /Name; should only be used when the name cannot be extracted from the certificate. */
    val signerName: String? = null,
    val signaturePolicy: SignaturePolicyIdentifier? = null,
    /** commitment-type-indication; [Commitment.id] may be a TS 119 172-1 URI or an OID. */
    val commitment: Commitment? = null,
    val timestampMode: PadesTimestampMode = PadesTimestampMode.SIGNATURE_TIMESTAMP,
    /** Partial name of the signature field to create; PDFBox chooses one when null. */
    val fieldName: String? = null,
    /** Bytes reserved for the CMS object in /Contents; estimated when null (retried with more space if too small). */
    val contentsSize: Int? = null,
    /** Digest algorithm for time-stamp message imprints. */
    val timestampDigest: AdesDigestAlgorithm = AdesDigestAlgorithm.SHA256,
    /** Write per-signature VRI dictionaries when adding validation data (Table 1 v: optional). */
    val includeVri: Boolean = false,
    /** Fully qualified name of an existing empty signature field. Mutually exclusive with [fieldName]. */
    val existingFieldName: String? = null,
    /** Zero-based page for a new visible signature; existing fields retain their page and rectangle. */
    val pageIndex: Int = 0,
    val rectangle: PadesSignatureRectangle? = null,
    val appearance: PadesSignatureAppearance? = null,
    val certificationPermission: PadesCertificationPermission? = null,
    val fieldLock: PadesFieldLock? = null,
) {
    init {
        require(listOfNotNull(fieldName, existingFieldName).all { it.isNotBlank() }) { "Signature field names must not be blank" }
        require(fieldName == null || existingFieldName == null) { "Choose a new field name or an existing field" }
        require(pageIndex >= 0) { "Page index must be non-negative" }
        require(rectangle == null || existingFieldName == null) { "Existing fields retain their rectangle" }
        require(rectangle == null || appearance != null) { "A rectangle requires an appearance" }
        require(appearance == null || rectangle != null || existingFieldName != null) { "A new visible signature requires a rectangle" }
        require(reason == null || (commitment == null && signaturePolicy == null)) {
            "/Reason shall not be used together with commitment-type-indication or signature-policy-identifier (EN 319 142-1 6.3 d, m)"
        }
    }
}

/** Parameters for augmenting existing PAdES signatures. */
class PadesAugmentationParameters(
    /**
     * Preferred way to add the B-T time. [PadesTimestampMode.SIGNATURE_TIMESTAMP] rewrites the signature's
     * /Contents in place, which is only possible when it has enough reserved space and no later signature or document
     * time-stamp covers it; otherwise a document time-stamp is added.
     */
    val timestampMode: PadesTimestampMode = PadesTimestampMode.DOCUMENT_TIMESTAMP,
    val timestampDigest: AdesDigestAlgorithm = AdesDigestAlgorithm.SHA256,
    val includeVri: Boolean = false,
    /** Bytes reserved for each document time-stamp token. */
    val timestampContentsSize: Int? = null,
)

/** Rectangle in unrotated PDF user-space coordinates, measured from the lower-left corner of the page. */
data class PadesSignatureRectangle(val x: Float, val y: Float, val width: Float, val height: Float) {
    init {
        require(listOf(x, y, width, height).all { it.isFinite() } && width > 0 && height > 0) { "A finite, positive signature rectangle is required" }
    }
}

/** A signed appearance. The image is fitted above the text; font bytes allow characters outside Helvetica. */
class PadesSignatureAppearance(
    val text: String,
    val image: ByteArray? = null,
    val font: ByteArray? = null,
    val fontSize: Float = 12f,
) {
    init {
        require(text.isNotBlank() || image != null) { "A visible signature needs text or an image" }
        require(fontSize.isFinite() && fontSize in 4f..72f) { "Font size must be between 4 and 72 points" }
    }
}

/** DocMDP permissions of a certification signature (ISO 32000-1 12.8.2.2). */
enum class PadesCertificationPermission(val value: Int) {
    NO_CHANGES(1), FORM_FILLING_AND_SIGNING(2), FORM_FILLING_SIGNING_AND_ANNOTATIONS(3),
}

enum class PadesFieldLockAction { ALL, INCLUDE, EXCLUDE }

/** Field names are fully qualified; a parent name also selects its descendants. */
class PadesFieldLock(val action: PadesFieldLockAction = PadesFieldLockAction.ALL, val fields: List<String> = emptyList()) {
    init {
        require(fields.all { it.isNotBlank() }) { "Locked field names must not be blank" }
        require(action == PadesFieldLockAction.ALL || fields.isNotEmpty()) { "Include/exclude locks require field names" }
        require(action != PadesFieldLockAction.ALL || fields.isEmpty()) { "An all-fields lock does not take field names" }
    }
}
