package id.walt.mdoc.proximity

internal data class MdocWireMutation(
    val id: String,
    val bytes: ByteArray,
)

internal object MdocWireMutationMatrix {
    fun structuralMutations(canonical: ByteArray): List<MdocWireMutation> {
        require(canonical.size >= 3)
        return listOf(
            MdocWireMutation("truncate-last-byte", canonical.copyOf(canonical.size - 1)),
            MdocWireMutation("append-trailing-item", canonical + byteArrayOf(0x00)),
            MdocWireMutation("indefinite-top-level-map", canonical.copyOf().also { it[0] = 0xbf.toByte() }),
        )
    }

    fun bitFlips(canonical: ByteArray): List<MdocWireMutation> {
        require(canonical.isNotEmpty())
        return listOf(0, canonical.lastIndex / 2, canonical.lastIndex)
            .distinct()
            .map { index ->
                MdocWireMutation(
                    id = "flip-byte-$index",
                    bytes = canonical.copyOf().also { it[index] = (it[index].toInt() xor 0x01).toByte() },
                )
            }
    }
}
