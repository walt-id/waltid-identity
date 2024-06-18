package id.walt.crypto

import kotlinx.cinterop.*
import platform.CoreFoundation.*
import platform.Foundation.*
import platform.Security.*

internal inline fun <T> withSecKey(
    kid: String, type: CFStringRef?, keyClass: CFStringRef?, block: MemScope.(SecKeyRef?) -> T
) = cfRetain(kid.toNSData()) { kidCf ->
    operation(query(kidCf, type, keyClass)) { query ->
        val secKeyRef = alloc<CFTypeRefVar>()
        val status = SecItemCopyMatching(query, secKeyRef.ptr)

        check(status == 0) {
            val msg = CFBridgingRelease(SecCopyErrorMessageString(status, null)) as NSString
            msg.toString()
        }
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

            val err: CFErrorRefVar = alloc<CFErrorRefVar>()

            val signed = SecKeyCreateSignature(
                secKey, algorithm, dataToSign as CFDataRef, err.ptr
            )

            check(err.value == null) {
                val err = CFBridgingRelease(err.value) as NSError
                println(err)
                err.toString()
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
        val err: CFErrorRefVar = alloc<CFErrorRefVar>()

        val result = SecKeyVerifySignature(
            publicKey, signingAlgorithm, signedData as CFDataRef?, signature as CFDataRef?, err.ptr
        )

        check(err.value == null) {
            val nsError = CFBridgingRelease(err.value) as NSError
            println(nsError)
            nsError.toString()
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