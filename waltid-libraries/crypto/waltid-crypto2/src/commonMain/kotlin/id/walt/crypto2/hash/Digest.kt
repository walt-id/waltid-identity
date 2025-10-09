package id.walt.crypto2.hash

import okio.ByteString
import okio.ByteString.Companion.toByteString

interface Digest {
    val algorithm: HashAlgorithm

    fun update(
        input: ByteArray,
        offset: Int = 0,
        length: Int = input.size,
    )

    fun update(input: ByteString) {
        update(input.toByteArray())
    }

    fun digest(): ByteArray

    fun digestByteString(): ByteString = digest().toByteString()

    suspend fun digestAsync(): ByteArray = digest()

    suspend fun digestAsyncByteString(): ByteString = digestAsync().toByteString()

    fun reset()

}
