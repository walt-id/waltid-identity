package id.walt.did.dids.registrar

import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.did.dids.registrar.dids.DidDocConfig
import id.walt.did.dids.registrar.dids.DidKeyCreateOptions
import id.walt.did.dids.registrar.local.key.DidKeyRegistrar
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AzureStylePublicKeyDidTest {

    @Test
    fun didKeyDocumentPublicJwkKidIsNeverVaultUrl() = runTest {
        val material = JWKKey.generate(KeyType.secp256r1).getPublicKey()
        val thumbprint = material.getThumbprint()
        val azureStyle = JWKKey.importJWK(
            JsonObject(
                material.exportJWKObject() +
                    ("kid" to JsonPrimitive("https://example.vault.azure.net/keys/k/v1"))
            ).toString()
        ).getOrThrow()

        val result = DidKeyRegistrar().registerByKey(azureStyle, DidKeyCreateOptions(useJwkJcsPub = true))
        assertTrue(result.did.startsWith("did:key:"))
        val vm = result.didDocument["verificationMethod"]!!.jsonArray.first().jsonObject
        val vmId = vm["id"]!!.jsonPrimitive.content
        val jwkKid = vm["publicKeyJwk"]!!.jsonObject["kid"]!!.jsonPrimitive.content
        assertEquals("${result.did}#${result.did.removePrefix("did:key:")}", vmId)
        assertEquals(thumbprint, jwkKid)
        assertFalse(jwkKid.contains("https://"))
        assertFalse(vmId.contains("vault.azure.net"))
    }

    @Test
    fun didDocConfigFragmentUsesThumbprintNotVaultUrl() = runTest {
        val material = JWKKey.generate(KeyType.secp256r1).getPublicKey()
        val thumbprint = material.getThumbprint()
        val azureStyle = JWKKey.importJWK(
            JsonObject(
                material.exportJWKObject() +
                    ("kid" to JsonPrimitive("https://example.vault.azure.net/keys/k/v1"))
            ).toString()
        ).getOrThrow()

        val config = DidDocConfig.buildFromPublicKeySet(publicKeySet = setOf(azureStyle))
        val doc = config.toDidDocument("did:web:example.com")
        val vm = doc["verificationMethod"]!!.jsonArray.first().jsonObject
        assertEquals("did:web:example.com#$thumbprint", vm["id"]!!.jsonPrimitive.content)
        assertEquals(thumbprint, vm["publicKeyJwk"]!!.jsonObject["kid"]!!.jsonPrimitive.content)
    }
}
