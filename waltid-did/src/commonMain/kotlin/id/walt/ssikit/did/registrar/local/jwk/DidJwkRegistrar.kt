package id.walt.ssikit.did.registrar.local.jwk

import id.walt.core.crypto.keys.LocalKey
import id.walt.core.crypto.keys.Key
import id.walt.core.crypto.keys.KeyType
import id.walt.core.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.ssikit.did.document.DidDocument
import id.walt.ssikit.did.document.DidJwkDocument
import id.walt.ssikit.did.registrar.DidResult
import id.walt.ssikit.did.registrar.dids.DidCreateOptions
import id.walt.ssikit.did.registrar.dids.DidJwkCreateOptions
import id.walt.ssikit.did.registrar.local.LocalRegistrarMethod
import io.ktor.utils.io.core.*

class DidJwkRegistrar : LocalRegistrarMethod("jwk") {
    override suspend fun register(options: DidCreateOptions) =
        registerByKey(LocalKey.generate(KeyType.Ed25519), options)

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

suspend fun main() {
    val r = DidJwkRegistrar().register(DidJwkCreateOptions())
    println(r.did)
    println(r.didDocument)
}
