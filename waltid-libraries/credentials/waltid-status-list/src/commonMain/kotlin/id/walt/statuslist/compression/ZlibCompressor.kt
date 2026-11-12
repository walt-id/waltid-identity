package id.walt.statuslist.compression

expect object ZlibCompressor {
    fun compress(data: ByteArray): ByteArray
    fun decompress(data: ByteArray): ByteArray
    fun decompress(data: ByteArray, maxOutputBytes: Int): ByteArray
}
