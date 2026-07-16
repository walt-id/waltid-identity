package id.walt.issuer2.openid4vci

import id.walt.issuer2.config.Issuer2MetadataConfig
import id.walt.issuer2.config.Issuer2ProfilesConfig
import id.walt.issuer2.config.Issuer2ServiceConfig
import id.walt.issuer2.domain.IssuanceSession
import id.walt.issuer2.repository.IssuanceSessionRepository
import id.walt.issuer2.service.CredentialProfileService
import id.walt.issuer2.service.IssuanceSessionService
import id.walt.issuer2.service.openid4vci.MetadataService
import id.walt.openid4vci.clientauth.ClientAuthenticationConfig
import id.walt.openid4vci.clientauth.ClientAuthenticationMethodConfig
import id.walt.openid4vci.clientauth.attestation.ClientAttestationSigningAlgorithms
import id.walt.openid4vci.clientauth.attestation.verifier.ClientAttestationVerificationMethod
import id.walt.openid4vci.clientauth.attestation.verifier.ClientAttestationVerifierConfig
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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

    @Test
    fun `authorization server metadata uses pre-authorized anonymous access capability`() {
        val metadata = metadataService(preAuthorizedGrantAnonymousAccessSupported = false)
            .getAuthorizationServerMetadata()

        assertEquals(false, metadata.preAuthorizedGrantAnonymousAccessSupported)
    }

    @Test
    fun `authorization server metadata omits client attestation by default`() {
        val defaultMetadata = metadataService().getAuthorizationServerMetadata()

        assertNull(defaultMetadata.tokenEndpointAuthMethodsSupported)
        assertNull(defaultMetadata.clientAttestationSigningAlgValuesSupported)
        assertNull(defaultMetadata.clientAttestationPopSigningAlgValuesSupported)
        assertNull(Issuer2ServiceConfig(baseUrl = "http://localhost").clientAuthenticationConfig)
    }

    @Test
    fun `authorization server metadata omits client attestation when explicitly disabled`() {
        val disabledMetadata = metadataService(clientAuthenticationConfig = null)
            .getAuthorizationServerMetadata()

        assertNull(disabledMetadata.tokenEndpointAuthMethodsSupported)
        assertNull(disabledMetadata.clientAttestationSigningAlgValuesSupported)
        assertNull(disabledMetadata.clientAttestationPopSigningAlgValuesSupported)
    }

    @Test
    fun `authorization server metadata advertises configured static jwk client attestation`() {
        val attestationMetadata = metadataService(
            clientAuthenticationConfig = ClientAuthenticationConfig(
                supportedMethods = listOf(
                    ClientAuthenticationMethodConfig.ClientAttestation(
                        config = ClientAttestationVerifierConfig(
                            verificationMethod = ClientAttestationVerificationMethod.StaticJwk(
                                jwk = buildJsonObject {
                                    put("kty", "EC")
                                    put("crv", "P-256")
                                    put("x", "x")
                                    put("y", "y")
                                },
                            ),
                        ),
                    ),
                ),
            ),
        ).getAuthorizationServerMetadata()

        assertEquals(setOf("attest_jwt_client_auth"), attestationMetadata.tokenEndpointAuthMethodsSupported)
        assertEquals(
            ClientAttestationSigningAlgorithms.SUPPORTED_JWS_ALGORITHMS,
            attestationMetadata.clientAttestationSigningAlgValuesSupported,
        )
        assertEquals(
            ClientAttestationSigningAlgorithms.SUPPORTED_JWS_ALGORITHMS,
            attestationMetadata.clientAttestationPopSigningAlgValuesSupported,
        )
    }

    @Test
    fun `authorization server metadata advertises configured x509 chain client attestation`() {
        val attestationMetadata = metadataService(
            clientAuthenticationConfig = ClientAuthenticationConfig(
                supportedMethods = listOf(
                    ClientAuthenticationMethodConfig.ClientAttestation(
                        config = ClientAttestationVerifierConfig(
                            verificationMethod = ClientAttestationVerificationMethod.X509Chain(
                                trustedRootCertificatesPem = listOf(
                                    """
                                        -----BEGIN CERTIFICATE-----
                                        MIIB
                                        -----END CERTIFICATE-----
                                    """.trimIndent(),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        ).getAuthorizationServerMetadata()

        assertEquals(setOf("attest_jwt_client_auth"), attestationMetadata.tokenEndpointAuthMethodsSupported)
        assertEquals(
            ClientAttestationSigningAlgorithms.SUPPORTED_JWS_ALGORITHMS,
            attestationMetadata.clientAttestationSigningAlgValuesSupported,
        )
        assertEquals(
            ClientAttestationSigningAlgorithms.SUPPORTED_JWS_ALGORITHMS,
            attestationMetadata.clientAttestationPopSigningAlgValuesSupported,
        )
    }

    private fun metadataService(
        enforcePushedAuthorizationRequests: Boolean = false,
        clientAuthenticationConfig: ClientAuthenticationConfig? = null,
        preAuthorizedGrantAnonymousAccessSupported: Boolean = false,
    ): MetadataService {
        val metadataConfig = Issuer2MetadataConfig()
        val serviceConfig = Issuer2ServiceConfig(
            baseUrl = "http://localhost",
            enforcePushedAuthorizationRequests = enforcePushedAuthorizationRequests,
            clientAuthenticationConfig = clientAuthenticationConfig,
        )
        return MetadataService(
            serviceConfig = serviceConfig,
            metadataConfig = metadataConfig,
            profileService = CredentialProfileService(
                profilesConfig = Issuer2ProfilesConfig(),
                metadataConfig = metadataConfig,
            ),
            sessionService = IssuanceSessionService(NoopIssuanceSessionRepository),
            preAuthorizedGrantAnonymousAccessSupported = preAuthorizedGrantAnonymousAccessSupported,
        )
    }

    private object NoopIssuanceSessionRepository : IssuanceSessionRepository {
        override suspend fun save(session: IssuanceSession): IssuanceSession = session
        override suspend fun get(sessionId: String): IssuanceSession? = null
        override suspend fun list(): List<IssuanceSession> = emptyList()
        override suspend fun remove(sessionId: String) = Unit
        override suspend fun take(sessionId: String): IssuanceSession? = null
    }
}
