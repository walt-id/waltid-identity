package id.walt.crypto

import kotlinx.cinterop.*
import platform.Foundation.*
import platform.CoreFoundation.*

internal inline fun <T> cfRetain(value: Any?, block: MemScope.(CFTypeRef?) -> T): T = memScoped {
    val cfValue = CFBridgingRetain(value)

    return try {
        block(cfValue)
    } finally {
        CFBridgingRelease(cfValue)
    }
}

inline fun <T> cfRetain(
    value1: Any?, value2: Any?, block: MemScope.(CFTypeRef?, CFTypeRef?) -> T
): T = memScoped {
    val cfValue1 = CFBridgingRetain(value1)
    val cfValue2 = CFBridgingRetain(value2)

    return try {
        block(cfValue1, cfValue2)
    } finally {
        CFBridgingRelease(cfValue1)
        CFBridgingRelease(cfValue2)
    }
}

