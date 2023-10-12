package id.walt.did.dids.resolver.local

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.LocalKey
import id.walt.crypto.keys.LocalKeyMetadata
import id.walt.crypto.utils.MultiBaseUtils.convertMultiBase58BtcToRawKey
import id.walt.crypto.utils.MultiCodecUtils
import id.walt.crypto.utils.MultiCodecUtils.JwkJcsPubMultiCodecKeyCode
import id.walt.crypto.utils.MultiCodecUtils.getMultiCodecKeyCode
import id.walt.did.dids.DidUtils
import id.walt.did.dids.document.DidDocument
import id.walt.did.dids.document.DidKeyDocument

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
                    MultiCodecUtils.getKeyTypeFromKeyCode(code), publicKeyRaw, LocalKeyMetadata()
                )
            )
        }
    } ?: Result.failure(Throwable("Failed to extract identifier from: $did"))
}
