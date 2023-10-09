package id.walt.didlib.did.registrar.local.web

import id.walt.core.crypto.keys.Key
import id.walt.core.crypto.keys.KeyType
import id.walt.core.crypto.keys.LocalKey
import id.walt.didlib.did.document.DidDocument
import id.walt.didlib.did.document.DidWebDocument
import id.walt.didlib.did.registrar.DidResult
import id.walt.didlib.did.registrar.dids.DidCreateOptions
import id.walt.didlib.did.registrar.local.LocalRegistrarMethod
import id.walt.didlib.utils.EncodingUtils.urlEncode

class DidWebRegistrar : LocalRegistrarMethod("web") {
    override suspend fun register(options: DidCreateOptions): DidResult = options.get<KeyType>("keyType")?.let {
        registerByKey(LocalKey.generate(it), options)
    } ?: throw IllegalArgumentException("KeyType option not found.")

    override suspend fun registerByKey(key: Key, options: DidCreateOptions): DidResult =
        options.get<String>("domain")?.takeIf {
            it.isNotEmpty()
        }?.let {
            val domain = urlEncode(it)
            val path = options.get<String>("path")?.takeIf { it.isNotEmpty() }?.let {
                it.split("/").joinToString(":") { part -> urlEncode(part) }
            } ?: ""
            DidResult(
                "did:web:$domain$path", DidDocument(
                    DidWebDocument(
                        did = "did:web:$domain$path", keyId = key.getKeyId(), didKey = key.exportJWKObject()
                    ).toMap()
                )
            )
        } ?: throw IllegalArgumentException("Domain option not found.")
}