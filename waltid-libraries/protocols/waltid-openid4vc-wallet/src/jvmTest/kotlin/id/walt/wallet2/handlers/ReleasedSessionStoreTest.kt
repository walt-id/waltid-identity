package id.walt.wallet2.handlers

import kotlinx.coroutines.test.runTest
import kotlin.test.*

class ReleasedSessionStoreTest {
    @Test fun compiledReleasedImplementerRetainsSnapshotsWithoutTheAtomicCapability() = runTest {
        val bytes = checkNotNull(javaClass.getResourceAsStream("/compat/ReleasedSessionStore.class.bin")).use { it.readBytes() }
        val store = object : ClassLoader(javaClass.classLoader) {
            fun instantiate() = defineClass("ReleasedSessionStore", bytes, 0, bytes.size)
                .getDeclaredConstructor().newInstance() as WalletIssuanceSessionStore
        }.instantiate()
        assertFalse(store is AtomicWalletIssuanceSessionStore)
        val original = WalletIssuanceSessionRecord("record", "session", WalletIssuanceSessionRecordKind.ACTIVE_SESSION, "review", 1)
        store.put(original)
        val runtime = WalletIssuanceSessionState("wallet", store)
        val replacement = original.copy(payload = "accepted", updatedAtEpochMilliseconds = 2)
        assertTrue(runtime.compareAndSet(original, replacement))
        assertFalse(runtime.compareAndSet(original, null))
        assertEquals(listOf(replacement), store.list())
        // A new runtime can read and update the snapshot; no cross-runtime exclusion is promised.
        assertTrue(WalletIssuanceSessionState("wallet", store).compareAndSet(replacement, null))
        assertNull(store.get(original.id))
    }
}
