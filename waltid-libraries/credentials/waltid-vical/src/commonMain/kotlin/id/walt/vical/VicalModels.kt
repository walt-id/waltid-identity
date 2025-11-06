@file:OptIn(ExperimentalTime::class, ExperimentalSerializationApi::class)

package id.walt.vical

import id.walt.crypto.keys.jwk.JWKKey
import id.walt.vical.serializers.VicalInstantSerializer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.ByteString
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.serialization.json.JsonObject
/**
 * Represents the payload of a VICAL. This data class is aligned with the CDDL structure
 * defined in ISO/IEC 18013-5, Annex C.1.7.1.
 *
 * It contains metadata about the VICAL and a list of certificates.
 */
@Serializable
data class VicalData(
    @SerialName("version") val version: String = "1.0",
    @SerialName("vicalProvider") val vicalProvider: String,
    @Serializable(with = VicalInstantSerializer::class)
    @SerialName("date") val date: Instant,

    // might exceed Long?
    @SerialName("vicalIssueID") val vicalIssueID: Long? = null,
    @Serializable(with = VicalInstantSerializer::class)
    @SerialName("nextUpdate") val nextUpdate: Instant? = null,
    @SerialName("certificateInfos") val certificateInfos: List<CertificateInfo>,
) {
    suspend fun getAllAllowedIssuers() = certificateInfos.associateWith { it.getKey() }

    override fun toString(): String =
        """
        |--- VICAL ---
        |    Version: ${version}
        |    Provider: ${vicalProvider}
        |    Date: ${date}
        |    Issue id: ${vicalIssueID}
        |    Next update: ${nextUpdate}
        |     
        |    ${certificateInfos.mapIndexed { idx, cert -> cert.toString().prependIndent("    ").drop(4) }.joinToString("\n")}
        |--- End of VICAL ---
        """.trimMargin()
}

/**
 * Represents the information for a single certificate within the VICAL.
 *
 * @property serialNumber The certificate's serial number, represented as a big-endian byte array.
 */
@Serializable
data class CertificateInfo(
    /** DER-encoded X.509 certificate */
    @SerialName("certificate") @ByteString val certificate: ByteArray,

    /** The serial number of the certificate */
    // possibly Long?
    @SerialName("serialNumber") @ByteString val serialNumber: ByteArray,

    /** The Subject Key Identifier of the certificate. */
    @SerialName("ski") @ByteString val ski: ByteArray,

    /** An array of document types for which the certificate can be used as a trust anchor. */
    @SerialName("docType") val docType: List<String>,

    /** An optional array of Uniform Resource Names (URNs) that specify the type of certificate. */
    @SerialName("certificateProfile") val certificateProfile: List<String>? = null,

    /** An optional name of the authority that issued the certificate. */
    @SerialName("issuingAuthority") val issuingAuthority: String? = null,

    /** An optional ISO 3166-1 or ISO 3166-2 code for the issuing authority's country or region. */
    @SerialName("issuingCountry") val issuingCountry: String? = null,

    /** An optional name of the state or province of the issuing authority. */
    @SerialName("stateOrProvinceName") val stateOrProvinceName: String? = null,

    /** An optional DER-encoded Issuer field of the certificate. */
    @SerialName("issuer") @ByteString val issuer: ByteArray? = null,

    /** An optional DER-encoded Subject field of the certificate. */
    @SerialName("subject") @ByteString val subject: ByteArray? = null,

    /** The start date of the certificate's validity. */
    @Serializable(with = VicalInstantSerializer::class)
    @SerialName("notBefore") val notBefore: Instant? = null,

    /** The end date of the certificate's validity. */
    @Serializable(with = VicalInstantSerializer::class)
    @SerialName("notAfter") val notAfter: Instant? = null,
) {
    suspend fun getKey() = JWKKey.importFromDerCertificate(certificate)

    // Auto-generated equals/hashCode are not sufficient for ByteArray properties.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as CertificateInfo

        if (!certificate.contentEquals(other.certificate)) return false
        //if (!serialNumber.contentEquals(other.serialNumber)) return false
        if (!ski.contentEquals(other.ski)) return false
        if (docType != other.docType) return false
        if (certificateProfile != other.certificateProfile) return false
        if (issuingAuthority != other.issuingAuthority) return false
        if (issuingCountry != other.issuingCountry) return false
        if (stateOrProvinceName != other.stateOrProvinceName) return false
        if (issuer != null) {
            if (other.issuer == null) return false
            if (!issuer.contentEquals(other.issuer)) return false
        } else if (other.issuer != null) return false
        if (subject != null) {
            if (other.subject == null) return false
            if (!subject.contentEquals(other.subject)) return false
        } else if (other.subject != null) return false
        if (notBefore != other.notBefore) return false
        if (notAfter != other.notAfter) return false

        return true
    }

    override fun hashCode(): Int {
        var result = certificate.contentHashCode()
        // result = 31 * result + serialNumber.contentHashCode()
        result = 31 * result + ski.contentHashCode()
        result = 31 * result + docType.hashCode()
        result = 31 * result + (certificateProfile?.hashCode() ?: 0)
        result = 31 * result + (issuingAuthority?.hashCode() ?: 0)
        result = 31 * result + (issuingCountry?.hashCode() ?: 0)
        result = 31 * result + (stateOrProvinceName?.hashCode() ?: 0)
        result = 31 * result + (issuer?.contentHashCode() ?: 0)
        result = 31 * result + (subject?.contentHashCode() ?: 0)
        result = 31 * result + (notBefore?.hashCode() ?: 0)
        result = 31 * result + (notAfter?.hashCode() ?: 0)
        return result
    }

    override fun toString() =
        """
            |--- VICAL Certificate Entry ---
            |    X509 Certificate: (hex) ${certificate.toHexString()}
            |    Serial: (hex) ${serialNumber.toHexString()}
            |    SKI: (hex) ${ski.toHexString()} 
            |    Document type: $docType
            |    Certificate profile: $certificateProfile
            |    Issuing authority: $issuingAuthority
            |    Issuing country: $issuingCountry
            |    State or province name: $stateOrProvinceName
            |    Issuer DER cert: (hex) ${issuer?.toHexString()}
            |    Subject DER cert: (hex) ${subject?.toHexString()}
            |    Not before: $notBefore
            |    Not after: $notAfter
            |--- End of VICAL Certificate Entry ---
            """.trimMargin()
}

@Serializable
data class VicalFetchRequest(val vicalUrl: String)

@Serializable
data class VicalFetchResponse(val vicalBase64: String? = null)

@Serializable
data class VicalValidationRequest(val verificationKey: JsonObject, val vicalBase64: String)

@Serializable
data class VicalValidationResponse(val vicalValid: Boolean, val vicalBase64: String? = null)


