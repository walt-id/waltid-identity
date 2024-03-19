package id.walt.crypto.utils

import org.khronos.webgl.Uint8Array
import org.khronos.webgl.get

@JsExport
object ArrayUtils {

    fun Uint8Array.toByteArray(): ByteArray {

        val byteArray = ByteArray(length)

        repeat(length) {
            byteArray[it] = this[it]
        }

        return byteArray
    }

}
