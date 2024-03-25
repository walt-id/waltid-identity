package id.walt.webwallet


import id.walt.did.dids.registrar.dids.DidKeyCreateOptions
import id.walt.did.dids.registrar.local.jwk.DidJwkRegistrar
import id.walt.did.dids.resolver.LocalResolver
import kotlin.io.encoding.ExperimentalEncodingApi
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.oci.OCIKey
import id.walt.crypto.keys.oci.OCIKeyMetadata
import id.walt.webwallet.config.ConfigManager
import id.walt.webwallet.config.OciKeyConfig
import id.walt.webwallet.config.OidcConfiguration


@OptIn(ExperimentalEncodingApi::class)
suspend fun main() {

    val config = ConfigManager.getConfig<OidcConfiguration>()
    println("Config: $config")


//    val keyId = "ocid1.key.oc1.eu-frankfurt-1.ens6bc6aaafs6.abtheljrcupblxs33myczzlszeb27o2uxln4fdqyhdjzkm5npcjmirn4eaya"
//
//    val key = OCIKey(configurationKey, keyId, _keyType = KeyType.secp256r1)
//    println("OCI Key: $key")
//    println("Public key of OCI Key: ${key.getPublicKey().exportJWK()}")
//
//    val did = DidJwkRegistrar().registerByKey(key, DidKeyCreateOptions(keyType = KeyType.secp256r1)).did
//    println("DID for OCI key: $did")
//
//
//    val plaintext = "this is my plaintext".encodeToByteArray()
//
//    val signed = key.signJws(plaintext).encodeToByteArray().decodeToString()
//    println("Signed: $signed")
//
//    // Verifier
//
//    val publicKey = LocalResolver().resolveToKey(did).getOrThrow()
//    println("Resolved key: ${publicKey.exportJWK()}")
//
//    println("Verification: ${publicKey.verifyJws(signed)}")
}
