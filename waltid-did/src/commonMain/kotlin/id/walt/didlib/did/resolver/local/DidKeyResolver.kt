package id.walt.didlib.did.resolver.local

import id.walt.core.crypto.keys.Key
import id.walt.core.crypto.keys.KeyMetadata
import id.walt.core.crypto.keys.LocalKey
import id.walt.core.crypto.utils.MultiBaseUtils.convertMultiBase58BtcToRawKey
import id.walt.core.crypto.utils.MultiCodecUtils
import id.walt.core.crypto.utils.MultiCodecUtils.JwkJcsPubMultiCodecKeyCode
import id.walt.core.crypto.utils.MultiCodecUtils.getMultiCodecKeyCode
import id.walt.didlib.did.DidUtils
import id.walt.didlib.did.document.DidDocument
import id.walt.didlib.did.document.DidKeyDocument

class DidKeyResolver : LocalResolverMethod("key") {
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

    override suspend fun resolveToKey(did: String): Result<Key> = DidUtils.identifierFromDid(did)?.let {
        val publicKeyRaw = convertMultiBase58BtcToRawKey(it)
        when (val code = getMultiCodecKeyCode(it)) {
            JwkJcsPubMultiCodecKeyCode -> LocalKey.importJWK(publicKeyRaw.decodeToString())
            else -> Result.success(
                LocalKey.importRawPublicKey(
                    MultiCodecUtils.getKeyTypeFromKeyCode(code), KeyMetadata(), publicKeyRaw
                )
            )
        }
    } ?: Result.failure(Throwable("Failed to extract identifier from: $did"))
}
