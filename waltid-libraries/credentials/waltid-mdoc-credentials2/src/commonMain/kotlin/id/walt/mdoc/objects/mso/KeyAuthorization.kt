package id.walt.mdoc.objects.mso

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Defines the authorizations for the mdoc's device key, restricting which data elements it is
 * permitted to sign or MAC. This is a critical security feature within the Mobile Security Object (MSO)
 * to prevent a compromised device key from attesting to unauthorized data.
 *
 * A verifier must check that any data received in the `DeviceSigned` structure is authorized here.
 *
 * @see ISO/IEC 18013-5:xxxx(E), 9.1.2.4 (MobileSecurityObject CDDL)
 *
 * @property namespaces An optional list of namespace identifiers. If a namespace is listed here,
 * the device key is authorized to sign or MAC **any and all** data elements within that namespace.
 * @property dataElements An optional map providing more granular control. The key is a namespace
 * identifier, and the value is a list of specific `DataElementIdentifier`s within that namespace
 * that the device key is authorized to sign or MAC.
 *
 * @note Per the specification, if a namespace is granted full authorization in the `namespaces` list,
 * it MUST NOT also be present as a key in the `dataElements` map.
 */
@Serializable
data class KeyAuthorization(
    @SerialName("nameSpaces")
    val namespaces: List<String>? = null,

    @SerialName("dataElements")
    val dataElements: Map<String, List<String>>? = null
)
