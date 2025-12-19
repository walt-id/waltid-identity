package id.walt.openid4vci.granttypehandlers

import id.walt.openid4vci.DefaultClient
import id.walt.openid4vci.DefaultSession
import id.walt.openid4vci.GRANT_TYPE_PRE_AUTHORIZED_CODE
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
import kotlin.time.ExperimentalTime

class PreAuthorizedCodeTokenHandlerTest {

    private val issuerId = "test-issuer"

    private val repository = InMemoryPreAuthorizedCodeRepository()
    private val handler = PreAuthorizedCodeTokenHandler(repository, StubTokenService())
    private val issuer = DefaultPreAuthorizedCodeIssuer(repository)

    @OptIn(ExperimentalTime::class, ExperimentalCoroutinesApi::class)
    @Test
    fun `handles pre-authorized code successfully`() = runTest {
        val credentialSession = DefaultSession().apply { setSubject("credential-subject") }
        val issued = issuer.issue(
            PreAuthorizedCodeIssueRequest(
                issuerId = issuerId,
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
        request.setIssuerId(issuerId)
        request.getRequestForm().set("pre-authorized_code", code)

        val result = handler.handleTokenEndpointRequest(request)
        assertTrue(result is TokenEndpointResult.Success)
        val success = result
        assertNotNull(success.extra["c_nonce"])
        assertTrue((success.extra["c_nonce_expires_in"] as? Long ?: 0) >= 0)
        assertEquals("credential-subject", request.getSession()?.getSubject())
        assertTrue(request.hasHandledGrantType(GRANT_TYPE_PRE_AUTHORIZED_CODE))
        assertEquals("client-pre", request.getClient().id)
        assertNull(repository.get(code, issuerId))
    }

    @OptIn(ExperimentalTime::class, ExperimentalCoroutinesApi::class)
    @Test
    fun `rejects invalid PIN and allows retry`() = runTest {
        val issued = issuer.issue(
            PreAuthorizedCodeIssueRequest(
                issuerId = issuerId,
                clientId = "client-pin",
                userPinRequired = true,
                userPin = "4321",
                session = DefaultSession(),
            ),
        )
        val code = issued.code

        val firstAttempt = createAccessRequestWithGrant().apply {
            setIssuerId(issuerId)
            getRequestForm().set("pre-authorized_code", code)
            getRequestForm().set("user_pin", "0000")
        }

        val failure = handler.handleTokenEndpointRequest(firstAttempt)
        assertTrue(failure is TokenEndpointResult.Failure)
        assertNotNull(repository.get(code, issuerId))

        val secondAttempt = createAccessRequestWithGrant().apply {
            setIssuerId(issuerId)
            getRequestForm().set("pre-authorized_code", code)
            getRequestForm().set("user_pin", "4321")
        }
        val success = handler.handleTokenEndpointRequest(secondAttempt)
        assertTrue(success is TokenEndpointResult.Success)
        assertNull(repository.get(code, issuerId))
    }

    @OptIn(ExperimentalTime::class, ExperimentalCoroutinesApi::class)
    @Test
    fun `rejects code reuse`() = runTest {
        val issued = issuer.issue(
            PreAuthorizedCodeIssueRequest(
                issuerId = issuerId,
                clientId = "client-reuse",
                userPinRequired = false,
                session = DefaultSession(),
            ),
        )
        val code = issued.code

        val first = createAccessRequestWithGrant().apply {
            setIssuerId(issuerId)
            getRequestForm().set("pre-authorized_code", code)
        }
        assertTrue(handler.handleTokenEndpointRequest(first) is TokenEndpointResult.Success)

        val second = createAccessRequestWithGrant().apply {
            setIssuerId(issuerId)
            getRequestForm().set("pre-authorized_code", code)
        }
        val failure = handler.handleTokenEndpointRequest(second)
        assertTrue(failure is TokenEndpointResult.Failure)
    }

    @OptIn(ExperimentalTime::class)
    private fun createAccessRequestWithGrant(): AccessTokenRequest =
        AccessTokenRequest(session = DefaultSession()).apply {
            setClient(
                DefaultClient(
                    id = "",
                    grantTypes = argumentsOf(GRANT_TYPE_PRE_AUTHORIZED_CODE),
                ),
            )
            appendGrantType(GRANT_TYPE_PRE_AUTHORIZED_CODE)
        }

    private class InMemoryPreAuthorizedCodeRepository : PreAuthorizedCodeRepository {
        private val records = mutableMapOf<Pair<String, String>, PreAuthorizedCodeRecord>()

        override fun save(record: PreAuthorizedCodeRecord, issuerId: String) {
            records[issuerId to record.code] = record
        }

        override fun get(code: String, issuerId: String): PreAuthorizedCodeRecord? =
            records[issuerId to code]

        @OptIn(ExperimentalTime::class)
        override fun consume(code: String, issuerId: String): PreAuthorizedCodeRecord? {
            val record = records.remove(issuerId to code) ?: return null
            if (kotlin.time.Clock.System.now() > record.expiresAt) {
                return null
            }
            return record
        }
    }
}
