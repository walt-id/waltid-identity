package id.walt.did.dids.registrar.local.web

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.UuidUtils.randomUUIDString
import id.walt.did.dids.document.DidDocument
import id.walt.did.dids.document.DidWebDocument
import id.walt.did.dids.registrar.DidResult
import id.walt.did.dids.registrar.dids.DidCreateOptions
import id.walt.did.dids.registrar.dids.DidDocConfig
import id.walt.did.dids.registrar.local.LocalRegistrarMethod
import id.walt.did.utils.ExtensionMethods.ensurePrefix
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
    override suspend fun register(options: DidCreateOptions): DidResult = options.didDocConfig?.let {
        registerByDidDocConfig(options, options.didDocConfig)
    } ?: options.get<KeyType>("keyType")?.let {
        registerByKey(JWKKey.generate(it), options)
    } ?: throw IllegalArgumentException("Option \"keyType\" not found.")

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun registerByKey(key: Key, options: DidCreateOptions): DidResult {
            val domain = getUrlEncodedDomainOrThrow(options)
            val path = getPath(options)
            val did = getDid(domain, path)
            return DidResult(
                did,
                DidDocument(
                    DidWebDocument(
                        did = did,
                        keyId = key.getKeyId(),
                        didKey = key.getPublicKey().exportJWKObject()
                    ).toMap()
                ),
            )
    }

    private suspend fun registerByDidDocConfig(
        options: DidCreateOptions,
        didDocConfig: DidDocConfig,
    ): DidResult {
        val domain = getUrlEncodedDomainOrThrow(options)
        val path = getPath(options)
        val did = getDid(domain, path)
        return DidResult(
            did = did,
            didDocument = didDocConfig.toDidDocument(did),
        )
    }

    private fun getUrlEncodedDomainOrThrow(options: DidCreateOptions) =
        options.get<String>("domain")?.takeIf { it.isNotEmpty() }?.let { UrlEncoderUtil.encode(it) }
            ?: throw IllegalArgumentException("Option \"domain\" not found.")

    private fun getPath(options: DidCreateOptions) = options.get<String>("path")?.takeIf { it.isNotEmpty() }?.let {
        it.replace("[random-uuid]", randomUUIDString()).ensurePrefix("/").split("/")
            .joinToString(":") { part -> UrlEncoderUtil.encode(part) }
    } ?: ""

    private fun getDid(
        domain: String,
        path: String,
    ) = "did:web:$domain$path"
}
