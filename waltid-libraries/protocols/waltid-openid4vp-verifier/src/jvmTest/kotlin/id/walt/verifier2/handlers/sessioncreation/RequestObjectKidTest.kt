package id.walt.verifier2.handlers.sessioncreation

import kotlin.test.Test
import kotlin.test.assertEquals

class RequestObjectKidTest {

    @Test
    fun `DID client identifiers use the DID key fragment`() {
        assertEquals(
            "did:key:z6MkExample#key-1",
            requestObjectKid("decentralized_identifier:did:key:z6MkExample", "key-1"),
        )
        assertEquals(
            "did:jwk:example#key-2",
            requestObjectKid("decentralized_identifier:did:jwk:example", "key-2"),
        )
    }

    @Test
    fun `existing DID fragments and non-DID client identifiers are preserved safely`() {
        assertEquals(
            "did:key:z6MkExample#existing",
            requestObjectKid("decentralized_identifier:did:key:z6MkExample#existing", "key-1"),
        )
        assertEquals("key-1", requestObjectKid("https://verifier.example", "key-1"))
        assertEquals("key-1", requestObjectKid(null, "key-1"))
    }
}
