package id.walt.statuslist.model

data class IndexedStatus(
    val index: Int,
    val value: UInt,
) {
    init {
        require(index >= 0) { "Status index must be non-negative" }
    }

    companion object {
        fun fromHex(index: Int, hex: String): IndexedStatus =
            IndexedStatus(index, hex.removePrefix("0x").toUInt(16))
    }
}
