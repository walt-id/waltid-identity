package id.walt.openid4vp.clientidprefix

import id.walt.crypto.keys.KeyType
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RequestObjectJwsAlgorithmTest {

    @Test
    fun `declared JWS algorithm must match certificate public key type`() {
        assertTrue(requestObjectAlgorithmMatchesKey("ES256", KeyType.secp256r1))
        assertTrue(requestObjectAlgorithmMatchesKey("ES384", KeyType.secp384r1))
        assertTrue(requestObjectAlgorithmMatchesKey("ES512", KeyType.secp521r1))
        assertTrue(requestObjectAlgorithmMatchesKey("EdDSA", KeyType.Ed25519))

        assertFalse(requestObjectAlgorithmMatchesKey("ES384", KeyType.secp256r1))
        assertFalse(requestObjectAlgorithmMatchesKey("ES256", KeyType.secp384r1))
        assertFalse(requestObjectAlgorithmMatchesKey(null, KeyType.secp256r1))
    }
}
