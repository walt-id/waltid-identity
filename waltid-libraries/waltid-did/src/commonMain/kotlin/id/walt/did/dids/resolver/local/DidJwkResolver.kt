package id.walt.did.dids.resolver.local

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.Base64Utils.base64UrlDecode
import id.walt.did.dids.DidUtils
import id.walt.did.dids.document.DidDocument
import id.walt.did.dids.document.DidJwkDocument
import love.forte.plugin.suspendtrans.annotation.JsPromise
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
class DidJwkResolver : LocalResolverMethod("jwk") {
    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun resolve(did: String): Result<DidDocument> {
        val keyResult = resolveToKey(did)
        if (keyResult.isFailure) return Result.failure(keyResult.exceptionOrNull()!!)

        val key = keyResult.getOrNull()!!

        val didDocument = DidDocument(DidJwkDocument(did, key.exportJWKObject()).toMap())

        return Result.success(didDocument)
    }

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun resolveToKey(did: String): Result<Key> =
        JWKKey.importJWK(DidUtils.pathFromDid(did)!!.base64UrlDecode().decodeToString())
}
