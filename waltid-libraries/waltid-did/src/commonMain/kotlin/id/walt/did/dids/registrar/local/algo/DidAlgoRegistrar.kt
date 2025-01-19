package id.walt.did.dids.registrar.local.algo

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.did.dids.registrar.DidResult
import id.walt.did.dids.document.DidDocument
import id.walt.did.dids.document.DidAlgoDocument
import id.walt.did.dids.document.DidJwkDocument
import id.walt.did.dids.registrar.dids.DidCreateOptions
import id.walt.did.dids.registrar.local.LocalRegistrarMethod
import io.ktor.utils.io.core.*
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

expect fun saveDidDocumentToAlgorand(didDocument: DidDocument): Unit

class DidAlgoRegistrar() : LocalRegistrarMethod("algo") {
    override suspend fun register(options: DidCreateOptions): DidResult =
        registerByKey(JWKKey.generate(KeyType.Ed25519), options)
    override suspend fun registerByKey(key: Key, options: DidCreateOptions): DidResult {
        val publicKey = key.getPublicKey().getPublicKeyRepresentation();
       // val didIdentifier = generateDidIdentifier(publicKey)
        //val did = "did:" + method + ":" + didIdentifier;
        val did = "did:jwk:${key.getPublicKey().exportJWK().toByteArray().encodeToBase64Url()}"


        val didDocument = createDidDocument(did, publicKey);
        saveDidDocumentToAlgorand(didDocument)
        return DidResult(did, didDocument)
    }
     // Custom method to create a DID Document for Algorand
     @OptIn(ExperimentalEncodingApi::class)
     private fun createDidDocument(did: String, publicKey: ByteArray): DidDocument {

         // Encode the public key in Base64URL format
         val base64UrlEncodedPublicKey = Base64.UrlSafe.encode(publicKey)

         // Create the JWK
         val publicKeyJwk = buildJsonObject {
             put("kty", JsonPrimitive("OKP"))
             put("crv", JsonPrimitive("Ed25519"))
             put("x", JsonPrimitive(base64UrlEncodedPublicKey))
         }

         // Define the DID and key ID
         //val did = "did:algo:1234" // Replace with your actual DID
         val keyId = "key-1" // Replace with your desired key ID

         // Create the DidAlgoDocument
         return DidDocument(DidAlgoDocument(did, keyId, publicKeyJwk).toMap())
    }

    private fun generateDidIdentifier(publicKey: String): String {
        return publicKey.hashCode().toString()
    }

    private fun generatePublicKey(): String {
        // This function generates a random public key in Base58 format.
        // In a real implementation, this would involve cryptographic operations
        // to create a valid public key. Here, we return a placeholder string.
        return "randomlyGeneratedPublicKeyBase58"
    }

    private fun extractDidIdentifier(did: String): String {
        return if (":" in did) {
            did.split(":").last()
        } else {
            did
        }
    }
}