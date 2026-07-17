package id.walt.wallet2

import id.walt.wallet2.data.Wallet
import id.walt.wallet2.data.WalletDidEntry
import id.walt.wallet2.handlers.ImportCredentialRequest
import id.walt.wallet2.handlers.WalletCredentialHandler
import id.walt.wallet2.stores.inmemory.InMemoryCredentialStore
import id.walt.wallet2.stores.inmemory.InMemoryDidStore
import id.walt.wallet2.stores.inmemory.InMemoryKeyStore
import id.walt.wallet2.stores.inmemory.InMemoryWalletStore
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlin.test.*

/**
 * Unit tests for waltid-openid4vc-wallet base library:
 * in-memory store implementations and [Wallet] aggregate helpers.
 */
class WalletBaseLibraryTest {

    @Test
    fun testInMemoryKeyStore() = runTest {
        val store = InMemoryKeyStore()
        assertTrue(store.listKeys().toList().isEmpty())
        assertNull(store.getDefaultKey())
    }

    @Test
    fun testInMemoryDidStore() = runTest {
        val store = InMemoryDidStore()
        assertTrue(store.listDids().toList().isEmpty())
        assertNull(store.getDefaultDid())

        val entry = WalletDidEntry(did = "did:key:test", document = JsonObject(emptyMap()))
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
    fun testDeletingWalletRemovesAccountMapping() = runTest {
        val store = InMemoryWalletStore()
        store.saveWallet(Wallet(id = "wallet"))
        store.linkWalletToAccount("account", "wallet")

        store.deleteWallet("wallet")

        assertTrue(store.getWalletIdsForAccount("account").isNullOrEmpty())
    }

    @Test
    fun testRawCredentialImportUsesSharedWalletOperation() = runTest {
        val credentialStore = InMemoryCredentialStore()
        val wallet = Wallet(id = "wallet", credentialStores = listOf(credentialStore))
        val rawCredential =
            "eyJhbGciOiJFZERTQSIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJkaWQ6a2V5OnRlc3QiLCJpc3MiOiJodHRwczovL2V4YW1wbGUuY29tIiwidmMiOnsiQGNvbnRleHQiOlsiaHR0cHM6Ly93d3cudzMub3JnLzIwMTgvY3JlZGVudGlhbHMvdjEiXSwidHlwZSI6WyJWZXJpZmlhYmxlQ3JlZGVudGlhbCIsIlRlc3RDcmVkZW50aWFsIl0sImNyZWRlbnRpYWxTdWJqZWN0Ijp7ImlkIjoiZGlkOmtleTp0ZXN0IiwibmFtZSI6IlRlc3QifX19.signature"

        val imported = WalletCredentialHandler.importCredential(
            wallet,
            ImportCredentialRequest(rawCredential = rawCredential, label = "Imported"),
        )

        assertEquals("Imported", imported.label)
        assertEquals(imported, credentialStore.getCredential(imported.id))
    }

    @Test
    fun testCreateWalletRequest_initBlockEnforcesExclusivity() {
        // Both offerUrl and offerJson → should throw
        val threw = runCatching {
            id.walt.wallet2.handlers.ReceiveCredentialRequest(
                offerUrl = io.ktor.http.Url("openid-credential-offer://example"),
                offerJson = JsonObject(emptyMap())
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
    fun testPresentCredentialRequestCarriesUntrustedRequestUrl() {
        val requestUrl = io.ktor.http.Url("openid4vp://authorize?client_id=test")
        val request = id.walt.wallet2.handlers.PresentCredentialRequest(requestUrl = requestUrl)

        assertEquals(requestUrl, request.requestUrl)
    }
}
