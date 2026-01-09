package id.walt.openid4vci.granttypehandlers

import id.walt.openid4vci.DefaultClient
import id.walt.openid4vci.DefaultSession
import id.walt.openid4vci.GrantType
import id.walt.openid4vci.StubTokenService
import id.walt.openid4vci.TokenEndpointResult
import id.walt.openid4vci.argumentsOf
import id.walt.openid4vci.preauthorized.DefaultPreAuthorizedCodeIssuer
import id.walt.openid4vci.preauthorized.PreAuthorizedCodeIssueRequest
import id.walt.openid4vci.repository.preauthorized.PreAuthorizedCodeRecord
import id.walt.openid4vci.repository.preauthorized.PreAuthorizedCodeRepository
import id.walt.openid4vci.request.AccessTokenRequest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.time.Duration.Companion.seconds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PreAuthorizedCodeTokenHandlerTest {

    private val repository = InMemoryPreAuthorizedCodeRepository()
    private val handler = PreAuthorizedCodeTokenHandler(repository, StubTokenService())
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

        val request = createAccessRequestWithGrant()
        request.getRequestForm().set("pre-authorized_code", code)

        val result = handler.handleTokenEndpointRequest(request)
        assertTrue(result is TokenEndpointResult.Success)
        assertNotNull(result.extra["c_nonce"])
        assertTrue((result.extra["c_nonce_expires_in"] as? Long ?: 0) >= 0)
        assertEquals("credential-subject", request.getSession()?.getSubject())
        assertTrue(request.hasHandledGrantType(GrantType.PreAuthorizedCode.value))
        assertEquals("client-pre", request.getClient().id)
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

        val firstAttempt = createAccessRequestWithGrant().apply {
            getRequestForm().set("pre-authorized_code", code)
            getRequestForm().set("user_pin", "0000")
        }

        val failure = handler.handleTokenEndpointRequest(firstAttempt)
        assertTrue(failure is TokenEndpointResult.Failure)
        assertNotNull(repository.get(code))

        val secondAttempt = createAccessRequestWithGrant().apply {
            getRequestForm().set("pre-authorized_code", code)
            getRequestForm().set("user_pin", "4321")
        }
        val success = handler.handleTokenEndpointRequest(secondAttempt)
        assertTrue(success is TokenEndpointResult.Success)
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

        val first = createAccessRequestWithGrant().apply {
            getRequestForm().set("pre-authorized_code", code)
        }
        assertTrue(handler.handleTokenEndpointRequest(first) is TokenEndpointResult.Success)

        val second = createAccessRequestWithGrant().apply {
            getRequestForm().set("pre-authorized_code", code)
        }
        val failure = handler.handleTokenEndpointRequest(second)
        assertTrue(failure is TokenEndpointResult.Failure)
    }

        private fun createAccessRequestWithGrant(): AccessTokenRequest =
            AccessTokenRequest(session = DefaultSession(subject = "access-subject")).apply {
            setClient(
                DefaultClient(
                    id = "",
                    grantTypes = argumentsOf(GrantType.PreAuthorizedCode.value),
                ),
            )
            appendGrantType(GrantType.PreAuthorizedCode.value)
        }

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
