package id.walt.crypto.keys

import id.walt.crypto.keys.PublicKeyIds.publicJwkForPublish
import id.walt.crypto.keys.PublicKeyIds.publicKeyId
import id.walt.crypto.keys.azure.AzureKeyRestApi
import id.walt.crypto.keys.jwk.JWKKey
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PublicKeyIdsTest {

    @Test
    fun publicKeyIdUsesThumbprintWhenJwkKidIsHttpUrl() = runTest {
        val material = JWKKey.generate(KeyType.secp256r1).getPublicKey()
        val thumbprint = material.getThumbprint()
        val withVaultKid = JWKKey.importJWK(
            JsonObject(
                material.exportJWKObject() + ("kid" to JsonPrimitive("https://example.vault.azure.net/keys/k/v1"))
            ).toString()
        ).getOrThrow()

        assertTrue(PublicKeyIds.isHttpKeyId(withVaultKid.getKeyId()))
        assertEquals(thumbprint, withVaultKid.publicKeyId())
        val published = withVaultKid.publicJwkForPublish()
        assertEquals(thumbprint, published["kid"]!!.jsonPrimitive.content)
        assertFalse(published["kid"]!!.jsonPrimitive.content.contains("vault.azure.net"))
    }

    @Test
    fun parseAzurePublicKeyStripsVaultKidAndExposesThumbprint() = runTest {
        val material = JWKKey.generate(KeyType.secp256r1).getPublicKey()
        val thumbprint = material.getThumbprint()
        val vaultUrl = "https://example.vault.azure.net/keys/demo/version-1"
        val azureStyleJwk = JsonObject(
            material.exportJWKObject().toMutableMap().apply {
                put("kid", JsonPrimitive(vaultUrl))
                put("key_ops", JsonPrimitive("sign"))
            }
        )

        val parsed = AzureKeyRestApi.AzureKeyFunctions.parseAzurePublicKey(azureStyleJwk)
        assertEquals(vaultUrl, parsed.kid)
        assertEquals(thumbprint, parsed.publicKey.getKeyId())
        assertEquals(thumbprint, parsed.publicKey.getThumbprint())
        assertFalse(parsed.publicKey.exportJWKObject()["kid"]!!.jsonPrimitive.content.contains("https://"))

        val azureKey = AzureKeyRestApi(
            id = vaultUrl,
            _keyType = KeyType.secp256r1,
            _publicKey = DirectSerializedKey(parsed.publicKey),
        )
        assertEquals(vaultUrl, azureKey.keyIdUrl)
        assertEquals(thumbprint, azureKey.getKeyId())
        assertEquals(thumbprint, azureKey.getThumbprint())
        assertEquals(thumbprint, azureKey.exportJWKObject()["kid"]!!.jsonPrimitive.content)
    }
}
