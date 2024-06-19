package id.walt.did.dids.resolver.local

import id.walt.crypto.keys.Key
import id.walt.did.dids.DidUtils
import id.walt.did.dids.document.DidDocument
import id.walt.did.dids.document.DidKeyDocument
import id.walt.did.utils.KeyUtils
import love.forte.plugin.suspendtrans.annotation.JsPromise
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
class DidKeyResolver : LocalResolverMethod("key") {
    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun resolve(did: String): Result<DidDocument> = resolveToKey(did).fold(
        onSuccess = {
            Result.success(
                DidDocument(
                    DidKeyDocument(
                        did, DidUtils.identifierFromDid(did)!!, it.exportJWKObject()
                    ).toMap()
                )
            )
        }, onFailure = {
            Result.failure(it)
        }
    )

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun resolveToKey(did: String): Result<Key> = DidUtils.identifierFromDid(did)?.let {
        KeyUtils.fromPublicKeyMultiBase(it)
    } ?: Result.failure(Throwable("Failed to extract identifier from: $did"))
}
