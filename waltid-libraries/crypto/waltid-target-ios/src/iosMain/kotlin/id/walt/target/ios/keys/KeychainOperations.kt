package id.walt.target.ios.keys

import kotlinx.cinterop.MemScope
import kotlinx.cinterop.ptr
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSNumber
import platform.Security.SecItemDelete
import platform.Security.SecKeyAlgorithm
import platform.Security.SecKeyCopyExternalRepresentation
import platform.Security.SecKeyCreateRandomKey
import platform.Security.SecKeyCreateSignature
import platform.Security.SecKeyCreateWithData
import platform.Security.SecKeyRef
import platform.Security.SecKeyVerifySignature
import platform.Security.kSecAttrApplicationLabel
import platform.Security.kSecAttrApplicationTag
import platform.Security.kSecAttrIsPermanent
import platform.Security.kSecAttrKeyClass
import platform.Security.kSecAttrKeyClassPrivate
import platform.Security.kSecAttrKeyClassPublic
import platform.Security.kSecAttrKeySizeInBits
import platform.Security.kSecAttrKeyType
import platform.Security.kSecAttrKeyTypeECSECPrimeRandom
import platform.Security.kSecAttrKeyTypeRSA
import platform.Security.kSecKeyAlgorithmECDSASignatureMessageX962SHA256
import platform.Security.kSecKeyAlgorithmRSASignatureDigestPKCS1v15SHA256
import platform.Security.kSecPrivateKeyAttrs

internal object KeychainOperations {



    private fun deleteFromKeychain(kid: String, keyType: CFStringRef?) {
        cfRetain(kid.toNSData()) { kidCF ->
            operation(
                query(
                    kidCF, keyType, null
                )
            ) { query ->
                SecItemDelete(query)
            }
        }
    }

    private fun loadFromKeychain(kid: String, keyType: CFStringRef?): String {
        return withSecKey(
            kid, keyType, null
        ) {
            kid
        }
    }

    private fun signRaw(
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

    private fun MemScope.verify(
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

    internal fun keyExternalRepresentation(
        secKey: SecKeyRef?
    ): ByteArray {
        val nsData = CFBridgingRelease(
            SecKeyCopyExternalRepresentation(
                secKey, null
            )
        ) as NSData
        return nsData.toByteArray()
    }

    private fun <T> createSecKeyWithData(
        externalRepresentation: ByteArray, keyClass: CFStringRef?, block: MemScope.(SecKeyRef?) -> T
    ): T {
        return cfRetain(
            NSNumber(int = 256), externalRepresentation.toNSData()
        ) { sizeCf, externalCf ->

            var attributes: CFMutableDictionaryRef? = null
            var secKey: SecKeyRef? = null

            try {
                attributes = CFDictionaryCreateMutable(
                    kCFAllocatorDefault,
                    3,
                    kCFTypeDictionaryKeyCallBacks.ptr,
                    kCFTypeDictionaryValueCallBacks.ptr
                ).apply {
                    CFDictionaryAddValue(
                        this, kSecAttrKeyType, kSecAttrKeyTypeECSECPrimeRandom
                    )
                    CFDictionaryAddValue(this, kSecAttrKeySizeInBits, sizeCf)
                    CFDictionaryAddValue(this, kSecAttrKeyClass, keyClass)
                }

                secKey = checkErrorResult { error ->
                    SecKeyCreateWithData(
                        externalCf as CFDataRef, attributes, error
                    )
                }

                block(secKey)
            } finally {
                attributes?.let { CFBridgingRelease(it) }
                secKey?.let { CFBridgingRelease(it) }
            }
        }
    }

    object RSA {
        internal fun create(kid: String, size: UInt): String {
            check(size in 1024u..7680u step 256) {
                "The key size you provided is not supported."
            }

            return cfRetain(kid.toNSData(), appId.toNSData()) { kidCf, appLabelCf ->
                var generateKeyParams: CFMutableDictionaryRef? = null
                val nsSize = CFBridgingRetain(NSNumber(size))
                try {
                    generateKeyParams = CFDictionaryCreateMutable(
                        kCFAllocatorDefault,
                        5,
                        kCFTypeDictionaryKeyCallBacks.ptr,
                        kCFTypeDictionaryValueCallBacks.ptr
                    ).apply {
                        CFDictionaryAddValue(this, kSecAttrKeyType, kSecAttrKeyTypeRSA)
                        CFDictionaryAddValue(this, kSecAttrApplicationTag, kidCf)
                        CFDictionaryAddValue(this, kSecAttrApplicationLabel, appLabelCf)
                        CFDictionaryAddValue(this, kSecAttrKeySizeInBits, nsSize)
                        CFDictionaryAddValue(this, kSecAttrIsPermanent, kCFBooleanTrue)
                    }

                    val privateKey = checkErrorResult { error ->  SecKeyCreateRandomKey(
                        generateKeyParams, error
                    )}

                    CFBridgingRelease(privateKey)

                    kid

                } finally {
                    generateKeyParams?.let { CFBridgingRelease(it) }
                    CFBridgingRelease(nsSize)
                }
            }
        }

        internal fun load(kid: String) = loadFromKeychain(kid, kSecAttrKeyTypeRSA)

        internal fun delete(kid: String) = deleteFromKeychain(kid, kSecAttrKeyTypeRSA)

        internal inline fun <T> withPrivateKey(kid: String, block: MemScope.(SecKeyRef?) -> T) =
            withSecKey(kid, kSecAttrKeyTypeRSA, kSecAttrKeyClassPrivate) {
                block(it)
            }


        internal inline fun <T> withPublicKey(kid: String, block: MemScope.(SecKeyRef?) -> T) =
            withSecKey(kid, kSecAttrKeyTypeRSA, kSecAttrKeyClassPublic) {
                block(it)
            }

        internal fun signRaw(kid: String, plainText: ByteArray) = signRaw(
            kid,
            kSecAttrKeyTypeRSA,
            kSecKeyAlgorithmRSASignatureDigestPKCS1v15SHA256,
            plainText
        )

        internal fun verifyRaw(publicKey: SecKeyRef?, signature: ByteArray, signedData: ByteArray) =
            cfRetain(signature.toNSData(), signedData.toNSData()) { signatureCf, signedCf ->
                verify(
                    signatureCf,
                    signedCf,
                    publicKey,
                    kSecKeyAlgorithmRSASignatureDigestPKCS1v15SHA256
                )
                Result.success(signedData)
            }
    }

    object P256 {
        internal fun delete(kid: String) = deleteFromKeychain(kid, kSecAttrKeyTypeECSECPrimeRandom)

        internal fun load(kid: String) = loadFromKeychain(kid, kSecAttrKeyTypeECSECPrimeRandom)

        internal fun <T> createPrivateKeyFrom(
            externalRepresentation: ByteArray,
            block: (SecKeyRef?) -> T
        ) = createSecKeyWithData(externalRepresentation, kSecAttrKeyClassPrivate) { key ->
            block(key)
        }

        internal fun <T> createPublicKeyFrom(
            externalRepresentation: ByteArray,
            block: (SecKeyRef?) -> T
        ) = createSecKeyWithData(externalRepresentation, kSecAttrKeyClassPublic) { key ->
            block(key)
        }


        internal fun create(kid: String): String {
            return cfRetain(kid.toNSData(), appId.toNSData()) { kidCf, appLabelCf ->

                var privateKeyParams: CFMutableDictionaryRef? = null
                var generateKeyPairQuery: CFMutableDictionaryRef? = null
                val size: CFTypeRef? = CFBridgingRetain(NSNumber(int = 256))

                try {
                    privateKeyParams = CFDictionaryCreateMutable(
                        kCFAllocatorDefault,
                        4,
                        kCFTypeDictionaryKeyCallBacks.ptr,
                        kCFTypeDictionaryValueCallBacks.ptr
                    ).apply {
                        CFDictionaryAddValue(this, kSecAttrIsPermanent, kCFBooleanTrue)
                        CFDictionaryAddValue(this, kSecAttrApplicationTag, kidCf)
                        CFDictionaryAddValue(this, kSecAttrApplicationLabel, appLabelCf)
                    }

                    generateKeyPairQuery = CFDictionaryCreateMutable(
                        kCFAllocatorDefault,
                        3,
                        kCFTypeDictionaryKeyCallBacks.ptr,
                        kCFTypeDictionaryValueCallBacks.ptr
                    ).apply {
                        CFDictionaryAddValue(
                            this, kSecAttrKeyType, kSecAttrKeyTypeECSECPrimeRandom
                        )
                        CFDictionaryAddValue(this, kSecAttrKeySizeInBits, size)
                        CFDictionaryAddValue(this, kSecPrivateKeyAttrs, privateKeyParams)
                    }

                    val privateKey = checkErrorResult { error ->
                        SecKeyCreateRandomKey(
                            generateKeyPairQuery, error
                        )
                    }

                    CFBridgingRelease(privateKey)

                    kid
                } finally {
                    privateKeyParams?.let { CFBridgingRelease(it) }
                    generateKeyPairQuery?.let { CFBridgingRelease(it) }
                    CFBridgingRelease(size)
                }
            }
        }

        internal inline fun <T> withPrivateKey(kid: String, block: MemScope.(SecKeyRef?) -> T) =
            withSecKey(kid, kSecAttrKeyTypeECSECPrimeRandom, null) {
                block(it)
            }


        internal inline fun <T> withPublicKey(kid: String, block: MemScope.(SecKeyRef?) -> T) =
            withPrivateKey(kid) { privateKey ->
                usePublicKey(privateKey) { publicKey ->
                    block(publicKey)
                }
            }

        internal fun signRaw(kid: String, plainText: ByteArray) = signRaw(
            kid,
            kSecAttrKeyTypeECSECPrimeRandom,
            kSecKeyAlgorithmECDSASignatureMessageX962SHA256,
            plainText
        )

        internal fun verifyRaw(publicKey: SecKeyRef?, signature: ByteArray, signedData: ByteArray) =
            cfRetain(signature.toNSData(), signedData.toNSData()) { signatureCf, signedCf ->
                verify(
                    signatureCf,
                    signedCf,
                    publicKey,
                    kSecKeyAlgorithmECDSASignatureMessageX962SHA256
                )
                Result.success(signedData)
            }
    }
}
