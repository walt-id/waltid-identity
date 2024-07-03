package id.walt.target.ios.keys

import kotlinx.cinterop.MemScope
import kotlinx.cinterop.alloc
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.NSString
import platform.Security.SecCopyErrorMessageString
import platform.Security.SecItemCopyMatching
import platform.Security.SecKeyCopyPublicKey
import platform.Security.SecKeyRef
import platform.Security.errSecSuccess
import platform.Security.kSecAttrApplicationTag
import platform.Security.kSecAttrKeyClass
import platform.Security.kSecAttrKeyType
import platform.Security.kSecClass
import platform.Security.kSecClassKey
import platform.Security.kSecReturnRef
import platform.darwin.OSStatus

internal inline fun <T> withSecKey(
    kid: String, type: CFStringRef?, keyClass: CFStringRef?, block: MemScope.(SecKeyRef?) -> T
) = cfRetain(kid.toNSData()) { kidCf ->
    operation(query(kidCf, type, keyClass)) { query ->
        val secKeyRef = alloc<CFTypeRefVar>()
        checkReturnStatus { SecItemCopyMatching(query, secKeyRef.ptr) }
        val secKey = secKeyRef.value as SecKeyRef?
        try {
            block(secKey)
        } finally {
            CFBridgingRelease(secKey)
        }
    }
}

internal inline fun <T> MemScope.operation(
    query: CFDictionaryRef?, block: MemScope.(CFDictionaryRef?) -> T
): T {
    return try {
        block(query)
    } finally {
        CFBridgingRelease(query)
    }
}

internal fun query(
    kidCF: CFTypeRef?, keyType: CFStringRef?, keyClass: CFStringRef?
) = CFDictionaryCreateMutable(
    kCFAllocatorDefault, 5, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr
).apply {
    CFDictionaryAddValue(this, kSecAttrApplicationTag, kidCF)
    CFDictionaryAddValue(this, kSecAttrKeyType, keyType)
    CFDictionaryAddValue(this, kSecClass, kSecClassKey)
    keyClass?.let {
        CFDictionaryAddValue(this, kSecAttrKeyClass, it)
    }
    CFDictionaryAddValue(this, kSecReturnRef, kCFBooleanTrue)
}

internal inline fun <T> MemScope.usePublicKey(
    privateKey: SecKeyRef?, block: MemScope.(SecKeyRef?) -> T
): T {
    val publicKey = SecKeyCopyPublicKey(privateKey)

    return try {
        block(publicKey)
    } finally {
        CFBridgingRelease(publicKey)
    }
}

internal fun checkReturnStatus(block: () -> OSStatus) {
    block().let { retStatus ->
        check(retStatus == errSecSuccess) {
            val msg = CFBridgingRelease(SecCopyErrorMessageString(retStatus, null)) as NSString
            msg.toString()
        }
    }
}