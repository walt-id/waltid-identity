@file:OptIn(ExperimentalSerializationApi::class)
package id.walt.mdoc.objects.engagement

import id.walt.cose.CoseKey
import id.walt.mdoc.encoding.ByteStringWrapper
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.CborElement
import kotlinx.serialization.cbor.CborLabel
import kotlinx.serialization.cbor.CborObjectAsArray
import kotlinx.serialization.cbor.ValueTags

/** Reader-side projection. Other engagement fields remain in the exact submitted transcript bytes. */
@Serializable
data class DeviceEngagement(
    @CborLabel(0) val version: String,
    @CborLabel(1) val security: DeviceEngagementSecurity,
    @CborLabel(5) val originInfos: List<CborElement>? = null,
) {
    init { require(version == "1.0" || version == "1.1") { "Unsupported DeviceEngagement version" } }
}

@Serializable
@CborObjectAsArray
data class DeviceEngagementSecurity(
    val cipherSuite: UInt,
    @ValueTags(24u) val eDeviceKey: ByteStringWrapper<CoseKey>,
) {
    init {
        require(cipherSuite == 1u) { "Unsupported DeviceEngagement cipher suite" }
        require(eDeviceKey.value.d == null) { "EDeviceKey must be public" }
    }
}
