package id.walt.mdoc.objects.mso

import id.walt.mdoc.encoding.TransformingSerializerTemplate
import id.walt.mdoc.objects.digest.ValueDigestList
import io.ktor.http.*
import kotlinx.serialization.Contextual
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlin.jvm.JvmInline

/**
 * Represents the Mobile Security Object (MSO), the cryptographic root of trust for an mdoc.
 *
 * The MSO is a CBOR object that is digitally signed by the Issuing Authority. It contains the essential
 * security information for an mdoc, including:
 * - Digests (hashes) of all issuer-signed data elements to ensure their integrity.
 * - The public key of the mdoc's device for holder proof-of-possession.
 * - Authorizations for what the device key is allowed to sign.
 * - Validity information (issuance and expiration dates).
 * - Optional revocation status information.
 *
 * An mdoc reader must successfully verify the signature on the MSO to trust any of the credential data.
 *
 * @see ISO/IEC 18013-5:xxxx(E), 9.1.2.4 (Signing method and structure for MSO)
 *
 * @property version The version of the MSO structure (e.g., "1.0").
 * @property digestAlgorithm The algorithm used to create the digests in `valueDigests` (e.g., "SHA-256").
 * @property valueDigests A map of namespaces to a list of digests. This is the core of data integrity verification.
 * @property deviceKeyInfo Contains the device's public key and its authorizations.
 * @property docType The document type identifier this MSO is valid for (e.g., "org.iso.18013.5.1.mDL").
 * @property validityInfo Timestamps defining the MSO's validity period.
 * @property status Optional information for checking the revocation status of the mdoc.
 */
@Serializable
data class MobileSecurityObject(
    @SerialName("version")
    val version: String,

    @SerialName("digestAlgorithm")
    val digestAlgorithm: String,

    @SerialName("valueDigests")
    val valueDigests: Map<String, @Contextual ValueDigestList>,

    @SerialName("deviceKeyInfo")
    val deviceKeyInfo: DeviceKeyInfo,

    @SerialName("docType")
    val docType: String,

    @SerialName("validityInfo")
    val validityInfo: ValidityInfo,

    @SerialName("status")
    val status: Status? = null
) {
    fun precheck() {
        validityInfo.precheck()
    }
}

/**
 * A container for credential status information.
 *
 * Supports two shapes:
 * - **ISO/IEC 18013-5 §9.1.2.6**: `identifier_list` and/or `status_list`.
 * - **ETSI TS 119 472-1 §5.2.10.1 (EAA-5.2.10.1-03..11)**: a flat object with `type`, `purpose`,
 *   `index`, `uri` members, used by ETSI ISO-mdoc QEAA/PuB-EAA. All four are optional here so the
 *   same type can express both shapes; ETSI EAA-5.2.10.1-12 permits additional members.
 */
@Serializable
data class Status(
    @SerialName("identifier_list")
    val identifierList: IdentifierListInfo? = null,
    @SerialName("status_list")
    val statusList: StatusListInfo? = null,
    // ETSI TS 119 472-1 §5.2.10.1 flat status members (for ETSI ISO-mdoc QEAA/PuB-EAA).
    @SerialName("type")
    val type: String? = null,
    @SerialName("purpose")
    val purpose: String? = null,
    @SerialName("index")
    val index: Int? = null,
    @SerialName("uri")
    val uri: UniformResourceIdentifier? = null
) {
    /**
     * Identifier list revocation mechanism per ISO 18013-5 §9.1.2.6.
     * IdentifierListInfo = { "id": Identifier, "uri": URI, ? "certificate": Certificate }
     * where `Identifier = bstr` and `Certificate = bstr` (a DER-encoded X.509 certificate).
     */
    @Serializable
    data class IdentifierListInfo(
        /**
         * Issuer-defined identifier of this MSO within the revocation (identifier) list.
         *
         * Per ISO/IEC 18013-5 §9.1.2.6, `Identifier = bstr` — a CBOR byte string. Issuers that
         * encode `id` as a CBOR integer or text string are NOT conformant to ISO/IEC 18013-5.
         */
        @SerialName("id")
        val id: ByteArray,
        /** URI where the identifier list (status list token) can be fetched. */
        @SerialName("uri")
        val uri: UniformResourceIdentifier,
        /**
         * Optional certificate (DER-encoded X.509, a CBOR `bstr`) whose public key signed the
         * top-level certificate in the `x5chain` of the MSO revocation list (ISO 18013-5 §9.1.2.6).
         */
        @SerialName("certificate")
        val certificate: ByteArray? = null,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is IdentifierListInfo) return false
            if (!id.contentEquals(other.id)) return false
            if (uri != other.uri) return false
            if (certificate != null) {
                if (other.certificate == null) return false
                if (!certificate.contentEquals(other.certificate)) return false
            } else if (other.certificate != null) return false
            return true
        }

        override fun hashCode(): Int {
            var result = id.contentHashCode()
            result = 31 * result + uri.hashCode()
            result = 31 * result + (certificate?.contentHashCode() ?: 0)
            return result
        }
    }

    /**
     * Specifies the location of a Status List Token and the specific index within that list.
     */
    @Serializable
    data class StatusListInfo(
        /**
         * JOSE:
         * idx: REQUIRED. The idx (index) claim MUST specify an Integer that represents the index to
         * check for status information in the Status List for the current Referenced Token. The value
         * of idx MUST be a non-negative number, containing a value of zero or greater.
         *
         * COSE:
         * idx: REQUIRED.
         * Unsigned integer (Major Type 0) The idx (index) claim MUST specify an Integer that represents
         * the index to check for status information in the Status List for the current Referenced
         * Token. The value of idx MUST be a non-negative number, containing a value of zero or greater.
         */
        @SerialName("idx")
        val index: ULong,
        /**
         * JOSE:
         * uri: REQUIRED. The uri (URI) claim MUST specify a String value that identifies the Status
         * List or Status List Token containing the status information for the Referenced Token. The
         * value of uri MUST be a URI conforming to RFC3986.
         *
         * COSE:
         * uri: REQUIRED. Text string (Major Type 3). The uri (URI) claim MUST specify a String value
         * that identifies the Status List or Status List Token containing the status information for
         * the Referenced Token. The value of uri MUST be a URI conforming to RFC3986.
         */
        @SerialName("uri")
        val uri: UniformResourceIdentifier,
    )
}

/**
 * A type-safe wrapper for a Uniform Resource Identifier (URI), ensuring compliance with RFC 3986.
 * It uses a `JvmInline` value class for performance, avoiding runtime object allocation.
 *
 * @see RFC 3986 (Uniform Resource Identifier (URI): Generic Syntax)
 *
 * @property url The underlying Ktor `Url` object.
 */
@Serializable
@JvmInline
value class UniformResourceIdentifier(
    @Serializable(with = KtorUrlSerializer::class)
    private val url: Url
) {
    constructor(string: String) : this(Url(string))

    val string: String
        get() = url.toString()

    /**
     * A custom serializer to convert Ktor's `Url` object to and from a plain `String` for serialization,
     * while preserving the original custom serializer pattern.
     */
    object KtorUrlSerializer : KSerializer<Url> by TransformingSerializerTemplate(
        parent = String.serializer(),
        encodeAs = {
            it.toString()
        },
        decodeAs = {
            Url(it)
        }
    )
}

