package id.walt.crypto.keys

import love.forte.plugin.suspendtrans.annotation.JsPromise
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
interface LocalKeyCreator {
    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    suspend fun generate(type: KeyType, metadata: LocalKeyMetadata = LocalKeyMetadata()): LocalKey
    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    suspend fun importRawPublicKey(
        type: KeyType,
        rawPublicKey: ByteArray,
        metadata: LocalKeyMetadata
    ): Key

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    suspend fun importJWK(jwk: String): Result<LocalKey>

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    suspend fun importPEM(pem: String): Result<LocalKey>
}
