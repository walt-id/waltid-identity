package id.walt.did.dids.registrar.local.jwk

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.LocalKey
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.did.dids.document.DidDocument
import id.walt.did.dids.document.DidJwkDocument
import id.walt.did.dids.registrar.DidResult
import id.walt.did.dids.registrar.dids.DidCreateOptions
import id.walt.did.dids.registrar.local.LocalRegistrarMethod
import io.ktor.utils.io.core.*
import love.forte.plugin.suspendtrans.annotation.JsPromise
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@ExperimentalJsExport
@JsExport
class DidJwkRegistrar : LocalRegistrarMethod("jwk") {
    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun register(options: DidCreateOptions) = options.get<KeyType>("keyType")?.let {
        registerByKey(LocalKey.generate(it), options)
    } ?: throw IllegalArgumentException("KeyType option not found.")

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun registerByKey(key: Key, options: DidCreateOptions): DidResult {
        val did = "did:jwk:${key.getPublicKey().exportJWK().toByteArray().encodeToBase64Url()}"

        val didDocument = DidDocument(
            DidJwkDocument(did, key.getPublicKey().exportJWKObject())
                .toMap()
        )

        return DidResult(
            did = did,
            didDocument = didDocument
        )
    }
}
