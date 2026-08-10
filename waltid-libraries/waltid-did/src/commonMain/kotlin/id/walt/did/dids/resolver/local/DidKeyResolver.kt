package id.walt.did.dids.resolver.local

import id.walt.crypto.keys.Key
import id.walt.did.dids.DidUtils
import id.walt.did.dids.document.DidDocument
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import id.walt.did.dids.document.DidKeyDocument
import id.walt.did.dids.document.MultibasePublicKeys
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
    override suspend fun resolve(did: String): Result<DidDocument> {
        // Decoded with crypto2 rather than through a v1 key: the v1 JS implementation feeds raw multicodec bytes to
        // npm-jose's importSPKI, so building the document from a v1 key made did:key unresolvable on JS. Inputs the
        // crypto2 decoder does not cover (identifiers without a multicodec prefix, unknown codecs) keep the legacy
        // path, which is why this is a fallback rather than a replacement.
        crypto2Document(did)?.let { return Result.success(it) }
        return legacyDocument(did)
    }

    private suspend fun crypto2Document(did: String): DidDocument? = try {
        val publicJwk = MultibasePublicKeys.decode(did).jwk.data.toByteArray().decodeToString()
        DidDocument(
            DidKeyDocument(
                did,
                DidUtils.identifierFromDid(did)!!,
                Json.parseToJsonElement(publicJwk).jsonObject,
            ).toMap()
        )
    } catch (cause: CancellationException) {
        throw cause
    } catch (_: Throwable) {
        null
    }

    @Suppress("DEPRECATION")
    private suspend fun legacyDocument(did: String): Result<DidDocument> = resolveToKey(did).fold(
        onSuccess = {
            Result.success(
                DidDocument(
                    DidKeyDocument(did, DidUtils.identifierFromDid(did)!!, it.exportJWKObject()).toMap()
                )
            )
        },
        onFailure = { Result.failure(it) },
    )

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    @Deprecated("Use Crypto2DidKeyResolver or Crypto2DidService for key resolution")
    override suspend fun resolveToKey(did: String): Result<Key> = DidUtils.identifierFromDid(did)?.let {
        KeyUtils.fromPublicKeyMultiBase(it)
    } ?: Result.failure(Throwable("Failed to extract identifier from: $did"))
}
