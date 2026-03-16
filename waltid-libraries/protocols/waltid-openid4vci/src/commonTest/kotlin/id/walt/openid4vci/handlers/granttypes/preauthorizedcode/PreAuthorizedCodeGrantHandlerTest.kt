package id.walt.openid4vci.handlers.granttypes.preauthorizedcode

import id.walt.openid4vci.DefaultClient
import id.walt.openid4vci.DefaultSession
import id.walt.openid4vci.GrantType
import id.walt.openid4vci.StubTokenService
import id.walt.openid4vci.offers.TxCode
import id.walt.openid4vci.responses.token.AccessTokenResponseResult
import id.walt.openid4vci.preauthorized.DefaultPreAuthorizedCodeIssuer
import id.walt.openid4vci.preauthorized.PreAuthorizedCodeIssueRequest
import id.walt.openid4vci.repository.preauthorized.PreAuthorizedCodeRecord
import id.walt.openid4vci.repository.preauthorized.PreAuthorizedCodeRepository
import id.walt.openid4vci.requests.token.AccessTokenRequest
import id.walt.openid4vci.requests.token.DefaultAccessTokenRequest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.time.Duration.Companion.seconds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PreAuthorizedCodeGrantHandlerTest {

    private val repository = InMemoryPreAuthorizedCodeRepository()
    private val handler = PreAuthorizedCodeTokenEndpoint(repository, StubTokenService())
    private val issuer = DefaultPreAuthorizedCodeIssuer(repository)

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `handles pre-authorized code successfully`() = runTest {
        val credentialSession = DefaultSession(subject = "credential-subject")
        val issued = issuer.issue(
            PreAuthorizedCodeIssueRequest(
                clientId = "client-pre",
                scopes = setOf("openid"),
                audience = emptySet(),
                session = credentialSession,
                credentialNonce = "nonce-123",
                credentialNonceExpiresAt = kotlin.time.Clock.System.now() + 600.seconds,
            ),
        )
        val code = issued.code

        val request = createAccessRequestWithGrant(code = code)

        val result = handler.handleTokenEndpointRequest(request)
        assertTrue(result is AccessTokenResponseResult.Success)
        val extra = result.response.extra
        assertNotNull(extra["c_nonce"])
        assertTrue((extra["c_nonce_expires_in"] as? Long ?: 0) >= 0)
        // Request client remains whatever the validator provided; handler internal client selection is not reflected on the immutable request.
        assertNull(repository.get(code))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `rejects invalid tx_code and allows retry`() = runTest {
        val issued = issuer.issue(
            PreAuthorizedCodeIssueRequest(
                clientId = "client-pin",
                txCode = TxCode(length = 4, description = "Enter your transaction code"),
                txCodeValue = "4321",
                session = DefaultSession(subject = "pin-subject"),
            ),
        )
        val code = issued.code

        val firstAttempt = createAccessRequestWithGrant(code = code, txCode = "0000")

        val failure = handler.handleTokenEndpointRequest(firstAttempt)
        assertTrue(failure is AccessTokenResponseResult.Failure)
        assertNotNull(repository.get(code))

        val secondAttempt = createAccessRequestWithGrant(code = code, txCode = "4321")
        val success = handler.handleTokenEndpointRequest(secondAttempt)
        assertTrue(success is AccessTokenResponseResult.Success)
        assertNull(repository.get(code))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `rejects code reuse`() = runTest {
        val issued = issuer.issue(
            PreAuthorizedCodeIssueRequest(
                clientId = "client-reuse",
                session = DefaultSession(subject = "reuse-subject"),
            ),
        )
        val code = issued.code

        val first = createAccessRequestWithGrant(code = code)
        assertTrue(handler.handleTokenEndpointRequest(first) is AccessTokenResponseResult.Success)

        val second = createAccessRequestWithGrant(code = code)
        val failure = handler.handleTokenEndpointRequest(second)
        assertTrue(failure is AccessTokenResponseResult.Failure)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `stores tx_code metadata in the pre-authorized code record`() = runTest {
        val txCode = TxCode(inputMode = "numeric", length = 6, description = "Enter the code")
        val issued = issuer.issue(
            PreAuthorizedCodeIssueRequest(
                clientId = "client-tx-metadata",
                txCode = txCode,
                txCodeValue = "123456",
                session = DefaultSession(subject = "tx-subject"),
            ),
        )

        val record = repository.get(issued.code)
        assertNotNull(record)
        assertEquals(txCode, record.txCode)
        assertNotNull(record.txCodeValue)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `generates tx_code value when metadata is configured without explicit value`() = runTest {
        val txCode = TxCode(inputMode = "numeric", length = 6, description = "Enter the generated code")
        val issued = issuer.issue(
            PreAuthorizedCodeIssueRequest(
                clientId = "client-generated-tx",
                txCode = txCode,
                session = DefaultSession(subject = "generated-tx-subject"),
            )
        )

        assertNotNull(issued.txCodeValue)
        assertEquals(6, issued.txCodeValue.length)
        assertTrue(issued.txCodeValue.all(Char::isDigit))

        val record = repository.get(issued.code)
        assertNotNull(record)
        assertEquals(txCode, record.txCode)
        assertNotNull(record.txCodeValue)

        val request = createAccessRequestWithGrant(code = issued.code, txCode = issued.txCodeValue)
        val result = handler.handleTokenEndpointRequest(request)
        assertTrue(result is AccessTokenResponseResult.Success)
        assertNull(repository.get(issued.code))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `rejects provided tx_code value that does not match metadata`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            issuer.issue(
                PreAuthorizedCodeIssueRequest(
                    clientId = "client-bad-tx",
                    txCode = TxCode(inputMode = "numeric", length = 6, description = "Enter the code"),
                    txCodeValue = "abc123",
                    session = DefaultSession(subject = "bad-tx-subject"),
                ),
            )
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `defaults tx_code input_mode to numeric for provided values`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            issuer.issue(
                PreAuthorizedCodeIssueRequest(
                    clientId = "client-default-numeric",
                    txCode = TxCode(length = 6, description = "Enter the code"),
                    txCodeValue = "12AB34",
                    session = DefaultSession(subject = "default-numeric-subject"),
                ),
            )
        }
    }

    private fun createAccessRequestWithGrant(code: String? = null, txCode: String? = null): AccessTokenRequest =
        DefaultAccessTokenRequest(
            client = DefaultClient(
                id = "",
                redirectUris = emptyList(),
                grantTypes = setOf(GrantType.PreAuthorizedCode.value),
                responseTypes = emptySet(),
            ),
            grantTypes = setOf(GrantType.PreAuthorizedCode.value),
            requestForm = buildMap {
                if (code != null) put("pre-authorized_code", listOf(code))
                if (txCode != null) put("tx_code", listOf(txCode))
            },
            session = DefaultSession(subject = "access-subject"),
        )

    private class InMemoryPreAuthorizedCodeRepository : PreAuthorizedCodeRepository {
        private val records = mutableMapOf<String, PreAuthorizedCodeRecord>()

        override suspend fun save(record: PreAuthorizedCodeRecord) {
            records[record.code] = record
        }

        override suspend fun get(code: String): PreAuthorizedCodeRecord? =
            records[code]

        override suspend fun consume(code: String): PreAuthorizedCodeRecord? {
            val record = records.remove(code) ?: return null
            if (kotlin.time.Clock.System.now() > record.expiresAt) {
                return null
            }
            return record
        }
    }
}