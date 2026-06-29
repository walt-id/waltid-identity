package id.walt.issuer2.openid4vci

import id.walt.issuer2.config.Issuer2MetadataConfig
import id.walt.issuer2.config.Issuer2ProfilesConfig
import id.walt.issuer2.config.Issuer2ServiceConfig
import id.walt.issuer2.domain.IssuanceSession
import id.walt.issuer2.repository.IssuanceSessionRepository
import id.walt.issuer2.service.CredentialProfileService
import id.walt.issuer2.service.IssuanceSessionService
import id.walt.issuer2.service.openid4vci.MetadataService
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class Issuer2MetadataServiceTest {

    @Test
    fun `authorization server metadata defaults to optional PAR`() {
        val metadata = metadataService()
            .getAuthorizationServerMetadata()

        assertEquals("http://localhost/openid4vci/par", metadata.pushedAuthorizationRequestEndpoint)
        assertEquals(false, metadata.requirePushedAuthorizationRequests)
    }

    @Test
    fun `authorization server metadata advertises configured PAR enforcement`() {
        val metadata = metadataService(enforcePushedAuthorizationRequests = true)
            .getAuthorizationServerMetadata()

        assertEquals("http://localhost/openid4vci/par", metadata.pushedAuthorizationRequestEndpoint)
        assertEquals(true, metadata.requirePushedAuthorizationRequests)
    }

    private fun metadataService(enforcePushedAuthorizationRequests: Boolean = false): MetadataService {
        val metadataConfig = Issuer2MetadataConfig()
        return MetadataService(
            serviceConfig = Issuer2ServiceConfig(
                baseUrl = "http://localhost",
                enforcePushedAuthorizationRequests = enforcePushedAuthorizationRequests,
            ),
            metadataConfig = metadataConfig,
            profileService = CredentialProfileService(
                profilesConfig = Issuer2ProfilesConfig(),
                metadataConfig = metadataConfig,
            ),
            sessionService = IssuanceSessionService(NoopIssuanceSessionRepository),
        )
    }

    private object NoopIssuanceSessionRepository : IssuanceSessionRepository {
        override suspend fun save(session: IssuanceSession): IssuanceSession = session
        override suspend fun get(sessionId: String): IssuanceSession? = null
        override suspend fun list(): List<IssuanceSession> = emptyList()
        override suspend fun remove(sessionId: String) = Unit
    }
}