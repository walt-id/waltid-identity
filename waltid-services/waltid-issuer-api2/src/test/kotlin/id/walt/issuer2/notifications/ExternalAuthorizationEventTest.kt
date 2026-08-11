package id.walt.issuer2.notifications

import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.put
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
import id.walt.openid4vci.errors.OAuthErrorCodes
import id.walt.openid4vci.offers.AuthenticationMethod
import id.walt.openid4vci.repository.authorization.InMemoryAuthorizationCodeRepository
import id.walt.openid4vci.repository.par.InMemoryPARRepository
import id.walt.openid4vci.repository.preauthorized.InMemoryPreAuthorizedCodeRepository
import id.walt.openid4vci.repository.refresh.InMemoryRefreshTokenRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

/**
 * Covers the external identity provider callback, which the HTTP-level tests cannot reach because the
 * route sits behind Ktor's `auth-oauth` provider and needs a live Keycloak. The protocol service is
 * exercised directly instead, which is the same code path the route delegates to.
 */
class ExternalAuthorizationEventTest {

    @Test
    fun `unusable id_token from the external provider publishes an authorization failure`() = runTest {
        val sessionId = "external-auth-session"
        val service = protocolService(session(sessionId, externalState = STATE))
        val events = SseNotifier.getSseFlow(sessionId)
        val received = async { withTimeout(2.seconds) { events.first() } }

        // The provider redirected back, but what it returned cannot be decoded. The user authenticated
        // successfully, so this is not a rejected wallet request - see the note on the event name.
        service.processExternalAuthorizationCallback(authServerState = STATE, idToken = "not-a-jws")

        val update = received.await()
        assertEquals(IssuanceSessionEvent.AUTHORIZATION_REQUEST_FAILED.value, update.event)
        // The event name covers six causes, so the error code is the only way to tell them apart.
        assertEquals(
            OAuthErrorCodes.INVALID_REQUEST,
            assertNotNull(update.session["failure"]?.jsonObject)["errorCode"]?.jsonPrimitive?.contentOrNull,
        )
    }

    @Test
    fun `completed external authentication publishes the issued authorization code`() = runTest {
        val sessionId = "authorized-session"
        val service = protocolService(session(sessionId, externalState = STATE))
        val events = SseNotifier.getSseFlow(sessionId)
        val received = async { withTimeout(2.seconds) { events.first() } }

        val idToken = JWKKey.generate(KeyType.secp256r1).signJws(
            buildJsonObject { put("sub", JsonPrimitive("jane@walt.id")) }.toString().encodeToByteArray(),
        )
        service.processExternalAuthorizationCallback(authServerState = STATE, idToken = idToken)

        assertEquals(
            IssuanceSessionEvent.AUTHORIZATION_CODE_ISSUED.value,
            received.await().event,
        )
    }

    @Test
    fun `unknown external state publishes nothing`() = runTest {
        val sessionId = "unmatched-session"
        val service = protocolService(session(sessionId, externalState = STATE))
        val events = SseNotifier.getSseFlow(sessionId)

        service.processExternalAuthorizationCallback(authServerState = "some-other-state", idToken = "not-a-jws")

        // No session resolves from an unknown state, so there is nothing to attribute an event to.
        assertEquals(0, events.replayCache.size)
    }

    private fun session(
        sessionId: String,
        externalState: String,
    ) = IssuanceSession(
        sessionId = sessionId,
        profileId = "test-profile",
        authenticationMethod = AuthenticationMethod.AUTHORIZED,
        credentialConfigurationId = "identity_credential",
        issuerKey = JsonObject(emptyMap()),
        credentialData = buildJsonObject { },
        expiresAt = Clock.System.now() + 1.hours,
        externalAuthorizationState = externalState,
        authorizationRequest = mapOf(
            "response_type" to listOf("code"),
            "client_id" to listOf("demo-client"),
            "redirect_uri" to listOf("https://wallet.example/callback"),
        ),
    )

    private fun protocolService(vararg sessions: IssuanceSession): OpenId4VciProtocolService {
        val serviceConfig = Issuer2ServiceConfig(baseUrl = "http://localhost")
        val metadataConfig = Issuer2MetadataConfig()
        val profileService = CredentialProfileService(
            profilesConfig = Issuer2ProfilesConfig(),
            metadataConfig = metadataConfig,
        )
        val sessionService = IssuanceSessionService(InMemorySessionRepository(sessions.toList()))
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
            credentialNonceService = module.credentialNonceService,
        )
    }

    private class InMemorySessionRepository(initial: List<IssuanceSession>) : IssuanceSessionRepository {
        private val sessions = initial.associateBy { it.sessionId }.toMutableMap()

        override suspend fun save(session: IssuanceSession): IssuanceSession =
            session.also { sessions[it.sessionId] = it }

        override suspend fun get(sessionId: String): IssuanceSession? = sessions[sessionId]
        override suspend fun list(): List<IssuanceSession> = sessions.values.toList()
        override suspend fun remove(sessionId: String) {
            sessions.remove(sessionId)
        }
    }

    private companion object {
        const val STATE = "external-auth-state"
    }
}
