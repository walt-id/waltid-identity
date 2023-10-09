package id.walt.did.dids.registrar.local.jwk

import id.walt.core.crypto.keys.Key
import id.walt.core.crypto.keys.KeyType
import id.walt.core.crypto.keys.LocalKey
import id.walt.core.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.did.dids.document.DidDocument
import id.walt.did.dids.document.DidJwkDocument
import id.walt.did.dids.registrar.DidResult
import id.walt.did.dids.registrar.dids.DidCreateOptions
import id.walt.did.dids.registrar.local.LocalRegistrarMethod
import io.ktor.utils.io.core.*

class DidJwkRegistrar : LocalRegistrarMethod("jwk") {
    override suspend fun register(options: DidCreateOptions) = options.get<KeyType>("keyType")?.let {
        registerByKey(LocalKey.generate(it), options)
    } ?: throw IllegalArgumentException("KeyType option not found.")

    override suspend fun registerByKey(key: Key, options: DidCreateOptions): DidResult {
        val did = "did:jwk:${key.exportJWK().toByteArray().encodeToBase64Url()}"

        val didDocument = DidDocument(
            DidJwkDocument(did, key.exportJWKObject())
                .toMap()
        )

        return DidResult(
            did = did,
            didDocument = didDocument
        )
    }
}
