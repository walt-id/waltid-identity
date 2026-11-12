package id.walt.ades.asn1

import kotlin.time.Instant

/** DER encoder helpers. All functions return complete TLV encodings. */
object DerWriter {
    fun tlv(identifier: Int, content: ByteArray): ByteArray = byteArrayOf(identifier.toByte()) + length(content.size) + content

    fun length(len: Int): ByteArray {
        if (len < 0x80) return byteArrayOf(len.toByte())
        val bytes = ArrayList<Byte>()
        var l = len
        while (l > 0) {
            bytes.add(0, (l and 0xff).toByte()); l = l ushr 8
        }
        return byteArrayOf((0x80 or bytes.size).toByte()) + bytes.toByteArray()
    }

    fun sequence(vararg elements: ByteArray): ByteArray = tlv(0x30, concat(elements.toList()))
    fun sequence(elements: List<ByteArray>): ByteArray = tlv(0x30, concat(elements))

    /** DER SET OF: elements are sorted by their encodings as required by X.690 11.6. */
    fun setOf(elements: List<ByteArray>): ByteArray = tlv(0x31, concat(elements.sortedWith(::compareUnsigned)))
    fun set(vararg elements: ByteArray): ByteArray = setOf(elements.toList())

    fun oid(oid: String): ByteArray = tlv(0x06, Der.encodeOidContent(oid))
    fun nullValue(): ByteArray = byteArrayOf(0x05, 0x00)
    fun boolean(value: Boolean): ByteArray = tlv(0x01, byteArrayOf(if (value) 0xff.toByte() else 0))
    fun octetString(value: ByteArray): ByteArray = tlv(0x04, value)
    fun bitString(value: ByteArray, unusedBits: Int = 0): ByteArray = tlv(0x03, byteArrayOf(unusedBits.toByte()) + value)
    fun utf8String(value: String): ByteArray = tlv(0x0c, value.encodeToByteArray())
    fun ia5String(value: String): ByteArray = tlv(0x16, value.encodeToByteArray())
    fun printableString(value: String): ByteArray = tlv(0x13, value.encodeToByteArray())

    /** INTEGER from a non-negative magnitude (big-endian, unsigned). */
    fun unsignedInteger(magnitude: ByteArray): ByteArray {
        var i = 0
        while (i < magnitude.size - 1 && magnitude[i] == 0.toByte()) i++
        var m = magnitude.copyOfRange(i, magnitude.size)
        if (m.isEmpty()) m = byteArrayOf(0)
        if (m[0].toInt() and 0x80 != 0) m = byteArrayOf(0) + m
        return tlv(0x02, m)
    }

    /** INTEGER from raw two's-complement content (as read via [DerElement.asIntegerBytes]). */
    fun integerContent(content: ByteArray): ByteArray = tlv(0x02, content)

    fun integer(value: Long): ByteArray {
        val bytes = ArrayList<Byte>()
        var v = value
        do {
            bytes.add(0, (v and 0xff).toByte()); v = v shr 8
        } while (!((v == 0L && bytes[0] >= 0) || (v == -1L && bytes[0] < 0)))
        return tlv(0x02, bytes.toByteArray())
    }

    fun generalizedTime(instant: Instant): ByteArray {
        val iso = instant.toString() // 2026-10-27T10:00:00Z or with fraction
        val main = iso.substringBefore('.').removeSuffix("Z").replace("-", "").replace(":", "").replace("T", "")
        return tlv(0x18, (main + "Z").encodeToByteArray())
    }

    fun explicit(tag: Int, inner: ByteArray): ByteArray = tlv(0xa0 or tag, inner)
    fun implicitPrimitive(tag: Int, content: ByteArray): ByteArray = tlv(0x80 or tag, content)
    fun implicitConstructed(tag: Int, content: ByteArray): ByteArray = tlv(0xa0 or tag, content)

    /** Replaces the identifier octet of a complete TLV (e.g. IMPLICIT re-tagging, or [0] -> SET for signedAttrs). */
    fun retag(encoded: ByteArray, identifier: Int): ByteArray = encoded.copyOf().also { it[0] = identifier.toByte() }

    fun algorithmIdentifier(oid: String, parameters: ByteArray? = null): ByteArray =
        if (parameters == null) sequence(oid(oid)) else sequence(oid(oid), parameters)

    fun concat(parts: List<ByteArray>): ByteArray {
        val out = ByteArray(parts.sumOf { it.size })
        var pos = 0
        for (p in parts) {
            p.copyInto(out, pos); pos += p.size
        }
        return out
    }

    private fun compareUnsigned(a: ByteArray, b: ByteArray): Int {
        for (i in 0 until minOf(a.size, b.size)) {
            val c = (a[i].toInt() and 0xff) - (b[i].toInt() and 0xff)
            if (c != 0) return c
        }
        return a.size - b.size
    }
}
