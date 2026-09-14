package id.walt.issuer2.notifications

import id.walt.issuer2.application.openid4vci.OpenId4VciModule
import id.walt.issuer2.config.Issuer2MetadataConfig
import id.walt.issuer2.config.Issuer2ProfilesConfig
import id.walt.issuer2.config.Issuer2ServiceConfig
import id.walt.issuer2.domain.IssuanceSession
import id.walt.issuer2.repository.IssuanceSessionRepository
import id.walt.issuer2.service.CredentialProfileService
import id.walt.issuer2.service.IssuanceSessionService
import id.walt.issuer2.service.openid4vci.MetadataService
import id.walt.issuer2.service.openid4vci.OpenId4VciProtocolService
import id.walt.ktornotifications.SseNotifier
import id.walt.ktornotifications.core.KtorSessionUpdate
import id.walt.openid4vci.errors.OAuthErrorCodes
import id.walt.openid4vci.proofs.CredentialNonceBinding
import id.walt.openid4vci.proofs.CredentialNonceService
import id.walt.openid4vci.proofs.CredentialNonceValidationResult
import id.walt.openid4vci.proofs.IssuedCredentialNonce
import id.walt.openid4vci.repository.authorization.InMemoryAuthorizationCodeRepository
import id.walt.openid4vci.repository.par.InMemoryPARRepository
import id.walt.openid4vci.repository.preauthorized.InMemoryPreAuthorizedCodeRepository
import id.walt.openid4vci.repository.refresh.InMemoryRefreshTokenRepository
import id.walt.openid4vci.responses.nonce.NonceResponseHttp
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class NonceEventTest {

    @Test
    fun `successful nonce request publishes one uncorrelated success event`() = runTest {
        val requestId = "nonce-success"
        val service = protocolService(
            nonceService { IssuedCredentialNonce("signed-nonce") },
        )

        val (response, events) = service.processNonceAndCaptureEvents(requestId)

        assertEquals(200, response.status)
        assertEquals("signed-nonce", response.payload["c_nonce"]?.jsonPrimitive?.content)
        assertEquals("no-store", response.headers["Cache-Control"])
        val event = events.single()
        assertEquals(IssuanceSessionEvent.NONCE_REQUEST_SUCCEEDED.value, event.event)
        assertEquals(requestId, event.target)
        assertEquals(true, event.session.isEmpty())
        assertNull(event.error)
        assertNull(event.errorDescription)
    }

    @Test
    fun `nonce generation failure publishes one sanitized failure event`() = runTest {
        val requestId = "nonce-failure"
        val service = protocolService(
            nonceService { error("Sensitive signing failure") },
        )

        val (response, events) = service.processNonceAndCaptureEvents(requestId)

        assertEquals(500, response.status)
        assertEquals(OAuthErrorCodes.SERVER_ERROR, response.payload["error"]?.jsonPrimitive?.content)
        assertEquals(
            "Nonce request processing failed",
            response.payload["error_description"]?.jsonPrimitive?.content,
        )
        assertEquals("no-store", response.headers["Cache-Control"])
        val event = events.single()
        assertEquals(IssuanceSessionEvent.NONCE_REQUEST_FAILED.value, event.event)
        assertEquals(requestId, event.target)
        assertEquals(true, event.session.isEmpty())
        assertEquals(OAuthErrorCodes.SERVER_ERROR, event.error)
        assertEquals("Nonce request processing failed", event.errorDescription)
    }

    @Test
    fun `nonce request cancellation publishes no event`() = runTest {
        val requestId = "nonce-cancelled"
        val service = protocolService(
            nonceService { throw CancellationException("cancelled") },
        )
        val events = SseNotifier.getSseFlow(IssuanceNotificationService.ISSUER_EVENT_STREAM_TARGET)

        assertFailsWith<CancellationException> {
            service.processNonceRequest(requestId)
        }
        assertEquals(emptyList(), events.replayCache.filter { it.requestId == requestId })
    }

    private suspend fun OpenId4VciProtocolService.processNonceAndCaptureEvents(
        requestId: String,
    ): Pair<NonceResponseHttp, List<KtorSessionUpdate>> {
        val events = SseNotifier.getSseFlow(IssuanceNotificationService.ISSUER_EVENT_STREAM_TARGET)
        val response = processNonceRequest(requestId)
        return response to events.replayCache.filter { it.requestId == requestId }
    }

    private fun nonceService(
        issue: suspend (CredentialNonceBinding) -> IssuedCredentialNonce,
    ): CredentialNonceService = object : CredentialNonceService {
        override suspend fun issue(binding: CredentialNonceBinding): IssuedCredentialNonce = issue(binding)

        override suspend fun validate(
            nonce: String,
            binding: CredentialNonceBinding,
        ): CredentialNonceValidationResult = CredentialNonceValidationResult.INVALID
    }

    private fun protocolService(
        credentialNonceService: CredentialNonceService,
    ): OpenId4VciProtocolService {
        val serviceConfig = Issuer2ServiceConfig(baseUrl = "http://localhost")
        val metadataConfig = Issuer2MetadataConfig()
        val profileService = CredentialProfileService(
            profilesConfig = Issuer2ProfilesConfig(),
            metadataConfig = metadataConfig,
        )
        val sessionService = IssuanceSessionService(EmptySessionRepository)
        val module = OpenId4VciModule.create(
            config = serviceConfig,
            authorizationCodeRepository = InMemoryAuthorizationCodeRepository(),
            preAuthorizedCodeRepository = InMemoryPreAuthorizedCodeRepository(),
            parRepository = InMemoryPARRepository(),
            refreshTokenRepository = InMemoryRefreshTokenRepository(),
        )
        val metadataService = MetadataService(
            serviceConfig = serviceConfig,
            metadataConfig = metadataConfig,
            profileService = profileService,
            sessionService = sessionService,
            preAuthorizedGrantAnonymousAccessSupported = module.preAuthorizedCodeIssuer.anonymousAccessSupported,
        )
        return OpenId4VciProtocolService(
            oauth2Provider = module.oauth2Provider,
            sessionService = sessionService,
            profileService = profileService,
            metadataService = metadataService,
            notificationService = IssuanceNotificationService(),
            credentialNonceService = credentialNonceService,
        )
    }

    private object EmptySessionRepository : IssuanceSessionRepository {
        override suspend fun save(session: IssuanceSession): IssuanceSession = session
        override suspend fun get(sessionId: String): IssuanceSession? = null
        override suspend fun list(): List<IssuanceSession> = emptyList()
        override suspend fun remove(sessionId: String) = Unit
    }
}
