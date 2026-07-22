package id.walt.issuer2.openid4vci

import id.walt.issuer2.service.openid4vci.CredentialNonceService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CredentialNonceServiceTest {

    @Test
    fun `issued nonce can be consumed once`() {
        val service = CredentialNonceService()
        val nonce = service.issueNonce()

        assertTrue(service.consumeNonce(nonce))
        assertFalse(service.consumeNonce(nonce))
    }

    @Test
    fun `concurrent nonce consumption has one winner`() = runTest {
        val service = CredentialNonceService()
        val nonce = service.issueNonce()

        val results = List(20) {
            async(Dispatchers.Default) { service.consumeNonce(nonce) }
        }.awaitAll()

        assertEquals(1, results.count { it })
    }
}
