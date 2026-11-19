package id.walt.ades.model

import id.walt.ades.crypto.AdesDigestAlgorithm
import kotlinx.serialization.Serializable

/** Commitment types of ETSI TS 119 172-1 Annex B (URIs shared by XAdES/JAdES/CB-AdES, OIDs for CAdES/PAdES). */
enum class CommitmentType(val uri: String, val oid: String) {
    PROOF_OF_ORIGIN("http://uri.etsi.org/01903/v1.2.2#ProofOfOrigin", "1.2.840.113549.1.9.16.6.1"),
    PROOF_OF_RECEIPT("http://uri.etsi.org/01903/v1.2.2#ProofOfReceipt", "1.2.840.113549.1.9.16.6.2"),
    PROOF_OF_DELIVERY("http://uri.etsi.org/01903/v1.2.2#ProofOfDelivery", "1.2.840.113549.1.9.16.6.3"),
    PROOF_OF_SENDER("http://uri.etsi.org/01903/v1.2.2#ProofOfSender", "1.2.840.113549.1.9.16.6.4"),
    PROOF_OF_APPROVAL("http://uri.etsi.org/01903/v1.2.2#ProofOfApproval", "1.2.840.113549.1.9.16.6.5"),
    PROOF_OF_CREATION("http://uri.etsi.org/01903/v1.2.2#ProofOfCreation", "1.2.840.113549.1.9.16.6.6");

    companion object {
        fun fromUri(uri: String) = entries.firstOrNull { it.uri == uri }
    }
}

/** A commitment with optional human-readable description (CB-AdES oId.desc / XAdES Description). */
@Serializable
data class Commitment(val id: String, val description: String? = null, val documentationReferences: List<String> = emptyList())

/** Signature production place (schema.org PostalAddress in JAdES/CB-AdES, SignatureProductionPlaceV2 in XAdES). */
@Serializable
data class SignatureProductionPlace(
    val country: String? = null,
    val locality: String? = null,
    val region: String? = null,
    val postOfficeBoxNumber: String? = null,
    val postalCode: String? = null,
    val streetAddress: String? = null,
) {
    init {
        require(listOfNotNull(country, locality, region, postOfficeBoxNumber, postalCode, streetAddress).isNotEmpty()) {
            "Signature production place needs at least one member"
        }
    }
}

/** Explicit signature policy identifier with the digest of the policy document. */
class SignaturePolicyIdentifier(
    val id: String,
    val digestAlgorithm: AdesDigestAlgorithm,
    val digest: ByteArray,
    val description: String? = null,
    /** URL where the policy document can be retrieved (spURI qualifier). */
    val uri: String? = null,
    val userNotice: String? = null,
    /** Signature policy document to embed in the signature policy store (sigPSt / SignaturePolicyStore). */
    val document: ByteArray? = null,
) {
    companion object {
        suspend fun forDocument(
            id: String,
            document: ByteArray,
            digestAlgorithm: AdesDigestAlgorithm = AdesDigestAlgorithm.SHA256,
            uri: String? = null,
            description: String? = null,
            embedDocument: Boolean = false,
        ) = SignaturePolicyIdentifier(id, digestAlgorithm, digestAlgorithm.digest(document), description, uri, null, document.takeIf { embedDocument })
    }
}
