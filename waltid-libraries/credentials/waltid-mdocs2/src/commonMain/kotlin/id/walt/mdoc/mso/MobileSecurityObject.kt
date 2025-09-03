@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.mdoc.mso

import id.walt.cose.CoseKey
import id.walt.mdoc.credsdata.AuthorizedDataElements
import id.walt.mdoc.credsdata.Digests
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents the Mobile Security Object (MSO) as defined in ISO/IEC 18013-5, Section 9.1.2.4.
 * The MSO is the payload of the `issuerAuth` COSE_Sign1 structure and is fundamental for verifying
 * the authenticity of issuer-signed data elements.
 *
 * @property version The version of the MSO structure.
 * @property digestAlgorithm The algorithm used to calculate the digests of the data elements.
 * @property valueDigests A map of namespaces to digests of data elements within that namespace.
 * @property deviceKeyInfo Information about the device key used for mdoc authentication.
 * @property docType The document type identifier.
 * @property validityInfo Information about the validity period of the MSO.
 */
@Serializable
data class MobileSecurityObject(
    val version: String,
    val digestAlgorithm: String,
    val valueDigests: Map<String, Digests>,
    val deviceKeyInfo: DeviceKeyInfo,
    val docType: String,
    val validityInfo: ValidityInfo,
)

/**
 * Contains information about the device key used for mdoc authentication.
 *
 * @property deviceKey The public key of the mdoc authentication key.
 * @property keyAuthorizations Optional authorizations for the key, specifying which data elements it can sign.
 * @property keyInfo Optional additional information about the key.
 */
@Serializable
data class DeviceKeyInfo(
    val deviceKey: CoseKey,
    val keyAuthorizations: KeyAuthorizations? = null,
    val keyInfo: Map<Int, ByteArray>? = null,
)

/**
 * Specifies the authorizations for a device key.
 *
 * @property nameSpaces Namespaces for which the key is fully authorized.
 * @property dataElements Specific data elements, grouped by namespace, for which the key is authorized.
 */
@Serializable
data class KeyAuthorizations(
    val nameSpaces: List<String>? = null,
    val dataElements: Map<String, AuthorizedDataElements>? = null,
)

/**
 * Contains information about the validity of the MSO.
 *
 * @property signed The timestamp when the MSO was signed.
 * @property validFrom The timestamp from which the MSO is valid.
 * @property validUntil The timestamp until which the MSO is valid.
 * @property expectedUpdate An optional timestamp indicating the expected next update.
 */
@Serializable
data class ValidityInfo(
    @SerialName("signed") val signed: String, // tdate
    @SerialName("validFrom") val validFrom: String, // tdate
    @SerialName("validUntil") val validUntil: String, // tdate
    @SerialName("expectedUpdate") val expectedUpdate: String? = null, // tdate
)
