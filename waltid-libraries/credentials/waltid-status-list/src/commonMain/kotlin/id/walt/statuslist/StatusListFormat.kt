package id.walt.statuslist

enum class StatusListFormat {
    TokenStatusList,
    BitstringStatusList,
    StatusList2021,
    RevocationList2020,
}

object StatusListDefaults {
    const val W3C_LIST_SIZE_BYTES: Int = 32 * 1024
    const val IETF_STATUS_LIST_ENTRIES: Int = 1 shl 20
    const val BITS_PER_BYTE: Int = 8

    fun ietfListSizeBytes(statusSize: Int): Int =
        IETF_STATUS_LIST_ENTRIES * statusSize / BITS_PER_BYTE
}
