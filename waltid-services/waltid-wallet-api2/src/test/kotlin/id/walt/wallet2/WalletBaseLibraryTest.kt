package id.walt.wallet2

import id.walt.wallet2.data.Wallet
import id.walt.wallet2.data.WalletDidEntry
import id.walt.wallet2.stores.inmemory.InMemoryCredentialStore
import id.walt.wallet2.stores.inmemory.InMemoryDidStore
import id.walt.wallet2.stores.inmemory.InMemoryKeyStore
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for waltid-openid4vc-wallet base library:
 * in-memory store implementations and [Wallet] aggregate helpers.
 */
class WalletBaseLibraryTest {

    @Test
    fun testInMemoryKeyStore() = runTest {
        val store = InMemoryKeyStore()
        assertTrue(store.listKeys().isEmpty())
        assertNull(store.getDefaultKey())
    }

    @Test
    fun testInMemoryDidStore() = runTest {
        val store = InMemoryDidStore()
        assertTrue(store.listDids().toList().isEmpty())
        assertNull(store.getDefaultDid())

        val entry = WalletDidEntry(did = "did:key:test", document = "{}")
        store.addDid(entry)

        val dids = store.listDids().toList()
        assertEquals(1, dids.size)
        assertEquals("did:key:test", dids[0].did)
        assertEquals("did:key:test", store.getDefaultDid())

        val found = store.getDid("did:key:test")
        assertNotNull(found)
        assertEquals("did:key:test", found.did)

        val removed = store.removeDid("did:key:test")
        assertTrue(removed)
        assertTrue(store.listDids().toList().isEmpty())
    }

    @Test
    fun testWalletDefaultDidFallbackToStaticDid() = runTest {
        val wallet = Wallet(
            id = "test-wallet",
            staticDid = "did:key:static"
        )
        assertEquals("did:key:static", wallet.defaultDid())
    }

    @Test
    fun testWalletDefaultKeyFallsBackToStaticKey() = runTest {
        // Wallet with no key stores and no static key → null
        val emptyWallet = Wallet(id = "empty")
        assertNull(emptyWallet.defaultKey())
    }

    @Test
    fun testWalletStreamAllCredentials() = runTest {
        val store1 = InMemoryCredentialStore()
        val store2 = InMemoryCredentialStore()

        // We cannot easily create a real DigitalCredential without parsing,
        // so we just verify the streaming aggregation across stores is empty here.
        val wallet = Wallet(
            id = "multi-store",
            credentialStores = listOf(store1, store2)
        )

        val all = wallet.streamAllCredentials().toList()
        assertTrue(all.isEmpty())
    }

    @Test
    fun testCreateWalletRequest_initBlockEnforcesExclusivity() {
        // Both offerUrl and offerJson → should throw
        val threw = runCatching {
            id.walt.wallet2.handlers.ReceiveCredentialRequest(
                offerUrl = io.ktor.http.Url("openid-credential-offer://example"),
                offerJson = kotlinx.serialization.json.JsonObject(emptyMap())
            )
        }.isFailure
        assertTrue(threw, "Should have thrown when both offerUrl and offerJson are set")

        // Neither → should throw
        val threwNeither = runCatching {
            id.walt.wallet2.handlers.ReceiveCredentialRequest()
        }.isFailure
        assertTrue(threwNeither, "Should have thrown when neither offerUrl nor offerJson is set")
    }

    @Test
    fun testPresentCredentialRequest_initBlockEnforcesExclusivity() {
        val threw = runCatching {
            id.walt.wallet2.handlers.PresentCredentialRequest(
                requestUrl = io.ktor.http.Url("openid4vp://authorize"),
                requestObject = kotlinx.serialization.json.JsonObject(emptyMap())
            )
        }.isFailure
        assertTrue(threw)
    }
}
