package id.walt.mdoc.proximity

/** Defensive immutable byte snapshot for transport and consent boundaries. */
class ImmutableBytes private constructor(private val value: ByteArray) {
    val size: Int get() = value.size

    fun copy(): ByteArray = value.copyOf()
    fun contentEquals(other: ByteArray): Boolean = value.contentEquals(other)

    override fun equals(other: Any?): Boolean = other is ImmutableBytes && value.contentEquals(other.value)
    override fun hashCode(): Int = value.contentHashCode()
    override fun toString(): String = "ImmutableBytes(size=$size)"

    companion object {
        fun of(value: ByteArray): ImmutableBytes = ImmutableBytes(value.copyOf())
    }
}
