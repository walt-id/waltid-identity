@file:OptIn(kotlinx.cinterop.BetaInteropApi::class, kotlinx.cinterop.ExperimentalForeignApi::class)

package id.walt.walletdemo.compose.logic

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.CValuesRef
import kotlinx.cinterop.LongVar
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryGetValue
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFNumberGetValue
import platform.CoreFoundation.CFRelease
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.create
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFNumberLongLongType
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.ImageIO.CGImageSourceCopyPropertiesAtIndex
import platform.ImageIO.CGImageSourceCreateThumbnailAtIndex
import platform.ImageIO.CGImageSourceCreateWithData
import platform.ImageIO.kCGImageSourceCreateThumbnailFromImageAlways
import platform.ImageIO.kCGImageSourceShouldCacheImmediately
import platform.ImageIO.kCGImageSourceThumbnailMaxPixelSize
import platform.ImageIO.kCGImagePropertyPixelHeight
import platform.ImageIO.kCGImagePropertyPixelWidth

@Suppress("UNCHECKED_CAST")
internal actual fun platformCanDecodeImage(bytes: ByteArray, maxPixelCount: Long): Boolean {
    if (bytes.isEmpty()) return false
    val data = bytes.usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
    }
    val retainedData = CFBridgingRetain(data) ?: return false
    return try {
        val source = CGImageSourceCreateWithData(retainedData as CFDataRef, null) ?: return false
        try {
            val properties = CGImageSourceCopyPropertiesAtIndex(source, 0u, null) ?: return false
            try {
                val width = properties.dimension(kCGImagePropertyPixelWidth) ?: return false
                val height = properties.dimension(kCGImagePropertyPixelHeight) ?: return false
                if (width <= 0 || height <= 0 || width > maxPixelCount / height) return false
            } finally {
                CFRelease(properties)
            }

            val options = CFDictionaryCreateMutable(
                kCFAllocatorDefault,
                3,
                kCFTypeDictionaryKeyCallBacks.ptr,
                kCFTypeDictionaryValueCallBacks.ptr,
            )
            val retainedMaxPixelSize = CFBridgingRetain(validationImageSize)
            try {
                CFDictionaryAddValue(options, kCGImageSourceCreateThumbnailFromImageAlways, kCFBooleanTrue)
                CFDictionaryAddValue(options, kCGImageSourceShouldCacheImmediately, kCFBooleanTrue)
                CFDictionaryAddValue(options, kCGImageSourceThumbnailMaxPixelSize, retainedMaxPixelSize)
                val thumbnail = CGImageSourceCreateThumbnailAtIndex(source, 0u, options) ?: return false
                CFRelease(thumbnail)
                true
            } finally {
                CFBridgingRelease(retainedMaxPixelSize)
                CFBridgingRelease(options)
            }
        } finally {
            CFRelease(source)
        }
    } finally {
        CFBridgingRelease(retainedData)
    }
}

private fun CFDictionaryRef.dimension(key: CValuesRef<*>?): Long? = memScoped {
    val number = CFDictionaryGetValue(this@dimension, key) ?: return null
    val value = alloc<LongVar>()
    if (CFNumberGetValue(number.reinterpret(), kCFNumberLongLongType, value.ptr)) value.value else null
}

private const val validationImageSize = 64
