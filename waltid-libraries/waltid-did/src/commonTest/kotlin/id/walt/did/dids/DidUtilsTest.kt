package id.walt.did.dids

import kotlin.test.Test
import kotlin.test.assertEquals

class DidUtilsTest {
    @Test
    fun `base DID is extracted from all DID URL components`() {
        assertEquals(DID, DidUtils.didFromDidUrl(DID))
        assertEquals(DID, DidUtils.didFromDidUrl("$DID/keys/1"))
        assertEquals(DID, DidUtils.didFromDidUrl("$DID?versionId=1"))
        assertEquals(DID, DidUtils.didFromDidUrl("$DID#key-1"))
        assertEquals(DID, DidUtils.didFromDidUrl("$DID/keys/1?versionId=1#key-1"))
    }

    private companion object {
        const val DID = "did:web:example.com:users:u1"
    }
}
