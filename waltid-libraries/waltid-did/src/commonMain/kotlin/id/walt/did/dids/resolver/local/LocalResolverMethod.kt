package id.walt.did.dids.resolver.local

import id.walt.crypto.keys.Key
import id.walt.did.dids.document.DidDocument
import love.forte.plugin.suspendtrans.annotation.JsPromise
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
abstract class LocalResolverMethod(val method: String) {

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    abstract suspend fun resolve(did: String): Result<DidDocument>

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    @Deprecated("Use Crypto2DidKeyResolver or Crypto2DidService for key resolution")
    abstract suspend fun resolveToKey(did: String): Result<Key>

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    @Deprecated("Use Crypto2DidKeyResolver or Crypto2DidService for key resolution")
    open suspend fun resolveToKeys(did: String): Result<Set<Key>> =
        resolveToKey(did).map { setOf(it) }

}
