package id.walt.crypto.keys.azure

import com.azure.core.http.rest.PagedIterable
import com.azure.json.JsonProviders
import com.azure.security.keyvault.keys.KeyClient
import com.azure.security.keyvault.keys.cryptography.CryptographyAsyncClient
import com.azure.security.keyvault.keys.cryptography.models.SignResult
import com.azure.security.keyvault.keys.cryptography.models.SignatureAlgorithm
import com.azure.security.keyvault.keys.cryptography.models.VerifyResult
import com.azure.security.keyvault.keys.models.KeyProperties
import com.azure.security.keyvault.keys.models.KeyVaultKey
import id.walt.crypto.exceptions.KeyNotFoundException
import id.walt.crypto.keys.DirectSerializedKey
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto2.algorithms.EcdsaSignatureCodec
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import reactor.core.publisher.Mono
import java.security.MessageDigest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AzureSdkKeyVersionTest {
    private val keyClient = mockk<KeyClient>()
    private val cryptoClient = mockk<CryptographyAsyncClient>()

    @BeforeTest
    fun mockAzure() {
        mockkObject(KeyVaultClientFactory)
        every { KeyVaultClientFactory.keyClient(VAULT) } returns keyClient
        every { KeyVaultClientFactory.cryptoClient(VAULT, NAME, ORIGINAL_VERSION) } returns cryptoClient
    }

    @AfterTest
    fun restoreAzure() {
        unmockkObject(KeyVaultClientFactory)
    }

    @Test
    fun `restored signing key follows its original public key after rotation`() = runTest {
        val original = JWKKey.generate(KeyType.secp256r1)
        val rotated = JWKKey.generate(KeyType.secp256r1)
        val publicKey = original.getPublicKey()
        val message = "after Azure rotation".encodeToByteArray()
        val digest = MessageDigest.getInstance("SHA-256").digest(message)
        val rawSignature = EcdsaSignatureCodec.derToP1363(original.signRaw(message), 32)
        every { keyClient.getKey(NAME) } returns remoteKey("version-2", rotated.getPublicKey())
        every { keyClient.listPropertiesOfKeyVersions(NAME) } returns keyVersions("version-2", ORIGINAL_VERSION)
        every { keyClient.getKey(NAME, ORIGINAL_VERSION) } returns remoteKey(ORIGINAL_VERSION, publicKey)
        every { cryptoClient.sign(SignatureAlgorithm.ES256, digest) } returns Mono.just(mockk<SignResult> {
            every { signature } returns rawSignature
        })

        val key = stored(publicKey)
        val signature = key.signRaw(message)
        assertEquals(publicKey.getThumbprint(), key.getPublicKey().getThumbprint())
        assertTrue(publicKey.verifyRaw(signature, message).isSuccess)
        assertTrue(rotated.getPublicKey().verifyRaw(signature, message).isFailure)
        verify(exactly = 1) { KeyVaultClientFactory.cryptoClient(VAULT, NAME, ORIGINAL_VERSION) }
    }

    @Test
    fun `fails before signing when no version matches the stored public key`() = runTest {
        val original = JWKKey.generate(KeyType.secp256r1).getPublicKey()
        val rotated = JWKKey.generate(KeyType.secp256r1).getPublicKey()
        every { keyClient.getKey(NAME) } returns remoteKey("version-2", rotated)
        every { keyClient.listPropertiesOfKeyVersions(NAME) } returns keyVersions("version-2")

        val failure = assertFailsWith<KeyNotFoundException> { stored(original).signRaw(byteArrayOf(1)) }
        assertTrue(failure.message.orEmpty().contains("matches the stored public key"))
        verify(exactly = 0) { KeyVaultClientFactory.cryptoClient(any(), any(), any()) }
    }

    @Test
    fun `verify converts a valid 64 byte DER signature before sending it to Azure`() = runTest {
        val publicKey = JWKKey.importJWK(
            """{"kty":"EC","crv":"P-256","x":"ptjZBacR13ovjMYQZwwD-P5MsUBt9M1Rq17gx7S5Ytc","y":"HESgKQDNUBpxBB-0-Md4A2e1RrZh0Hvpx39q0dgm-8k"}""",
        ).getOrThrow()
        val message = "azure DER fixture".encodeToByteArray()
        val digest = MessageDigest.getInstance("SHA-256").digest(message)
        val der = ("303e02206b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c296" +
            "021a0102030405060708090a0b0c0d0e0f101112131415161718191a").hexToByteArray()
        val raw = ("6b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c296" +
            "0000000000000102030405060708090a0b0c0d0e0f101112131415161718191a").hexToByteArray()
        assertEquals(64, der.size)
        assertTrue(publicKey.verifyRaw(der, message).isSuccess)
        every { keyClient.getKey(NAME) } returns remoteKey(ORIGINAL_VERSION, publicKey)
        val sent = mutableListOf<ByteArray>()
        every { cryptoClient.verify(SignatureAlgorithm.ES256, digest, capture(sent)) } returns
            Mono.just(mockk<VerifyResult> { every { isValid } returns true })

        val key = stored(publicKey)
        assertContentEquals(message, key.verifyRaw(der, message).getOrThrow())
        assertContentEquals(message, key.verifyRaw(raw, message).getOrThrow())
        assertEquals(2, sent.size)
        sent.forEach { assertContentEquals(raw, it) }
    }

    private fun stored(publicKey: Key) = AzureKey(
        AzureKeyMetadataSDK(AzureSDKAuth(VAULT)),
        NAME,
        DirectSerializedKey(publicKey),
        KeyType.secp256r1,
    )

    private suspend fun remoteKey(version: String, publicKey: Key): KeyVaultKey {
        val jwk = JsonObject(publicKey.exportJWKObject() + ("kid" to JsonPrimitive("$VAULT/keys/$NAME/$version")))
        return JsonProviders.createReader("""{"key":$jwk,"attributes":{"enabled":true}}""")
            .use { KeyVaultKey.fromJson(it) }
    }

    private fun keyVersions(vararg versions: String): PagedIterable<KeyProperties> = mockk {
        every { iterator() } answers { versions.map(::keyProperties).toMutableList().iterator() }
    }

    private fun keyProperties(keyVersion: String): KeyProperties = mockk {
        every { version } returns keyVersion
    }

    private companion object {
        const val VAULT = "https://vault.example"
        const val NAME = "test-key"
        const val ORIGINAL_VERSION = "version-1"
    }
}
