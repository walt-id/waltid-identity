package id.walt.crypto

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyMeta
import id.walt.crypto.keys.KeyType
import id.walt.platform.utils.ios.DS_Operations
import id.walt.platform.utils.ios.RSAKeyUtils
import io.ktor.util.encodeBase64
import kotlinx.cinterop.ptr
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFMutableDictionaryRef
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
import platform.Security.kSecAttrKeyClassPrivate
import platform.Security.kSecAttrKeyClassPublic
import platform.Security.kSecAttrKeySizeInBits
import platform.Security.kSecAttrKeyType
import platform.Security.kSecAttrKeyTypeRSA
import platform.Security.kSecKeyAlgorithmRSASignatureDigestPKCS1v15SHA256

class RSAKey private constructor(
    val keyId: String, override val keyType: KeyType = KeyType.RSA,
) : Key(), CoreFoundationSecOperations {

    override suspend fun getThumbprint(): String = withSecKey(
        this.keyId, kSecAttrKeyTypeRSA, null
    ) { secKey ->
        RSAKeyUtils.thumbprintWithPublicKey(secKey, null)!!
    }

    override suspend fun exportJWK(): String = withSecKey(
        this.keyId, kSecAttrKeyTypeRSA, null
    ) { secKey ->
        RSAKeyUtils.exportJwkWithPublicKey(secKey, null)!!
    }

    override suspend fun signRaw(inputBytes: ByteArray): Any = signRaw(
        keyId, kSecAttrKeyTypeRSA, kSecKeyAlgorithmRSASignatureDigestPKCS1v15SHA256, inputBytes
    )

    private suspend fun signJws(bodyJson: ByteArray, headersJson: ByteArray): String {
        return withSecKey(
            keyId, kSecAttrKeyTypeRSA, kSecAttrKeyClassPrivate
        ) { privateKey ->
            val result = DS_Operations.signWithBody(
                bodyJson.toNSData(),
                "RS256",
                privateKey,
                headersJson.toNSData()
            )

            check(result.success()) {
                result.errorMessage()!!
            }

            result.data()!!
        }
    }

    override suspend fun signJws(plaintext: ByteArray, headers: Map<String, String>): String =
        withSecKey(
            keyId, kSecAttrKeyTypeRSA, kSecAttrKeyClassPrivate
        ) { privateKey ->
            val result = DS_Operations.signWithBody(
                plaintext.toNSData(), "RS256", privateKey, headers as Map<Any?, *>
            )

            check(result.success()) {
                result.errorMessage()!!
            }

            result.data()!!
        }

    override suspend fun verifyRaw(
        signed: ByteArray, detachedPlaintext: ByteArray?
    ): Result<ByteArray> = withSecKey(
        keyId, kSecAttrKeyTypeRSA, kSecAttrKeyClassPublic
    ) { secKey ->
        cfRetain(
            signed.toNSData(), detachedPlaintext!!.toNSData()
        ) { signatureCF, signedDataCF ->
            verify(
                signatureCF, signedDataCF, secKey, kSecKeyAlgorithmRSASignatureDigestPKCS1v15SHA256
            )
            Result.success(detachedPlaintext)
        }
    }

    override suspend fun verifyJws(signedJws: String): Result<JsonObject> = withSecKey(
        keyId, kSecAttrKeyTypeRSA, kSecAttrKeyClassPublic
    ) { publicKey ->
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

    override suspend fun getPublicKeyRepresentation(): ByteArray =
        publicKeyExternalRepresentation(this.keyId, kSecAttrKeyTypeRSA)

    override suspend fun getMeta(): KeyMeta {
        TODO("Not yet implemented")
    }

    companion object : CoreFoundationSecOperations {
        public fun create(kid: String, appId: String, size: UInt): RSAKey {
            check(size in 1024u..7680u step 256) {
                "The key size you provided is not supported."
            }

            delete(kid)

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

                    RSAKey(kid)

                } finally {
                    generateKeyParams?.let { CFBridgingRelease(it) }
                    CFBridgingRelease(nsSize)
                }
            }
        }


        internal fun delete(kid: String) {
            cfRetain(kid.toNSData()) { kidCF ->
                operation(
                    query(
                        kidCF, kSecAttrKeyTypeRSA, null
                    )
                ) { query ->
                    SecItemDelete(query)
                }
            }
        }

        internal fun load(kid: String): RSAKey {
            return withSecKey(
                kid, kSecAttrKeyTypeRSA, null
            ) {
                RSAKey(kid)
            }
        }

        internal fun exist(kid: String) = try {
            load(kid)
            true
        } catch (e: Throwable) {
            false
        }.also { println("RSA:$kid:exist=$it") }
    }


    override val hasPrivateKey: Boolean
        get() = true

    override suspend fun getKeyId(): String = keyId

    override suspend fun exportJWKObject(): JsonObject =
        exportJWK().let { Json.parseToJsonElement(it).jsonObject }

    override suspend fun exportPEM(): String =
        publicKeyExternalRepresentation(keyId, kSecAttrKeyTypeRSA).encodeBase64()

    override suspend fun getPublicKey(): Key = this
}