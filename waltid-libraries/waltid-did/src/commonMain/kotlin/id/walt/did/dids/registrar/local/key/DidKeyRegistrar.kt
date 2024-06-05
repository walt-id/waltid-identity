package id.walt.did.dids.registrar.local.key

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.JsonCanonicalizationUtils
import id.walt.crypto.utils.MultiBaseUtils.convertRawKeyToMultiBase58Btc
import id.walt.crypto.utils.MultiCodecUtils
import id.walt.crypto.utils.MultiCodecUtils.getMultiCodecKeyCode
import id.walt.did.dids.document.DidDocument
import id.walt.did.dids.document.DidKeyDocument
import id.walt.did.dids.registrar.DidResult
import id.walt.did.dids.registrar.dids.DidCreateOptions
import id.walt.did.dids.registrar.local.LocalRegistrarMethod
import id.walt.did.utils.JsonCanonicalization
import kotlinx.serialization.json.JsonObject
import love.forte.plugin.suspendtrans.annotation.JsPromise
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
class DidKeyRegistrar : LocalRegistrarMethod("key") {

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
    override suspend fun registerByKey(key: Key, options: DidCreateOptions): DidResult = options.let {
        val pubKey = key.getPublicKey()
        val identifierComponents = getIdentifierComponents(pubKey, it)
        val identifier = convertRawKeyToMultiBase58Btc(identifierComponents.pubKeyBytes, identifierComponents.multiCodecKeyCode)
        createDid(identifier, pubKey.exportJWKObject())
    }

    private suspend fun getIdentifierComponents(key: Key, options: DidCreateOptions): IdentifierComponents =
        options.get<Boolean>("useJwkJcsPub")?.takeIf { it }?.let {
            IdentifierComponents(
                MultiCodecUtils.JwkJcsPubMultiCodecKeyCode, JsonCanonicalization.getCanonicalBytes(
                    JsonCanonicalizationUtils.convertToRequiredMembersJsonString(key)
                )
            )
        } ?: IdentifierComponents(getMultiCodecKeyCode(key.keyType), key.getPublicKeyRepresentation())

    private fun createDid(identifier: String, publicKeyJwk: JsonObject) = "did:key:$identifier".let {
        DidResult(it, DidDocument(DidKeyDocument(did = it, identifier = identifier, didKey = publicKeyJwk).toMap()))
    }
}
