package id.walt.wallet2.handlers

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.wallet2.data.Wallet
import id.walt.wallet2.data.WalletKeyInfo
import id.walt.wallet2.data.WalletKeyStore
import id.walt.wallet2.data.resolveKeyMaterial
import id.walt.wallet2.stores.inmemory.InMemoryKeyStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class WalletCrypto2OnlyKeyStoreTest {
    private val runtime = CryptoRuntime(defaultSoftwareKeyProviders())

    @Test
    fun `in-memory store supports crypto2-only add lookup default list and remove`() = runTest {
        val key = crypto2Key("crypto2-only")
        val store = InMemoryKeyStore()

        assertEquals(key.id.value, store.addCrypto2Key(key))
        assertNull(store.getKey(key.id.value))
        assertSame(key, store.getCrypto2Key(key.id.value, setOf(KeyUsage.SIGN)))
        assertSame(key, store.getKeyMaterial(key.id.value, setOf(KeyUsage.SIGN))?.crypto2Key)
        assertNull(store.getKeyMaterial(key.id.value)?.legacyKey)
        assertEquals(listOf(key.id.value), store.listKeysAsList().map(WalletKeyInfo::keyId))
        assertSame(key, Wallet(id = "wallet", keyStores = listOf(store)).defaultCrypto2Key(setOf(KeyUsage.SIGN)))
        assertTrue(store.removeKey(key.id.value))
        assertNull(store.getCrypto2Key(key.id.value))
    }

    @Test
    fun `old v1 store remains compatible with default crypto2 methods`() = runTest {
        val legacyKey = JWKKey.generate(KeyType.Ed25519)
        val store = LegacyOnlyKeyStore(legacyKey)
        val material = store.getKeyMaterial(legacyKey.getKeyId(), setOf(KeyUsage.SIGN))

        assertSame(legacyKey, material?.legacyKey)
        assertNull(material?.crypto2Key)
        assertFailsWith<UnsupportedOperationException> { store.addCrypto2Key(crypto2Key("unsupported")) }
    }

    @Test
    fun `capabilities are scoped to the single key that will sign`() = runTest {
        val ed25519StaticKey = JWKKey.generate(KeyType.Ed25519)
        val crypto2Store = InMemoryKeyStore()
        val p256Key = crypto2Key("crypto2-capability")
        crypto2Store.addCrypto2Key(p256Key)

        val wallet = Wallet(
            id = "combined",
            keyStores = listOf(crypto2Store),
            staticKey = ed25519StaticKey,
        )
        val keyMaterial = assertNotNull(wallet.resolveKeyMaterial(null, setOf(KeyUsage.SIGN)))

        // The store key is the one that will sign, so only its algorithm is advertised and accepted -
        // not the union with the Ed25519 static key, which submission would never use.
        assertEquals(p256Key.id.value, keyMaterial.keyId)
        assertEquals(listOf("ES256"), keyMaterial.presentationCapabilities().supportedJwsAlgorithms)
    }

    @Test
    fun `legacy-only key material falls back to its v1 key type`() = runTest {
        val legacyKey = JWKKey.generate(KeyType.Ed25519)
        val keyMaterial = assertNotNull(
            Wallet(id = "legacy", staticKey = legacyKey).resolveKeyMaterial(null, setOf(KeyUsage.SIGN))
        )

        assertNull(keyMaterial.crypto2Key)
        assertEquals(listOf("Ed25519"), keyMaterial.presentationCapabilities().supportedJwsAlgorithms)
    }

    private suspend fun crypto2Key(id: String) = runtime.generateSoftwareKey(
        GenerateSoftwareKeyRequest(
            id = KeyId(id),
            spec = KeySpec.Ec(EcCurve.P256),
            usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
        )
    )

    private class LegacyOnlyKeyStore(private val key: Key) : WalletKeyStore {
        override suspend fun getKey(keyId: String): Key? = key.takeIf { keyId == it.getKeyId() }

        override suspend fun listKeys(): Flow<WalletKeyInfo> =
            flowOf(WalletKeyInfo(key.getKeyId(), key.keyType.name))

        override suspend fun addKey(key: Key): String = key.getKeyId()

        override suspend fun removeKey(keyId: String): Boolean = false
    }
}
