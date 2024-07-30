package id.walt.target.ios.keys

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.usePinned
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDataRef
import platform.Foundation.NSData
import platform.Foundation.create
import platform.Foundation.dataWithBytes
import platform.posix.memcpy

fun String.toNSData(): NSData = memScoped {
    NSData.dataWithBytes(this@toNSData.cstr.ptr, this@toNSData.length.toULong())
}

inline fun ByteArray.toNSData() = memScoped {
    NSData.create(
        bytes = allocArrayOf(this@toNSData), length = this@toNSData.size.toULong()
    )
}

inline fun NSData.toByteArray() = memScoped {
    ByteArray(length.toInt()).apply {
        usePinned {
            memcpy(it.addressOf(0), bytes, length)
        }
    }
}

internal inline fun CFDataRef?.toNSData() = NSData.create(
    bytes = CFDataGetBytePtr(this), length = CFDataGetLength(this).toULong()
)