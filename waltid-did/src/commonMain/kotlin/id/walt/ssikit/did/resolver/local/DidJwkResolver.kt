package id.walt.ssikit.did.resolver.local

import id.walt.core.crypto.keys.Key
import id.walt.core.crypto.keys.LocalKey
import id.walt.core.crypto.utils.Base64Utils.base64UrlDecode
import id.walt.ssikit.did.DidUtils
import id.walt.ssikit.did.document.DidDocument
import id.walt.ssikit.did.document.DidJwkDocument

class DidJwkResolver : LocalResolverMethod("jwk") {
    override suspend fun resolve(did: String): Result<DidDocument> {
        val keyResult = resolveToKey(did)
        if (keyResult.isFailure) return Result.failure(keyResult.exceptionOrNull()!!)

        val key = keyResult.getOrNull()!!

        val didDocument = DidDocument(DidJwkDocument(did, key.exportJWKObject()).toMap())

        return Result.success(didDocument)
    }

    override suspend fun resolveToKey(did: String): Result<Key> =
        LocalKey.importJWK(DidUtils.pathFromDid(did)!!.base64UrlDecode().decodeToString())
}