package id.walt.crypto2.algorithms

object EcdsaSignatureCodec {
    fun p1363ToDer(signature: ByteArray, componentSizeBytes: Int): ByteArray {
        require(componentSizeBytes > 0) { "ECDSA component size must be positive" }
        require(signature.size == componentSizeBytes * 2) {
            "P1363 signature must contain two $componentSizeBytes-byte components"
        }
        val r = encodeInteger(signature.copyOfRange(0, componentSizeBytes))
        val s = encodeInteger(signature.copyOfRange(componentSizeBytes, signature.size))
        val content = r + s
        return byteArrayOf(SEQUENCE_TAG) + encodeLength(content.size) + content
    }

    fun derToP1363(signature: ByteArray, componentSizeBytes: Int): ByteArray {
        require(componentSizeBytes > 0) { "ECDSA component size must be positive" }
        var index = 0
        require(signature.readByte(index++) == SEQUENCE_TAG) { "ECDSA signature must be a DER sequence" }
        val sequenceLength = readLength(signature, index).also { index = it.nextIndex }
        require(sequenceLength.value == signature.size - index) { "ECDSA DER sequence length is invalid" }
        val r = readInteger(signature, index).also { index = it.nextIndex }
        val s = readInteger(signature, index).also { index = it.nextIndex }
        require(index == signature.size) { "ECDSA DER signature contains trailing data" }
        return r.value.toFixedSize(componentSizeBytes) + s.value.toFixedSize(componentSizeBytes)
    }

    private fun encodeInteger(component: ByteArray): ByteArray {
        val firstValueIndex = component.indexOfFirst { it != 0.toByte() }.takeIf { it >= 0 } ?: component.lastIndex
        val unsigned = component.copyOfRange(firstValueIndex, component.size)
        val value = if (unsigned.first().toInt() and 0x80 != 0) byteArrayOf(0) + unsigned else unsigned
        return byteArrayOf(INTEGER_TAG) + encodeLength(value.size) + value
    }

    private fun readInteger(bytes: ByteArray, startIndex: Int): Parsed<ByteArray> {
        var index = startIndex
        require(bytes.readByte(index++) == INTEGER_TAG) { "ECDSA DER signature must contain two integers" }
        val length = readLength(bytes, index).also { index = it.nextIndex }
        require(length.value > 0 && index + length.value <= bytes.size) { "ECDSA DER integer length is invalid" }
        val encoded = bytes.copyOfRange(index, index + length.value)
        require(encoded.first().toInt() and 0x80 == 0) { "ECDSA DER integer cannot be negative" }
        require(encoded.size == 1 || encoded[0] != 0.toByte() || encoded[1].toInt() and 0x80 != 0) {
            "ECDSA DER integer is not minimally encoded"
        }
        val value = if (encoded.size > 1 && encoded[0] == 0.toByte()) encoded.copyOfRange(1, encoded.size) else encoded
        return Parsed(value, index + length.value)
    }

    private fun readLength(bytes: ByteArray, startIndex: Int): Parsed<Int> {
        var index = startIndex
        val first = bytes.readByte(index++).toInt() and 0xff
        if (first < 0x80) return Parsed(first, index)
        val byteCount = first and 0x7f
        require(byteCount in 1..4 && index + byteCount <= bytes.size) { "Invalid DER length" }
        require(bytes[index] != 0.toByte()) { "DER length is not minimally encoded" }
        var value = 0
        repeat(byteCount) { value = (value shl 8) or (bytes[index++].toInt() and 0xff) }
        require(value >= 0x80) { "DER length must use short form" }
        return Parsed(value, index)
    }

    private fun encodeLength(length: Int): ByteArray {
        require(length >= 0)
        if (length < 0x80) return byteArrayOf(length.toByte())
        val bytes = buildList {
            var remaining = length
            while (remaining > 0) {
                add(0, (remaining and 0xff).toByte())
                remaining = remaining ushr 8
            }
        }.toByteArray()
        return byteArrayOf((0x80 or bytes.size).toByte()) + bytes
    }

    private fun ByteArray.toFixedSize(size: Int): ByteArray {
        require(this.size <= size) { "ECDSA signature component exceeds $size bytes" }
        return ByteArray(size).also { copyInto(it, destinationOffset = size - this.size) }
    }

    private fun ByteArray.readByte(index: Int): Byte {
        require(index in indices) { "Unexpected end of ECDSA DER signature" }
        return this[index]
    }

    private data class Parsed<T>(val value: T, val nextIndex: Int)

    private const val SEQUENCE_TAG: Byte = 0x30
    private const val INTEGER_TAG: Byte = 0x02
}
