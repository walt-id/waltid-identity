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
) {
    init {
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
