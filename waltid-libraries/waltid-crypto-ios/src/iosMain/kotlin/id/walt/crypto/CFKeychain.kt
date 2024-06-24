package id.walt.crypto

import kotlinx.cinterop.MemScope
import kotlinx.cinterop.alloc
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDataRef
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
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Security.SecCopyErrorMessageString
import platform.Security.SecItemCopyMatching
import platform.Security.SecKeyAlgorithm
import platform.Security.SecKeyCopyExternalRepresentation
import platform.Security.SecKeyCopyPublicKey
import platform.Security.SecKeyCreateSignature
import platform.Security.SecKeyRef
import platform.Security.SecKeyVerifySignature
import platform.Security.errSecSuccess
import platform.Security.kSecAttrApplicationTag
import platform.Security.kSecAttrKeyClass
import platform.Security.kSecAttrKeyClassPrivate
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

internal interface CoreFoundationSecOperations {
    fun signRaw(
        keyId: String,
        keyType: CFStringRef?,
        algorithm: SecKeyAlgorithm?,
        data: ByteArray,

        ) = withSecKey(keyId, keyType, kSecAttrKeyClassPrivate) { secKey ->
        cfRetain(data.toNSData()) { dataToSign ->
            val signed = checkErrorResult { error ->
                SecKeyCreateSignature(
                    secKey, algorithm, dataToSign as CFDataRef, error
                )
            }
            val signedNsData = signed.toNSData()
            CFBridgingRelease(signed)

            signedNsData.toByteArray()
        }
    }

    fun MemScope.verify(
        signature: CFTypeRef?,
        signedData: CFTypeRef?,
        publicKey: SecKeyRef?,
        signingAlgorithm: SecKeyAlgorithm?
    ): Boolean {
        val result = checkErrorResult { error ->
            SecKeyVerifySignature(
                publicKey,
                signingAlgorithm,
                signedData as CFDataRef?,
                signature as CFDataRef?,
                error
            )
        }

        check(result) {
            "Not verified."
        }

        return result
    }

    fun publicKeyExternalRepresentation(
        keyId: String, keyType: CFStringRef?
    ) = withSecKey(
        keyId, keyType, null
    ) { secKey ->
        usePublicKey(secKey) { publicKey ->
            val nsData = CFBridgingRelease(
                SecKeyCopyExternalRepresentation(
                    publicKey, null
                )
            ) as NSData
            nsData.toByteArray()
        }
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