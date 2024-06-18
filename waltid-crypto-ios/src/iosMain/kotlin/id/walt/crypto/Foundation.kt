package id.walt.crypto

import kotlinx.cinterop.*
import platform.CoreFoundation.*
import platform.Foundation.*
import platform.posix.*

internal fun String.toNSData(): NSData = memScoped {
    NSData.dataWithBytes(this@toNSData.cstr.ptr, this@toNSData.length.toULong())
}

internal inline fun ByteArray.toNSData() = memScoped {
    NSData.create(
        bytes = allocArrayOf(this@toNSData), length = this@toNSData.size.toULong()
    )
}

internal inline fun NSData.toByteArray() = memScoped {
    ByteArray(length.toInt()).apply {
        usePinned {
            memcpy(it.addressOf(0), bytes, length)
        }
    }
}

internal inline fun CFDataRef?.toNSData() = NSData.create(
    bytes = CFDataGetBytePtr(this), length = CFDataGetLength(this).toULong()
)