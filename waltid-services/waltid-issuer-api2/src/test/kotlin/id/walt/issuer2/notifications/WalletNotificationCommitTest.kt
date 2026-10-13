package id.walt.issuer2.notifications

import id.walt.issuer2.application.openid4vci.OpenId4VciModule
import id.walt.issuer2.config.Issuer2MetadataConfig
import id.walt.issuer2.config.Issuer2ProfilesConfig
import id.walt.issuer2.config.Issuer2ServiceConfig
import id.walt.issuer2.domain.IssuanceRequest
import id.walt.issuer2.domain.IssuanceResult
import id.walt.issuer2.domain.IssuanceSession
import id.walt.issuer2.domain.IssuanceSessionStatus
import id.walt.issuer2.repository.IssuanceSessionRepository
import id.walt.issuer2.service.CredentialProfileService
import id.walt.issuer2.service.IssuanceSessionService
import id.walt.issuer2.service.openid4vci.MetadataService
import id.walt.issuer2.service.openid4vci.OpenId4VciProtocolService
import id.walt.ktornotifications.SseNotifier
import id.walt.openid4vci.core.OAuth2Provider
import id.walt.openid4vci.offers.AuthenticationMethod
import id.walt.openid4vci.repository.authorization.InMemoryAuthorizationCodeRepository
import id.walt.openid4vci.repository.par.InMemoryPARRepository
import id.walt.openid4vci.repository.preauthorized.InMemoryPreAuthorizedCodeRepository
import id.walt.openid4vci.repository.refresh.InMemoryRefreshTokenRepository
import id.walt.openid4vci.requests.notification.*
import id.walt.openid4vci.tokens.access.ProtectedResourceAccessTokenContext
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test

class WalletNotificationCommitTest {
    @Test
    fun `cancellation as committed save returns never restores the old snapshot`() = runTest {
        val repository = RecordingRepository()
        val service = protocolService(repository)
        lateinit var request: Job
        repository.afterSave = { request.cancel() }
        request = launch {
            service.processNotificationRequest(listOf("Bearer token"), emptyList(), "{}", "notification-cancel")
        }
        request.join()
        assertEquals(true, request.isCancelled)
        assertCommitted(repository)
    }

    @Test
    fun `post commit event failure never restores the old snapshot`() = runTest {
        val repository = RecordingRepository()
        val service = protocolService(repository)
        // The notifier rejects an empty request id after the changed record was saved.
        assertFailsWith<IllegalArgumentException> {
            service.processNotificationRequest(listOf("Bearer token"), emptyList(), "{}", "")
        }
        assertCommitted(repository)
        val retry = service.processNotificationRequest(listOf("Bearer token"), emptyList(), "{}", "retry")
        assertEquals(204, retry.status)
        val events = SseNotifier.getSseFlow(IssuanceNotificationService.ISSUER_EVENT_STREAM_TARGET)
        assertEquals(emptyList(), events.replayCache.filter { it.requestId == "retry" })
    }

    @Test
    fun `batch notifications respect narrowed tokens and established selection`() = runTest {
        val grant = Json.parseToJsonElement("""{"sub":"session","authorization_details":[{"type":"openid_credential","credential_configuration_id":"configuration","credential_identifiers":["credential"]}]}""").jsonObject
        for ((claims, selected, expectedStatus) in listOf(
            Triple(buildJsonObject { put("sub", "session") }, listOf("credential"), 400),
            Triple(grant, listOf("other"), 400),
            Triple(grant, listOf("credential", "other"), 204),
        )) {
            val repository = RecordingRepository()
            val original = assertNotNull(repository.current)
            repository.current = original.copy(
                issuanceRequests = original.issuanceRequests + original.issuanceRequests.single().copy(credentialIdentifier = "other"),
                issuanceResults = original.issuanceResults + ("other" to IssuanceResult("dc+sd-jwt", IssuedCredentialNotification("other", "other-notification"))),
                authorizedCredentialIdentifiers = selected,
            )
            val response = protocolService(repository, claims).processNotificationRequest(listOf("Bearer token"), emptyList(), "{}", "batch-notification")
            assertEquals(expectedStatus, response.status)
            val saved = assertNotNull(repository.current)
            assertNull(saved.issuanceResults.getValue("other").walletNotification?.event)
            assertEquals(if (expectedStatus == 204) NotificationEvent.CREDENTIAL_ACCEPTED else null,
                saved.issuanceResults.getValue("credential").walletNotification?.event)
        }
    }

    private fun assertCommitted(repository: RecordingRepository) {
        assertEquals(1, repository.saves.size)
        val saved = assertNotNull(repository.current)
        assertEquals(NotificationEvent.CREDENTIAL_ACCEPTED, saved.issuanceResults.getValue("credential").walletNotification?.event)
        assertEquals("stored", saved.issuanceResults.getValue("credential").walletNotification?.eventDescription)
        assertEquals(IssuanceSessionStatus.SUCCESSFUL, saved.status)
    }

    private fun protocolService(
        repository: IssuanceSessionRepository,
        claims: JsonObject = buildJsonObject { put("sub", "session") },
    ): OpenId4VciProtocolService {
        val serviceConfig = Issuer2ServiceConfig(baseUrl = "http://localhost")
        val metadataConfig = Issuer2MetadataConfig()
        val profileService = CredentialProfileService(
            profilesConfig = Issuer2ProfilesConfig(),
            metadataConfig = metadataConfig,
        )
        val sessionService = IssuanceSessionService(repository)
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
            oauth2Provider = object : OAuth2Provider by module.oauth2Provider {
                override suspend fun createNotificationRequest(
                    parameters: Map<String, List<String>>,
                    accessTokenContext: ProtectedResourceAccessTokenContext,
                ) = NotificationRequestResult.Success(
                    DefaultNotificationRequest("notification", NotificationEvent.CREDENTIAL_ACCEPTED, "stored"),
                    claims,
                )
            },
            sessionService = sessionService,
            profileService = profileService,
            metadataService = metadataService,
            notificationService = IssuanceNotificationService(),
            credentialNonceService = module.credentialNonceService,
        )
    }

    private class RecordingRepository : IssuanceSessionRepository {
        var current: IssuanceSession? = IssuanceSession(
            sessionId = "session",
            authenticationMethod = AuthenticationMethod.PRE_AUTHORIZED,
            issuanceRequests = listOf(IssuanceRequest(
                credentialIdentifier = "credential", profileId = "profile", credentialConfigurationId = "configuration",
                issuerKey = JsonObject(emptyMap()), credentialData = JsonObject(emptyMap()),
            )),
            issuanceResults = mapOf("credential" to IssuanceResult("dc+sd-jwt", IssuedCredentialNotification("credential", "notification"))),
            expiresAt = Instant.DISTANT_FUTURE,
            status = IssuanceSessionStatus.SUCCESSFUL,
        )
        val saves = mutableListOf<IssuanceSession>()
        var afterSave: () -> Unit = {}
        override suspend fun save(session: IssuanceSession): IssuanceSession {
            current = session
            saves += session
            afterSave()
            return session
        }
        override suspend fun get(sessionId: String) = current
        override suspend fun list() = listOfNotNull(current)
        override suspend fun remove(sessionId: String) { current = null }
        override suspend fun take(sessionId: String) = current.also { current = null }
    }
}
