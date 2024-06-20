package id.walt.crypto

import id.walt.crypto.keys.*
import id.walt.platform.utils.ios.DS_Operations
import id.walt.platform.utils.ios.RSAKeyUtils
import io.ktor.util.encodeBase64
import kotlinx.cinterop.ptr
import kotlinx.cinterop.alloc
import kotlinx.cinterop.value
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import platform.CoreFoundation.*
import platform.Foundation.*
import platform.Security.*

class RSAKey private constructor(
    val keyId: String, override val keyType: KeyType = KeyType.RSA,
) : IosKey(), CoreFoundationSecOperations {

    override suspend fun getThumbprint(): String = withSecKey(
        this.keyId, kSecAttrKeyTypeRSA, null
    ) { secKey ->
        RSAKeyUtils.thumbprintWithPublicKey(secKey, null)!!
    }

    override suspend fun exportJWK(): String = withSecKey(
        this.keyId, kSecAttrKeyTypeRSA, null
    ) { secKey ->
        RSAKeyUtils.exportJwtWithPublicKey(secKey, null)!!
    }

    override suspend fun signRaw(inputBytes: ByteArray): Any = signRaw(
        keyId, kSecAttrKeyTypeRSA, kSecKeyAlgorithmRSASignatureDigestPKCS1v15SHA256, inputBytes
    )

    override suspend fun signJws(bodyJson: ByteArray, headersJson: ByteArray): String {
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

                    val createRandomKeyError = alloc<CFErrorRefVar>()
                    val privateKey = SecKeyCreateRandomKey(
                        generateKeyParams, createRandomKeyError.ptr
                    )

                    check(createRandomKeyError.value == null) {
                        val nsError = CFBridgingRelease(createRandomKeyError.value) as NSError
                        println(nsError.toString())
                        nsError.toString()
                    }

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