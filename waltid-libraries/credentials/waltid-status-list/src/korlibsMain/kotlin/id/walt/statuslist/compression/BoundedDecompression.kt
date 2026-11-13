package id.walt.statuslist.compression

import korlibs.io.compression.CompressionMethod
import korlibs.io.compression.uncompress
import korlibs.io.stream.MemorySyncStreamToByteArray
import korlibs.io.stream.SyncOutputStream
import korlibs.io.stream.openSync

internal fun CompressionMethod.uncompressBounded(data: ByteArray, maxOutputBytes: Int): ByteArray {
    require(maxOutputBytes > 0)
    return MemorySyncStreamToByteArray(minOf(maxOutputBytes, 4096)) {
        val output = this
        var written = 0
        val bounded = object : SyncOutputStream {
            override fun write(buffer: ByteArray, offset: Int, len: Int) {
                if (len > maxOutputBytes - written) throw StatusListSizeLimitException()
                output.write(buffer, offset, len)
                written += len
            }
        }
        this@uncompressBounded.uncompress(data.openSync(), bounded)
    }
}
