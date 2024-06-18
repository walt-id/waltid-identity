package id.walt.did.dids.registrar.local.web

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.did.dids.document.DidDocument
import id.walt.did.dids.document.DidWebDocument
import id.walt.did.dids.registrar.DidResult
import id.walt.did.dids.registrar.dids.DidCreateOptions
import id.walt.did.dids.registrar.local.LocalRegistrarMethod
import id.walt.did.utils.ExtensionMethods.ensurePrefix
import kotlinx.uuid.UUID
import kotlinx.uuid.generateUUID
import love.forte.plugin.suspendtrans.annotation.JsPromise
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking
import net.thauvin.erik.urlencoder.UrlEncoderUtil
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
class DidWebRegistrar : LocalRegistrarMethod("web") {
    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun register(options: DidCreateOptions): DidResult = options.get<KeyType>("keyType")?.let {
        registerByKey(JWKKey.generate(it), options)
    } ?: throw IllegalArgumentException("Option \"keyType\" not found.")

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun registerByKey(key: Key, options: DidCreateOptions): DidResult {
        return options.get<String>("domain")?.takeIf { it.isNotEmpty() }?.let {
            val domain = UrlEncoderUtil.encode(it)
            val path = options.get<String>("path")?.takeIf { it.isNotEmpty() }?.let {
                it.replace("[random-uuid]", UUID.generateUUID().toString()).ensurePrefix("/").split("/")
                    .joinToString(":") { part -> UrlEncoderUtil.encode(part) }
            } ?: ""
            DidResult(
                "did:web:$domain$path", DidDocument(
                    DidWebDocument(
                        did = "did:web:$domain$path", keyId = key.getKeyId(), didKey = key.getPublicKey().exportJWKObject()
                    ).toMap()
                )
            )
        } ?: throw IllegalArgumentException("Option \"domain\" not found.")
    }
}
