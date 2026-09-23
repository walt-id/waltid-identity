package id.walt.statuslist.bit

enum class BitOrder {
    LeastSignificantFirst,
    MostSignificantFirst,
    ;

    val bitIndices: IntProgression
        get() = when (this) {
            LeastSignificantFirst -> 0..7
            MostSignificantFirst -> 7 downTo 0
        }
}
