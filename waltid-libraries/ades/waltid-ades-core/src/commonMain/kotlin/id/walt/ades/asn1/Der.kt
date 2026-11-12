package id.walt.ades.asn1

import kotlin.time.Instant

/**
 * Minimal, allocation-friendly DER element. [encoded] always holds the complete TLV bytes exactly as they appeared
 * in the input, so signed structures (TBSCertificate, signedAttrs, tbsResponseData, ...) can be verified over
 * their original encoding.
 */
class DerElement internal constructor(
    val tagClass: TagClass,
    val constructed: Boolean,
    val tagNumber: Int,
    private val source: ByteArray,
    private val start: Int,
    private val contentStart: Int,
    internal val end: Int,
) {
    enum class TagClass { UNIVERSAL, APPLICATION, CONTEXT, PRIVATE }

    val encoded: ByteArray get() = source.copyOfRange(start, end)
    val content: ByteArray get() = source.copyOfRange(contentStart, end)
    val contentLength: Int get() = end - contentStart

    /** Identifier octet for low tag numbers (all tags used by the PKI structures handled here). */
    val identifier: Int
        get() = (tagClass.ordinal shl 6) or (if (constructed) 0x20 else 0) or tagNumber

    val children: List<DerElement> by lazy {
        require(constructed) { "DER element ${describe()} is primitive" }
        Der.parseAll(source, contentStart, end)
    }

    fun child(index: Int): DerElement =
        children.getOrNull(index) ?: throw DerException("Missing element $index in ${describe()}")

    fun isUniversal(tag: Int) = tagClass == TagClass.UNIVERSAL && tagNumber == tag
    fun isContext(tag: Int) = tagClass == TagClass.CONTEXT && tagNumber == tag

    /** Children with context tag [tag] (useful for OPTIONAL/[n] fields). */
    fun contextChild(tag: Int): DerElement? = children.firstOrNull { it.isContext(tag) }

    /** For an EXPLICIT [n] wrapper, the single inner element. */
    fun explicitInner(): DerElement = children.singleOrNull()
        ?: throw DerException("Explicit tag ${describe()} must wrap exactly one element")

    fun expectSequence(): DerElement = also {
        if (!isUniversal(Der.SEQUENCE)) throw DerException("Expected SEQUENCE, got ${describe()}")
    }

    fun expectSet(): DerElement = also {
        if (!isUniversal(Der.SET)) throw DerException("Expected SET, got ${describe()}")
    }

    fun asOid(): String {
        if (!isUniversal(Der.OID)) throw DerException("Expected OID, got ${describe()}")
        return Der.decodeOid(content)
    }

    /** Two's complement big-endian integer content bytes. */
    fun asIntegerBytes(): ByteArray {
        if (!isUniversal(Der.INTEGER) && !isUniversal(Der.ENUMERATED) && !(tagClass == TagClass.CONTEXT && !constructed)) {
            throw DerException("Expected INTEGER, got ${describe()}")
        }
        return content
    }

    fun asLong(): Long {
        val bytes = asIntegerBytes()
        if (bytes.size > 8) throw DerException("INTEGER too large for Long")
        var value = if (bytes.isNotEmpty() && bytes[0] < 0) -1L else 0L
        for (b in bytes) value = (value shl 8) or (b.toLong() and 0xff)
        return value
    }

    fun asInt(): Int = asLong().toInt()

    fun asBoolean(): Boolean {
        if (!isUniversal(Der.BOOLEAN)) throw DerException("Expected BOOLEAN, got ${describe()}")
        return content.singleOrNull()?.let { it != 0.toByte() } ?: throw DerException("Invalid BOOLEAN")
    }

    fun asOctetString(): ByteArray {
        if (!isUniversal(Der.OCTET_STRING) && !(tagClass == TagClass.CONTEXT && !constructed)) {
            throw DerException("Expected OCTET STRING, got ${describe()}")
        }
        return content
    }

    /** BIT STRING value without the leading unused-bits octet. */
    fun asBitStringBytes(): ByteArray {
        if (!isUniversal(Der.BIT_STRING) && !(tagClass == TagClass.CONTEXT && !constructed)) {
            throw DerException("Expected BIT STRING, got ${describe()}")
        }
        val c = content
        if (c.isEmpty()) throw DerException("Empty BIT STRING")
        return c.copyOfRange(1, c.size)
    }

    /** BIT STRING as a list of booleans (bit 0 = most significant bit of first octet). */
    fun asBitList(): List<Boolean> {
        val c = content
        if (c.isEmpty()) return emptyList()
        val unused = c[0].toInt() and 0xff
        val total = (c.size - 1) * 8 - unused
        return (0 until total).map { bit ->
            val octet = c[1 + bit / 8].toInt() and 0xff
            (octet shr (7 - bit % 8)) and 1 == 1
        }
    }

    fun asString(): String = when {
        tagClass == TagClass.UNIVERSAL && tagNumber == Der.BMP_STRING ->
            buildString { content.toList().chunked(2).forEach { append(((it[0].toInt() and 0xff shl 8) or (it[1].toInt() and 0xff)).toChar()) } }
        tagClass == TagClass.UNIVERSAL && tagNumber == Der.UNIVERSAL_STRING ->
            buildString { content.toList().chunked(4).forEach { b -> appendCodePoint(b.fold(0) { acc, x -> (acc shl 8) or (x.toInt() and 0xff) }) } }
        else -> content.decodeToString()
    }

    fun asTime(): Instant = when {
        isUniversal(Der.UTC_TIME) -> Der.parseUtcTime(content.decodeToString())
        isUniversal(Der.GENERALIZED_TIME) -> Der.parseGeneralizedTime(content.decodeToString())
        else -> throw DerException("Expected time, got ${describe()}")
    }

    fun describe(): String = "[${tagClass.name.lowercase()} ${if (constructed) "cons" else "prim"} $tagNumber len=$contentLength]"

    override fun toString(): String = describe()
}

class DerException(message: String) : IllegalArgumentException(message)

private fun StringBuilder.appendCodePoint(cp: Int) {
    if (cp < 0x10000) append(cp.toChar()) else {
        val v = cp - 0x10000
        append((0xD800 + (v shr 10)).toChar()); append((0xDC00 + (v and 0x3FF)).toChar())
    }
}

object Der {
    const val BOOLEAN = 1
    const val INTEGER = 2
    const val BIT_STRING = 3
    const val OCTET_STRING = 4
    const val NULL = 5
    const val OID = 6
    const val ENUMERATED = 10
    const val UTF8_STRING = 12
    const val SEQUENCE = 16
    const val SET = 17
    const val PRINTABLE_STRING = 19
    const val T61_STRING = 20
    const val IA5_STRING = 22
    const val UTC_TIME = 23
    const val GENERALIZED_TIME = 24
    const val VISIBLE_STRING = 26
    const val UNIVERSAL_STRING = 28
    const val BMP_STRING = 30

    /** Parses exactly one element spanning the complete input. */
    fun parse(bytes: ByteArray): DerElement {
        val all = parseAll(bytes, 0, bytes.size)
        if (all.size != 1) throw DerException("Expected a single DER element, found ${all.size}")
        return all[0]
    }

    /** Parses the first element and ignores trailing data (some producers append padding). */
    fun parseFirst(bytes: ByteArray): DerElement = parseOne(bytes, 0, bytes.size)

    internal fun parseAll(source: ByteArray, from: Int, to: Int): List<DerElement> {
        val out = ArrayList<DerElement>()
        var pos = from
        while (pos < to) {
            val el = parseOne(source, pos, to)
            out += el
            pos = el.end
        }
        return out
    }

    private fun parseOne(source: ByteArray, start: Int, limit: Int): DerElement {
        var pos = start
        if (pos >= limit) throw DerException("Unexpected end of DER data")
        val id = source[pos++].toInt() and 0xff
        val tagClass = DerElement.TagClass.entries[id shr 6]
        val constructed = id and 0x20 != 0
        var tagNumber = id and 0x1f
        if (tagNumber == 0x1f) {
            tagNumber = 0
            do {
                if (pos >= limit) throw DerException("Truncated high tag number")
                val b = source[pos++].toInt() and 0xff
                tagNumber = (tagNumber shl 7) or (b and 0x7f)
            } while (b and 0x80 != 0)
        }
        if (pos >= limit) throw DerException("Missing DER length")
        val first = source[pos++].toInt() and 0xff
        val length: Int = when {
            first < 0x80 -> first
            first == 0x80 -> throw DerException("Indefinite length is not allowed in DER")
            else -> {
                val n = first and 0x7f
                if (n > 4 || pos + n > limit) throw DerException("Unsupported DER length")
                var l = 0
                repeat(n) { l = (l shl 8) or (source[pos++].toInt() and 0xff) }
                if (l < 0) throw DerException("DER length overflow")
                l
            }
        }
        val contentStart = pos
        val end = contentStart + length
        if (end > limit) throw DerException("DER element exceeds available data ($end > $limit)")
        return DerElement(tagClass, constructed, tagNumber, source, start, contentStart, end)
    }

    fun decodeOid(content: ByteArray): String {
        if (content.isEmpty()) throw DerException("Empty OID")
        val parts = ArrayList<Long>()
        var value = 0L
        for (b in content) {
            value = (value shl 7) or (b.toLong() and 0x7f)
            if (b.toInt() and 0x80 == 0) {
                parts += value; value = 0
            }
        }
        val first = parts.first()
        val head = when {
            first < 40 -> listOf(0L, first)
            first < 80 -> listOf(1L, first - 40)
            else -> listOf(2L, first - 80)
        }
        return (head + parts.drop(1)).joinToString(".")
    }

    fun encodeOidContent(oid: String): ByteArray {
        val parts = oid.split('.').map { it.toLong() }
        require(parts.size >= 2) { "Invalid OID $oid" }
        val out = ArrayList<Byte>()
        fun base128(v: Long) {
            val tmp = ArrayList<Byte>()
            var x = v
            tmp += (x and 0x7f).toByte()
            x = x shr 7
            while (x > 0) {
                tmp += ((x and 0x7f) or 0x80).toByte(); x = x shr 7
            }
            out += tmp.reversed()
        }
        base128(parts[0] * 40 + parts[1])
        parts.drop(2).forEach(::base128)
        return out.toByteArray()
    }

    fun parseUtcTime(s: String): Instant {
        // YYMMDDHHMM[SS]Z
        val body = s.removeSuffix("Z")
        val yy = body.substring(0, 2).toInt()
        val year = if (yy >= 50) 1900 + yy else 2000 + yy
        val sec = if (body.length >= 12) body.substring(10, 12) else "00"
        return Instant.parse("$year-${body.substring(2, 4)}-${body.substring(4, 6)}T${body.substring(6, 8)}:${body.substring(8, 10)}:${sec}Z")
    }

    fun parseGeneralizedTime(s: String): Instant {
        // YYYYMMDDHHMMSS[.fff]Z
        val body = s.removeSuffix("Z")
        val main = body.substringBefore('.')
        val frac = body.substringAfter('.', "")
        val sec = if (main.length >= 14) main.substring(12, 14) else "00"
        val iso = "${main.substring(0, 4)}-${main.substring(4, 6)}-${main.substring(6, 8)}T${main.substring(8, 10)}:${main.substring(10, 12)}:$sec" +
            (if (frac.isNotEmpty()) ".$frac" else "") + "Z"
        return Instant.parse(iso)
    }
}
