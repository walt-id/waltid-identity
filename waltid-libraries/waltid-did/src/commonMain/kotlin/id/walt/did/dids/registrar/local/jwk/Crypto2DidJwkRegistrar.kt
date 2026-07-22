package id.walt.did.dids.registrar.local.jwk

import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.crypto2.keys.Key
import id.walt.did.dids.document.DidDocument
import id.walt.did.dids.document.DidJwkDocument
import id.walt.did.dids.registrar.Crypto2DidRegistrar
import id.walt.did.dids.registrar.DidResult
import id.walt.did.dids.registrar.dids.DidCreateOptions
import id.walt.did.dids.registrar.exportPublicJwkObject
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Crypto2DidJwkRegistrar : Crypto2DidRegistrar {
    override suspend fun createByKey(key: Key, options: DidCreateOptions): DidResult {
        val publicJwk = key.exportPublicJwkObject()
        val did = "did:jwk:${Json.encodeToString(publicJwk).encodeToByteArray().encodeToBase64Url()}"
        return DidResult(did, DidDocument(DidJwkDocument(did, publicJwk).toMap()))
    }
}
