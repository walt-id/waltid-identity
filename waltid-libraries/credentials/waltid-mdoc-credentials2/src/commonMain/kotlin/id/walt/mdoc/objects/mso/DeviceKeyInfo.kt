package id.walt.mdoc.objects.mso

import id.walt.cose.CoseKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Contains information about the mdoc's authentication key, which is embedded within the
 * Mobile Security Object (MSO).
 *
 * This structure holds the public key of the device, which is used for mdoc authentication
 * (holder proof of possession). It also defines which data elements that key is permitted to sign,
 * a crucial security feature to limit the key's scope.
 *
 * @see ISO/IEC 18013-5:xxxx(E), 9.1.2.4 (MobileSecurityObject CDDL)
 *
 * @property deviceKey The public key (`COSE_Key`) corresponding to the private key held by the mdoc.
 * This key is used to create the `DeviceAuth` signature or MAC.
 * @property keyAuthorizations An optional structure that restricts what the `deviceKey` is authorized
 * to sign. If present, the mdoc can only provide device-signed attestations for the namespaces
 * or specific data elements listed here. If absent, the key can sign any element.
 * @property keyInfo An optional map for proprietary or future extensions related to the key.
 * Per the specification, keys are positive integers for future use and negative for proprietary use.
 * NOTE: The spec allows values of `any` type, but this implementation uses `String` for simplicity.
 */
@Serializable
data class DeviceKeyInfo(
    @SerialName("deviceKey")
    val deviceKey: CoseKey,

    @SerialName("keyAuthorizations")
    val keyAuthorizations: KeyAuthorization? = null,

    @SerialName("keyInfo")
    val keyInfo: Map<Int, String>? = null
)
