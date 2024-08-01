package id.walt.crypto.utils

import id.walt.crypto.utils.Base64Utils.base64Decode
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFErrorRefVar
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.create
import platform.Security.SecKeyCreateSignature
import platform.Security.SecKeyCreateWithData
import platform.Security.kSecAttrKeyClass
import platform.Security.kSecAttrKeyClassPrivate
import platform.Security.kSecAttrKeyType
import platform.Security.kSecAttrKeyTypeRSA
import platform.Security.kSecClass
import platform.Security.kSecClassKey
import platform.Security.kSecKeyAlgorithmRSASignatureDigestPKCS1v15SHA256
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class)
actual fun sha256WithRsa(privateKeyAsPem: String, data: ByteArray): ByteArray {

    memScoped {
        val keyCfData = minimalPem(privateKeyAsPem).base64Decode().let {
            CFBridgingRetain(
                NSData.create(
                    bytes = allocArrayOf(it), length = it.size.toULong()
                )
            )
        }

        val cfData = CFBridgingRetain(
            NSData.create(
                bytes = allocArrayOf(data), length = data.size.toULong()
            )
        )

        val keyCfAttributes = CFDictionaryCreateMutable(
            kCFAllocatorDefault,
            3,
            kCFTypeDictionaryKeyCallBacks.ptr,
            kCFTypeDictionaryValueCallBacks.ptr
        ).apply {
            CFDictionaryAddValue(this, kSecAttrKeyType, kSecAttrKeyTypeRSA)
            CFDictionaryAddValue(this, kSecClass, kSecClassKey)
            CFDictionaryAddValue(this, kSecAttrKeyClass, kSecAttrKeyClassPrivate)
        }
        return try {
            val secKeyRef = checkErrorResult { error ->
                SecKeyCreateWithData(
                    keyCfData as CFDataRef, keyCfAttributes, error
                )
            }

            val signed = checkErrorResult { error ->
                SecKeyCreateSignature(
                    secKeyRef,
                    kSecKeyAlgorithmRSASignatureDigestPKCS1v15SHA256,
                    cfData as CFDataRef,
                    error
                )
            }

            val nsSignature = NSData.create(
                bytes = CFDataGetBytePtr(signed), length = CFDataGetLength(signed).toULong()
            )
            CFBridgingRelease(signed)

            ByteArray(nsSignature.length.toInt()).apply {
                usePinned {
                    memcpy(it.addressOf(0), nsSignature.bytes, nsSignature.length)
                }
            }
        } finally {
            CFBridgingRelease(keyCfData)
            CFBridgingRelease(keyCfAttributes)
            CFBridgingRelease(cfData)
        }

    }
}

@OptIn(ExperimentalForeignApi::class)
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