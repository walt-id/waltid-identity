package id.walt.did.dids.registrar.local.key

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
internal data class IdentifierComponents(
    val multiCodecKeyCode: UInt,
    val pubKeyBytes: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null) return false
        if (this::class != other::class) return false

        other as IdentifierComponents

        if (multiCodecKeyCode != other.multiCodecKeyCode) return false
        if (!pubKeyBytes.contentEquals(other.pubKeyBytes)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = multiCodecKeyCode.hashCode()
        result = 31 * result + pubKeyBytes.contentHashCode()
        return result
    }
}
