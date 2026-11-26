package id.walt.issuer2.openid4vci

import id.walt.commons.config.ConfigManager
import id.walt.commons.logging.LoggingManager
import id.walt.commons.persistence.InMemoryPersistence
import id.walt.issuer2.application.openid4vci.OpenId4VciModule
import id.walt.issuer2.config.*
import id.walt.issuer2.controller.openapi.Issuer2RequestExamples
import id.walt.issuer2.domain.IssuanceSession
import id.walt.issuer2.notifications.IssuanceNotificationService
import id.walt.issuer2.repository.*
import id.walt.issuer2.service.*
import id.walt.issuer2.service.openid4vci.*
import id.walt.issuer2.testsupport.*
import id.walt.openid4vci.repository.authorization.InMemoryAuthorizationCodeRepository
import id.walt.openid4vci.repository.par.InMemoryPARRepository
import id.walt.openid4vci.repository.preauthorized.InMemoryPreAuthorizedCodeRepository
import id.walt.openid4vci.repository.refresh.InMemoryRefreshTokenRepository
import io.klogging.Level
import io.klogging.config.loggingConfiguration
import io.klogging.events.LogEvent
import io.klogging.sending.EventSender
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.*
import kotlin.time.Duration
import org.junit.jupiter.api.parallel.ResourceLock

/**
 * A session store outage during a Credential Request is the issuer's failure, not the wallet's token. It used to
 * be answered as `invalid_token` with the store client's message (for Redis: "Unexpected end of stream."), which
 * told the wallet to discard a valid token, disclosed the store's error, and logged nothing. It must now reach the
 * application's error handling, which logs it under an errorId and answers with a generic 500.
 */
class SessionStoreFailureDisclosureTest {

    private val catalogues = mutableListOf<IssuerTypeMetadataCatalogue>()

    @AfterEach
    fun cleanup() {
        catalogues.forEach { it.close() }
        clearIssuer2TestEnvironment()
    }

    @Test
    @ResourceLock("KloggingConfiguration")
    fun `a session store failure is not reported as an invalid token`() = runTest {
        loadIssuer2ConfigFiles()
        val serviceConfig = ConfigManager.getConfig<Issuer2ServiceConfig>()
        val metadataConfig = ConfigManager.getConfig<Issuer2MetadataConfig>()
        val profiles = CredentialProfileService(ConfigManager.getConfig<Issuer2ProfilesConfig>(), metadataConfig)
        val backing = ConfiguredIssuanceSessionRepository()
        var storeDown = false
        val storeFailure = RuntimeException("redis://secret-host:6379 Unexpected end of stream.")
        val repository = object : IssuanceSessionRepository by backing {
            override suspend fun get(sessionId: String): IssuanceSession? =
                if (storeDown) throw storeFailure else backing.get(sessionId)
        }
        val sessions = IssuanceSessionService(repository)
        val typeMetadata = IssuerTypeMetadataCatalogue(
            serviceConfig, metadataConfig, InMemoryPersistence("types", Duration.INFINITE),
        ).also(catalogues::add)
        val module = OpenId4VciModule.create(
            serviceConfig, InMemoryAuthorizationCodeRepository(), InMemoryPreAuthorizedCodeRepository(),
            InMemoryPARRepository(), InMemoryRefreshTokenRepository(), metadataResolver = typeMetadata,
        )
        val metadata = MetadataService(
            serviceConfig, metadataConfig, profiles, sessions, typeMetadata,
            preAuthorizedGrantAnonymousAccessSupported = module.preAuthorizedCodeIssuer.anonymousAccessSupported,
        )
        val notifications = IssuanceNotificationService()
        val protocol = OpenId4VciProtocolService(
            oauth2Provider = module.oauth2Provider, sessionService = sessions, profileService = profiles,
            metadataService = metadata, notificationService = notifications, credentialNonceService = module.credentialNonceService,
        )
        val offers = CredentialOfferService(profiles, sessions, module.preAuthorizedCodeIssuer, serviceConfig, notifications, typeMetadata)
        val receipt = offers.createCredentialOffer(Issuer2RequestExamples.PROFILE_DEFERRED_OFFER.copy(profileId = "identityCredentialSdJwt"), "create")
        val offer = assertNotNull(offers.getCredentialOffer(receipt.offerId, "resolve"))
        val token = protocol.processTokenRequest(mapOf(
            "grant_type" to listOf("urn:ietf:params:oauth:grant-type:pre-authorized_code"),
            "pre-authorized_code" to listOf(assertNotNull(offer.grants?.preAuthorizedCode?.preAuthorizedCode)),
        ), requestId = "token").payload.getValue("access_token").jsonPrimitive.content

        storeDown = true
        val failures = LinkedBlockingQueue<LogEvent>()
        loggingConfiguration(append = true) {
            sink("credential-failure-test", EventSender { batch ->
                failures.addAll(batch.filter { it.message.contains("Credential issuance failed (requestId=credential") })
            })
            logging {
                fromMinLevel(Level.ERROR) { toSink("credential-failure-test") }
            }
        }
        try {
            val response = protocol.processCredentialRequest(
                listOf("Bearer $token"), emptyList(),
                buildJsonObject { put("credential_configuration_id", offer.credentialConfigurationIds.single()) },
                "credential",
            )
            // invalid_token would tell the wallet to discard a valid token; the store's message names its host.
            assertEquals(500, response.status)
            assertEquals("server_error", response.payload.getValue("error").jsonPrimitive.content)
            val description = response.payload.getValue("error_description").jsonPrimitive.content
            val errorId = UUID.fromString(description.substringAfter("errorId=").removeSuffix(")"))
            assertEquals("Credential request processing failed (errorId=$errorId)", description)
            val logged = assertNotNull(failures.poll(5, TimeUnit.SECONDS))
            assertContains(logged.message, "errorId=$errorId")
            val trace = assertNotNull(logged.stackTrace)
            assertContains(trace, storeFailure.message!!)
            assertContains(trace, SessionStoreFailureDisclosureTest::class.simpleName!!)
        } finally {
            loggingConfiguration(append = false, LoggingManager.loggingSetup.config)
        }
    }
}
