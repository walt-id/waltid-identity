package id.walt.didlib.did.registrar.local.key

import id.walt.core.crypto.keys.Key
import id.walt.core.crypto.keys.KeyType
import id.walt.core.crypto.keys.LocalKey
import id.walt.core.crypto.utils.JsonCanonicalizationUtils
import id.walt.core.crypto.utils.MultiBaseUtils.convertRawKeyToMultiBase58Btc
import id.walt.core.crypto.utils.MultiCodecUtils
import id.walt.core.crypto.utils.MultiCodecUtils.getMultiCodecKeyCode
import id.walt.didlib.did.document.DidDocument
import id.walt.didlib.did.document.DidKeyDocument
import id.walt.didlib.did.registrar.DidResult
import id.walt.didlib.did.registrar.dids.DidCreateOptions
import id.walt.didlib.did.registrar.local.LocalRegistrarMethod
import id.walt.didlib.utils.JsonCanonicalization
import kotlinx.serialization.json.JsonObject

class DidKeyRegistrar : LocalRegistrarMethod("key") {

    override suspend fun register(options: DidCreateOptions): DidResult = options.get<KeyType>("keyType")?.let {
        registerByKey(LocalKey.generate(it), options)
    } ?: throw IllegalArgumentException("KeyType option not found.")

    override suspend fun registerByKey(key: Key, options: DidCreateOptions): DidResult = options.let {
        if (key.keyType !in setOf(
                KeyType.Ed25519,
                KeyType.RSA,//TODO: ??is it supposed to be supported for did:key (uni-registrar doesn't)
                KeyType.secp256k1,
                KeyType.secp256r1
            )
        ) throw IllegalArgumentException("did:key can not be created with a ${key.keyType} key.")
        val identifierComponents = getIdentifierComponents(key, it)
        val identifier = convertRawKeyToMultiBase58Btc(identifierComponents.pubKeyBytes, identifierComponents.multiCodecKeyCode)
        createDid(identifier, key.exportJWKObject())
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