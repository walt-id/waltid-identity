package id.walt.did.dids.resolver

import id.walt.crypto.keys.Key
import kotlinx.serialization.json.JsonObject
import love.forte.plugin.suspendtrans.annotation.JsPromise
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
interface DidResolver {
    val name: String

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    suspend fun getSupportedMethods(): Result<Set<String>>

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    suspend fun resolve(did: String): Result<JsonObject>

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    @Deprecated("Use Crypto2DidKeyResolver or Crypto2DidService for key resolution")
    suspend fun resolveToKey(did: String): Result<Key>

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    @Deprecated("Use Crypto2DidKeyResolver or Crypto2DidService for key resolution")
    suspend fun resolveToKeys(did: String): Result<Set<Key>>

}
