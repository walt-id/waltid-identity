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
import id.walt.crypto2.providers.cryptography.CryptographySoftwareKeyProvider
import id.walt.wallet2.data.Wallet
import id.walt.wallet2.data.WalletKeyInfo
import id.walt.wallet2.data.WalletKeyStore
import id.walt.wallet2.stores.inmemory.InMemoryKeyStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class WalletCrypto2OnlyKeyStoreTest {
    private val runtime = CryptoRuntime(listOf(CryptographySoftwareKeyProvider()))

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
    fun `capability discovery includes legacy fallback and prefers actual crypto2 material`() = runTest {
        val staticKey = JWKKey.generate(KeyType.Ed25519)
        val crypto2Store = InMemoryKeyStore()
        val crypto2Key = crypto2Key("crypto2-capability")
        crypto2Store.addCrypto2Key(crypto2Key)

        val combined = Wallet(
            id = "combined",
            keyStores = listOf(crypto2Store),
            staticKey = staticKey,
        ).presentationRuntimeCapabilities()

        assertEquals(listOf("ES256", "Ed25519"), combined.supportedJwsAlgorithms)

        val sameIdStore = InMemoryKeyStore()
        sameIdStore.addKey(staticKey)
        sameIdStore.addCrypto2Key(
            runtime.generateSoftwareKey(
                GenerateSoftwareKeyRequest(
                    id = KeyId(staticKey.getKeyId()),
                    spec = KeySpec.Ec(EcCurve.P256),
                    usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
                )
            )
        )

        assertEquals(
            listOf("ES256"),
            Wallet(id = "preferred", keyStores = listOf(sameIdStore))
                .presentationRuntimeCapabilities().supportedJwsAlgorithms,
        )
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
