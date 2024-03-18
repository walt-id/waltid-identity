package id.walt.crypto.keys.jwk

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import love.forte.plugin.suspendtrans.annotation.JsPromise
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
interface JwkKeyCreator {
    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    suspend fun generate(type: KeyType, metadata: JwkKeyMetadata = JwkKeyMetadata()): JWKKey
    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    suspend fun importRawPublicKey(
        type: KeyType,
        rawPublicKey: ByteArray,
        metadata: JwkKeyMetadata
    ): Key

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    suspend fun importJWK(jwk: String): Result<JWKKey>

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    suspend fun importPEM(pem: String): Result<JWKKey>
}
