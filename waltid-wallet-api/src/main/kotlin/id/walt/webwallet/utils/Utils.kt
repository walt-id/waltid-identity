package id.walt.webwallet.utils

import java.util.*
import java.util.zip.GZIPInputStream

object Base64Utils {
    fun decode(base64: String): ByteArray = Base64.getDecoder().decode(base64)
}

object GzipUtils {
    fun uncompress(data: ByteArray, idx: ULong? = null, bitSize: Int) =
        GZIPInputStream(data.inputStream()).bufferedReader().use {
            idx?.let { index ->
                var int = it.read()
                var count = 0UL
                var char = int.toChar()
                while (int != -1 && count <= index) {
                    char = int.toChar()
                    int = it.read()
                    count += bitSize.toULong()
                }
                char
            }?.let {
                val array = CharArray(1)
                array[0] = it
                array
            } ?: it.readText().toCharArray()
        }
}