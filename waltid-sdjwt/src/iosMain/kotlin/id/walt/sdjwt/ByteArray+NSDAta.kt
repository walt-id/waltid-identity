package id.walt.sdjwt

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.pin
import platform.Foundation.NSData
import platform.Foundation.create

@OptIn(ExperimentalForeignApi::class)
internal inline fun ByteArray.toData(offset: Int = 0, length: Int = size - offset): NSData {
    require(offset + length <= size) { "offset + length > size" }
    if (isEmpty()) return NSData()
    val pinned = pin()
    return NSData.create(pinned.addressOf(offset), length.toULong()) { _, _ -> pinned.unpin() }
}