package id.walt.crypto2.hash

import okio.ByteString
import okio.ByteString.Companion.toByteString

interface Hasher {

    val algorithm: HashAlgorithm

    fun hash(message: ByteArray): ByteArray

    fun hash(message: ByteString): ByteString = hash(message.toByteArray()).toByteString()

    suspend fun hashAsync(message: ByteArray): ByteArray = hash(message)

    suspend fun hashAsync(message: ByteString): ByteString = hashAsync(message.toByteArray()).toByteString()

}

