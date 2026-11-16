package id.walt.statuslist.compression

expect object ZlibCompressor {
    fun compress(data: ByteArray): ByteArray
    fun decompress(data: ByteArray): ByteArray
    /** Abort before writing beyond the consumer's decompressed-size limit. */
    fun decompress(data: ByteArray, maximumOutputBytes: Int): ByteArray
}
