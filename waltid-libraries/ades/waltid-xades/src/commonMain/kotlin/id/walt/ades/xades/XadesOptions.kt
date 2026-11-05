package id.walt.ades.xades

import id.walt.ades.crypto.AdesDigestAlgorithm
import id.walt.ades.model.Commitment
import id.walt.ades.model.SignatureProductionPlace
import id.walt.ades.model.SignaturePolicyIdentifier
import id.walt.ades.transport.AdesTransport
import id.walt.ades.tsp.TimestampSource
import id.walt.ades.validation.AdesLevel
import id.walt.ades.x509.X509Certificate
import kotlin.time.Instant

/** XML namespaces and identifiers of ETSI EN 319 132-1 and XMLDSig. */
object XadesNamespaces {
    const val DS = "http://www.w3.org/2000/09/xmldsig#"
    const val XADES = "http://uri.etsi.org/01903/v1.3.2#"
    const val XADES_141 = "http://uri.etsi.org/01903/v1.4.1#"
    const val SIGNED_PROPERTIES_TYPE = "http://uri.etsi.org/01903#SignedProperties"
    const val OBJECT_TYPE = "http://www.w3.org/2000/09/xmldsig#Object"
    const val ENVELOPED_TRANSFORM = "http://www.w3.org/2000/09/xmldsig#enveloped-signature"
    const val BASE64_TRANSFORM = "http://www.w3.org/2000/09/xmldsig#base64"
    const val ENCODING_BASE64 = "http://www.w3.org/2000/09/xmldsig#base64"
}

/** How the signed data objects relate to the ds:Signature element. */
enum class XadesPackaging { ENVELOPED, ENVELOPING, DETACHED }

/** Canonicalization algorithms allowed by EN 319 132-1 6.3 d) (comment-omitting variants for generation). */
enum class XadesCanonicalization(val uri: String) {
    C14N("http://www.w3.org/TR/2001/REC-xml-c14n-20010315"),
    C14N_11("http://www.w3.org/2006/12/xml-c14n11"),
    EXCLUSIVE("http://www.w3.org/2001/10/xml-exc-c14n#");

    companion object {
        /** Default when a time-stamp container carries no ds:CanonicalizationMethod (XMLDSig / XAdES default). */
        const val DEFAULT_URI = "http://www.w3.org/TR/2001/REC-xml-c14n-20010315"

        val ALLOWED_URIS = setOf(
            C14N.uri, C14N_11.uri, EXCLUSIVE.uri,
            "http://www.w3.org/TR/2001/REC-xml-c14n-20010315#WithComments",
            "http://www.w3.org/2006/12/xml-c14n11#WithComments",
            "http://www.w3.org/2001/10/xml-exc-c14n#WithComments",
        )
    }
}

/**
 * A data object to sign or a detached document needed for validation. [name] becomes the ds:Reference URI of
 * detached signatures (percent-encoded). For enveloping signatures, [xml] selects whether the content is embedded as
 * XML (must be well-formed) or base64-encoded.
 */
class XadesDocument(
    val name: String,
    val content: ByteArray,
    val mimeType: String = "application/octet-stream",
    val xml: Boolean = mimeType.endsWith("xml"),
    val description: String? = null,
)

/** Supplies detached data objects for validation by reference URI; return null when unknown. */
fun interface XadesDocumentResolver {
    suspend fun resolve(uri: String): ByteArray?
}

/** Options for B-T/B-LT/B-LTA augmentation (used both while signing and for later augmentation). */
class XadesAugmentationOptions(
    /** TSA for SignatureTimeStamp and AllDataObjectsTimeStamp. */
    val timestampSource: TimestampSource? = null,
    /** TSA for ArchiveTimeStamp; defaults to [timestampSource]. */
    val archiveTimestampSource: TimestampSource? = timestampSource,
    val timestampDigestAlgorithm: AdesDigestAlgorithm = AdesDigestAlgorithm.SHA256,
    /** Canonicalization indicated in (and used for) time-stamp containers. */
    val canonicalization: XadesCanonicalization = XadesCanonicalization.EXCLUSIVE,
    /** Network access to fetch OCSP responses, CRLs and missing CA certificates for B-LT/B-LTA. */
    val transport: AdesTransport? = null,
    val trustAnchors: List<X509Certificate> = emptyList(),
    /** Fail the augmentation when revocation data or issuer certificates cannot be collected. */
    val requireCompleteValidationData: Boolean = true,
)

/** XAdES baseline signature creation options (ETSI EN 319 132-1 clause 6). */
class XadesSignatureOptions(
    val level: AdesLevel = AdesLevel.B_B,
    val digestAlgorithm: AdesDigestAlgorithm = AdesDigestAlgorithm.SHA256,
    /** ds:SignedInfo canonicalization and the c14n transform of data references. */
    val canonicalization: XadesCanonicalization = XadesCanonicalization.EXCLUSIVE,
    /** Claimed signing time; defaults to the current time. */
    val signingTime: Instant? = null,
    val signaturePolicy: SignaturePolicyIdentifier? = null,
    /** Adds SignaturePolicyImplied when no explicit [signaturePolicy] is given. */
    val signaturePolicyImplied: Boolean = false,
    /** SPDocSpecification identifier (URI or OID) for the SignaturePolicyStore, required when the policy document is embedded. */
    val signaturePolicyDocSpecification: String? = null,
    val productionPlace: SignatureProductionPlace? = null,
    val claimedRoles: List<String> = emptyList(),
    /** Commitment type indications (Identifier is a URI, see [id.walt.ades.model.CommitmentType]); they apply to all signed data objects. */
    val commitments: List<Commitment> = emptyList(),
    /** Adds IssuerSerialV2 to SigningCertificateV2 (EN 319 132-1 6.3 j) recommends omitting it). */
    val includeIssuerSerial: Boolean = false,
    /** Put the whole credential chain into ds:KeyInfo/X509Data (the signing certificate is always included). */
    val includeCertificateChain: Boolean = true,
    /** Adds an AllDataObjectsTimeStamp (content time-stamp) obtained from the augmentation time-stamp source. */
    val contentTimestamp: Boolean = false,
    /** Id of the ds:Signature element; random when null. */
    val signatureId: String? = null,
    val augmentation: XadesAugmentationOptions = XadesAugmentationOptions(),
)

class XadesException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
