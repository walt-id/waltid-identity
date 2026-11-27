package id.walt.ades.cbades.cbor

import kotlin.math.abs

/**
 * Minimal CBOR data model (RFC 8949) with exact control over encoding, as required for COSE signing inputs and
 * CB-AdES message imprints. Encoding always uses preferred (shortest) definite-length serialization (RFC 9052 9).
 */
sealed interface CborItem {
    data class UInt(val value: Long) : CborItem {
        init {
            require(value >= 0)
        }
    }

    /** Negative integer, [value] < 0. */
    data class NInt(val value: Long) : CborItem {
        init {
            require(value < 0)
        }
    }

    class Bytes(val value: ByteArray) : CborItem {
        override fun equals(other: Any?) = other is Bytes && value.contentEquals(other.value)
        override fun hashCode() = value.contentHashCode()
        override fun toString() = "h'${value.toHexString()}'"
    }

    data class Text(val value: String) : CborItem
    data class Array(val items: List<CborItem>) : CborItem
    /** Map with preserved member order. */
    data class Map(val entries: List<Pair<CborItem, CborItem>>) : CborItem {
        operator fun get(key: CborItem): CborItem? = entries.firstOrNull { it.first == key }?.second
        operator fun get(label: Int): CborItem? = get(int(label.toLong()))
        operator fun get(label: String): CborItem? = get(Text(label))
        val keys: List<CborItem> get() = entries.map { it.first }
        fun hasDuplicateKeys(): Boolean = keys.size != keys.toSet().size
    }

    data class Tag(val tag: Long, val item: CborItem) : CborItem
    data class Simple(val value: Int) : CborItem
    data class Float(val value: Double) : CborItem

    companion object {
        val NULL = Simple(22)
        val TRUE = Simple(21)
        val FALSE = Simple(20)
        fun int(v: Long): CborItem = if (v >= 0) UInt(v) else NInt(v)
        fun int(v: Int): CborItem = int(v.toLong())
    }
}

val CborItem.asLong: Long?
    get() = when (this) {
        is CborItem.UInt -> value
        is CborItem.NInt -> value
        else -> null
    }
val CborItem.asInt: Int? get() = asLong?.toInt()
val CborItem.asBytes: ByteArray? get() = (this as? CborItem.Bytes)?.value
val CborItem.asText: String? get() = (this as? CborItem.Text)?.value
val CborItem.asArray: List<CborItem>? get() = (this as? CborItem.Array)?.items
val CborItem.asMap: CborItem.Map? get() = this as? CborItem.Map
/** Text value, unwrapping tag 32 (URI). */
val CborItem.asUri: String? get() = ((this as? CborItem.Tag)?.takeIf { it.tag == 32L }?.item ?: this).asText

fun cborMap(vararg entries: Pair<Any, CborItem?>): CborItem.Map = CborItem.Map(entries.mapNotNull { (k, v) ->
    v?.let { label(k) to it }
})

fun cborArray(items: List<CborItem>) = CborItem.Array(items)
fun cborArray(vararg items: CborItem) = CborItem.Array(items.toList())
fun ByteArray.cbor() = CborItem.Bytes(this)
fun String.cbor() = CborItem.Text(this)
fun String.cborUri() = CborItem.Tag(32, CborItem.Text(this))
fun Int.cbor() = CborItem.int(this)
fun Long.cbor() = CborItem.int(this)
fun Boolean.cbor() = if (this) CborItem.TRUE else CborItem.FALSE

fun label(k: Any): CborItem = when (k) {
    is Int -> CborItem.int(k)
    is Long -> CborItem.int(k)
    is String -> CborItem.Text(k)
    is CborItem -> k
    else -> error("Unsupported CBOR label $k")
}

class CborException(message: String) : IllegalArgumentException(message)

object Cbor {
    fun encode(item: CborItem): ByteArray = Output().also { it.write(item) }.toByteArray()

    /** Decodes exactly one item spanning the input. */
    fun decode(bytes: ByteArray): CborItem {
        val input = Input(bytes)
        val item = input.read(0)
        if (input.pos != bytes.size) throw CborException("Trailing data after CBOR item (${bytes.size - input.pos} bytes)")
        return item
    }

    /** Checks well-formedness and that only preferred/definite-length encodings are used (RFC 9052 section 9). */
    fun isDeterministicallyEncoded(bytes: ByteArray): Boolean = runCatching { encode(decode(bytes)).contentEquals(bytes) }.getOrDefault(false)

    private class Output {
        private var buf = ByteArray(256)
        private var size = 0

        fun toByteArray() = buf.copyOf(size)

        private fun ensure(n: Int) {
            if (size + n > buf.size) buf = buf.copyOf(maxOf(buf.size * 2, size + n))
        }

        private fun byte(b: Int) {
            ensure(1); buf[size++] = b.toByte()
        }

        private fun bytes(b: ByteArray) {
            ensure(b.size); b.copyInto(buf, size); size += b.size
        }

        private fun head(major: Int, value: Long) {
            val m = major shl 5
            when {
                value < 0 -> error("negative head")
                value < 24 -> byte(m or value.toInt())
                value <= 0xff -> { byte(m or 24); byte(value.toInt()) }
                value <= 0xffff -> { byte(m or 25); byte((value shr 8).toInt()); byte(value.toInt()) }
                value <= 0xffffffffL -> { byte(m or 26); for (s in 24 downTo 0 step 8) byte((value shr s).toInt()) }
                else -> { byte(m or 27); for (s in 56 downTo 0 step 8) byte((value shr s).toInt()) }
            }
        }

        fun write(item: CborItem) {
            when (item) {
                is CborItem.UInt -> head(0, item.value)
                is CborItem.NInt -> head(1, -1 - item.value)
                is CborItem.Bytes -> { head(2, item.value.size.toLong()); bytes(item.value) }
                is CborItem.Text -> item.value.encodeToByteArray().let { head(3, it.size.toLong()); bytes(it) }
                is CborItem.Array -> { head(4, item.items.size.toLong()); item.items.forEach(::write) }
                is CborItem.Map -> { head(5, item.entries.size.toLong()); item.entries.forEach { write(it.first); write(it.second) } }
                is CborItem.Tag -> { head(6, item.tag); write(item.item) }
                is CborItem.Simple -> if (item.value < 24) byte(0xe0 or item.value) else { byte(0xf8); byte(item.value) }
                is CborItem.Float -> {
                    val bits = item.value.toRawBits()
                    byte(0xfb); for (s in 56 downTo 0 step 8) byte((bits shr s).toInt())
                }
            }
        }
    }

    private class Input(val data: ByteArray) {
        var pos = 0

        private fun u8(): Int {
            if (pos >= data.size) throw CborException("Unexpected end of CBOR data")
            return data[pos++].toInt() and 0xff
        }

        private fun argument(info: Int): Long = when {
            info < 24 -> info.toLong()
            info == 24 -> u8().toLong()
            info == 25 -> ((u8() shl 8) or u8()).toLong()
            info == 26 -> (0 until 4).fold(0L) { acc, _ -> (acc shl 8) or u8().toLong() }
            info == 27 -> (0 until 8).fold(0L) { acc, _ -> (acc shl 8) or u8().toLong() }.also { if (it < 0) throw CborException("Integer exceeds 63 bits") }
            info == 31 -> -1L
            else -> throw CborException("Reserved additional information $info")
        }

        private fun take(n: Long): ByteArray {
            if (n < 0 || pos + n > data.size) throw CborException("CBOR length exceeds input")
            return data.copyOfRange(pos, (pos + n).toInt()).also { pos += n.toInt() }
        }

        fun read(depth: Int): CborItem {
            if (depth > 64) throw CborException("CBOR nesting too deep")
            val ib = u8()
            val major = ib shr 5
            val info = ib and 0x1f
            if (major == 7) {
                return when (info) {
                    in 0..23 -> CborItem.Simple(info)
                    24 -> CborItem.Simple(u8())
                    25 -> CborItem.Float(halfToDouble(((u8() shl 8) or u8())))
                    26 -> CborItem.Float(Float.fromBits((0 until 4).fold(0) { acc, _ -> (acc shl 8) or u8() }).toDouble())
                    27 -> CborItem.Float(Double.fromBits((0 until 8).fold(0L) { acc, _ -> (acc shl 8) or u8().toLong() }))
                    else -> throw CborException("Unexpected break/reserved simple value")
                }
            }
            val arg = argument(info)
            val indefinite = arg == -1L
            return when (major) {
                0 -> if (indefinite) throw CborException("Indefinite integer") else CborItem.UInt(arg)
                1 -> if (indefinite) throw CborException("Indefinite integer") else CborItem.NInt(-1 - arg)
                2 -> CborItem.Bytes(if (indefinite) chunks(2) else take(arg))
                3 -> CborItem.Text((if (indefinite) chunks(3) else take(arg)).decodeToString(throwOnInvalidSequence = true))
                4 -> CborItem.Array(if (indefinite) untilBreak { read(depth + 1) } else List(arg.toIntExact()) { read(depth + 1) })
                5 -> CborItem.Map(if (indefinite) untilBreak { read(depth + 1) to read(depth + 1) } else List(arg.toIntExact()) { read(depth + 1) to read(depth + 1) })
                6 -> CborItem.Tag(arg, read(depth + 1))
                else -> throw CborException("Unknown major type $major")
            }
        }

        private fun Long.toIntExact(): Int {
            if (this > data.size) throw CborException("CBOR container length $this exceeds input")
            return toInt()
        }

        private fun chunks(major: Int): ByteArray {
            val parts = mutableListOf<ByteArray>()
            while (true) {
                if (pos < data.size && data[pos] == 0xff.toByte()) { pos++; break }
                val ib = u8()
                if (ib shr 5 != major) throw CborException("Invalid chunk in indefinite string")
                parts += take(argument(ib and 0x1f))
            }
            return parts.fold(ByteArray(0)) { a, b -> a + b }
        }

        private fun <T> untilBreak(next: () -> T): List<T> {
            val out = mutableListOf<T>()
            while (true) {
                if (pos >= data.size) throw CborException("Missing break")
                if (data[pos] == 0xff.toByte()) { pos++; return out }
                out += next()
            }
        }

        private fun halfToDouble(h: Int): Double {
            val exp = (h shr 10) and 0x1f
            val mant = h and 0x3ff
            val v = when (exp) {
                0 -> mant * 5.960464477539063E-8
                31 -> if (mant == 0) Double.POSITIVE_INFINITY else Double.NaN
                else -> (1 + mant / 1024.0) * 2.0.pow(exp - 15)
            }
            return if (h and 0x8000 != 0) -v else v
        }

        private fun Double.pow(e: Int): Double {
            var r = 1.0
            repeat(abs(e)) { r *= this }
            return if (e < 0) 1 / r else r
        }
    }
}

/** CBOR diagnostic-notation-like rendering (for reports and debugging). */
fun CborItem.diagnostic(): String = when (this) {
    is CborItem.UInt -> value.toString()
    is CborItem.NInt -> value.toString()
    is CborItem.Bytes -> if (value.size > 32) "h'${value.copyOf(16).toHexString()}..'(${value.size}B)" else "h'${value.toHexString()}'"
    is CborItem.Text -> "\"$value\""
    is CborItem.Array -> items.joinToString(", ", "[", "]") { it.diagnostic() }
    is CborItem.Map -> entries.joinToString(", ", "{", "}") { "${it.first.diagnostic()}: ${it.second.diagnostic()}" }
    is CborItem.Tag -> "$tag(${item.diagnostic()})"
    is CborItem.Simple -> when (value) { 20 -> "false"; 21 -> "true"; 22 -> "null"; 23 -> "undefined"; else -> "simple($value)" }
    is CborItem.Float -> value.toString()
}
