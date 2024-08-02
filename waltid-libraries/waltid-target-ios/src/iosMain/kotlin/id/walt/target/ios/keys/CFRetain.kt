package id.walt.target.ios.keys

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreFoundation.CFErrorRefVar
import platform.CoreFoundation.CFTypeRef
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSError

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

internal inline fun <T> MemScope.checkErrorResult(block: MemScope.(err: CPointer<CFErrorRefVar>) -> T): T {
    val error = alloc<CFErrorRefVar>()

    return try {
        block(error.ptr)
    } finally {
        check(error.value == null) {
            val nsError = CFBridgingRelease(error.value) as NSError
            println(nsError.toString())
            nsError.toString()
        }
    }
}

