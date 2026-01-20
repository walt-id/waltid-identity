package id.walt.openid4vci.handlers.granttypes.preauthorizedcode

import id.walt.openid4vci.DefaultClient
import id.walt.openid4vci.DefaultSession
import id.walt.openid4vci.GrantType
import id.walt.openid4vci.StubTokenService
import id.walt.openid4vci.responses.token.AccessResponseResult
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
                userPinRequired = false,
                userPin = null,
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
        assertTrue(result is AccessResponseResult.Success)
        val extra = result.response.extra
        assertNotNull(extra["c_nonce"])
        assertTrue((extra["c_nonce_expires_in"] as? Long ?: 0) >= 0)
        // Request client remains whatever the validator provided; handler internal client selection is not reflected on the immutable request.
        assertNull(repository.get(code))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `rejects invalid PIN and allows retry`() = runTest {
        val issued = issuer.issue(
            PreAuthorizedCodeIssueRequest(
                clientId = "client-pin",
                userPinRequired = true,
                userPin = "4321",
                session = DefaultSession(subject = "pin-subject"),
            ),
        )
        val code = issued.code

        val firstAttempt = createAccessRequestWithGrant(code = code, userPin = "0000")

        val failure = handler.handleTokenEndpointRequest(firstAttempt)
        assertTrue(failure is AccessResponseResult.Failure)
        assertNotNull(repository.get(code))

        val secondAttempt = createAccessRequestWithGrant(code = code, userPin = "4321")
        val success = handler.handleTokenEndpointRequest(secondAttempt)
        assertTrue(success is AccessResponseResult.Success)
        assertNull(repository.get(code))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `rejects code reuse`() = runTest {
        val issued = issuer.issue(
            PreAuthorizedCodeIssueRequest(
                clientId = "client-reuse",
                userPinRequired = false,
                session = DefaultSession(subject = "reuse-subject"),
            ),
        )
        val code = issued.code

        val first = createAccessRequestWithGrant(code = code)
        assertTrue(handler.handleTokenEndpointRequest(first) is AccessResponseResult.Success)

        val second = createAccessRequestWithGrant(code = code)
        val failure = handler.handleTokenEndpointRequest(second)
        assertTrue(failure is AccessResponseResult.Failure)
    }

        private fun createAccessRequestWithGrant(code: String? = null, userPin: String? = null): AccessTokenRequest =
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
                    if (userPin != null) put("user_pin", listOf(userPin))
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
