package id.walt.crypto

import id.walt.crypto.keys.JwkKeyMeta
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyMeta
import id.walt.crypto.keys.KeyType
import id.walt.platform.utils.ios.DS_Operations
import id.walt.platform.utils.ios.ECKeyUtils
import kotlinx.cinterop.ptr
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
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
import platform.Foundation.NSNumber
import platform.Security.SecItemDelete
import platform.Security.SecKeyCreateRandomKey
import platform.Security.kSecAttrApplicationLabel
import platform.Security.kSecAttrApplicationTag
import platform.Security.kSecAttrIsPermanent
import platform.Security.kSecAttrKeySizeInBits
import platform.Security.kSecAttrKeyType
import platform.Security.kSecAttrKeyTypeECSECPrimeRandom
import platform.Security.kSecKeyAlgorithmECDSASignatureMessageX962SHA256
import platform.Security.kSecPrivateKeyAttrs

private val ksecKeyType: CFStringRef? = kSecAttrKeyTypeECSECPrimeRandom

class P256Key private constructor(
    val keyId: String, override val keyType: KeyType = KeyType.secp256r1
) : Key(), CoreFoundationSecOperations {


    override val hasPrivateKey: Boolean
        get() = true


    override suspend fun getKeyId(): String = keyId

    override suspend fun getThumbprint(): String = withSecKey(
        this.keyId, ksecKeyType, null
    ) { secKey ->
        ECKeyUtils.thumbprintWithPublicKey(secKey, null)!!
    }

    override suspend fun exportJWKObject(): JsonObject =
        exportJWK().let { Json.parseToJsonElement(it).jsonObject }

    override suspend fun getPublicKey(): Key = this

    override suspend fun exportJWK(): String = withSecKey(
        this.keyId, ksecKeyType, null
    ) { secKey ->
        usePublicKey(secKey) { publicKey ->
            ECKeyUtils.exportJwkWithPublicKey(publicKey, null)!!
        }
    }

    override suspend fun exportPEM(): String = getPublicKeyRepresentation().let {
        ECKeyUtils.pemWithPublicKeyRepresentation(it.toNSData())
    }

    override suspend fun signRaw(plaintext: ByteArray): ByteArray = signRaw(
        keyId, ksecKeyType, kSecKeyAlgorithmECDSASignatureMessageX962SHA256, plaintext
    )


    private suspend fun signJws(bodyJson: ByteArray, headersJson: ByteArray): String = withSecKey(
        keyId, ksecKeyType, null
    ) { privateKey ->

        val result = DS_Operations.signWithBody(
            bodyJson.toNSData(), "ES256", privateKey, headersJson.toNSData()
        )

        check(result.success()) {
            result.errorMessage()!!
        }

        result.data()!!
    }

    override suspend fun signJws(plaintext: ByteArray, headers: Map<String, String>): String =
        withSecKey(
            keyId, ksecKeyType, null
        ) { privateKey ->
            val result = DS_Operations.signWithBody(
                plaintext.toNSData(), "ES256", privateKey, headers as Map<Any?, *>
            )

            check(result.success()) {
                result.errorMessage()!!
            }

            result.data()!!
        }

    override suspend fun verifyRaw(
        signature: ByteArray, signedData: ByteArray?
    ): Result<ByteArray> = withSecKey(
        keyId, ksecKeyType, null
    ) { secKey ->
        cfRetain(
            signature.toNSData(), signedData!!.toNSData()
        ) { signatureCF, signedDataCF ->
            usePublicKey(secKey) { publicKey ->
                verify(
                    signatureCF,
                    signedDataCF,
                    publicKey,
                    kSecKeyAlgorithmECDSASignatureMessageX962SHA256
                )
                Result.success(signedData)
            }
        }
    }

    override suspend fun verifyJws(signedJws: String): Result<JsonObject> = withSecKey(
        keyId, ksecKeyType, null
    ) { privateKey ->
        usePublicKey(privateKey) { publicKey ->
            val result = DS_Operations.verifyWithJws(signedJws, publicKey)

            check(result.success()) {
                result.errorMessage()!!
            }

            result.isValidData()!!.toByteArray().let {
                Json.parseToJsonElement(it.decodeToString())
            }.let {
                Result.success(it.jsonObject)
            }
        }
    }

    override suspend fun getPublicKeyRepresentation(): ByteArray =
        publicKeyExternalRepresentation(this.keyId, ksecKeyType)

    override suspend fun getMeta(): KeyMeta = JwkKeyMeta(keyId, 256)


    companion object : CoreFoundationSecOperations {
        public fun create(kid: String, appId: String): P256Key {
            delete(kid)

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

                    P256Key(kid)

                } finally {
                    privateKeyParams?.let { CFBridgingRelease(it) }
                    generateKeyPairQuery?.let { CFBridgingRelease(it) }
                    CFBridgingRelease(size)
                }
            }
        }

        internal fun delete(kid: String) {
            cfRetain(kid.toNSData()) { kidCF ->
                operation(
                    query(
                        kidCF, ksecKeyType, null
                    )
                ) { query ->
                    SecItemDelete(query)
                }
            }
        }

        internal fun load(kid: String): P256Key {
            return withSecKey(
                kid, ksecKeyType, null
            ) {
                P256Key(kid)
            }
        }

        internal fun exist(kid: String) = try {
            load(kid)
            true
        } catch (e: Throwable) {
            false
        }.also { println("P256:$kid:exist=$it") }
    }
}