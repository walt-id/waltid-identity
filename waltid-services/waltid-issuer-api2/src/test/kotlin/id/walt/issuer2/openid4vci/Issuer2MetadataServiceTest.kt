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
import id.walt.openid4vci.requests.credential.encryption.CredentialEncryptionProfile
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
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
    fun `credential issuer metadata omits encryption metadata by default`() {
        val metadata = metadataService().getCredentialIssuerMetadata()

        assertNull(metadata.credentialRequestEncryption)
        assertNull(metadata.credentialResponseEncryption)
    }

    @Test
    fun `credential issuer metadata advertises configured credential encryption key`() {
        val metadata = metadataService(
            credentialEncryptionKey = CREDENTIAL_ENCRYPTION_KEY,
        ).getCredentialIssuerMetadata()

        assertCredentialEncryptionMetadata(metadata)
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
        credentialEncryptionKey: String? = null,
    ): MetadataService {
        val metadataConfig = Issuer2MetadataConfig()
        val serviceConfig = Issuer2ServiceConfig(
            baseUrl = "http://localhost",
            credentialEncryptionKey = credentialEncryptionKey,
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

    private fun assertCredentialEncryptionMetadata(metadata: id.walt.openid4vci.metadata.issuer.CredentialIssuerMetadata) {
        val requestEncryption = assertNotNull(metadata.credentialRequestEncryption)
        assertEquals(CredentialEncryptionProfile.encValuesSupported, requestEncryption.encValuesSupported)
        assertFalse(requestEncryption.encryptionRequired)
        assertNull(requestEncryption.zipValuesSupported)

        val key = assertNotNull(requestEncryption.jwks["keys"]?.jsonArray?.singleOrNull()).jsonObject
        assertEquals(CredentialEncryptionProfile.KEY_TYPE_EC, key["kty"]?.jsonPrimitive?.content)
        assertEquals(CredentialEncryptionProfile.CURVE_P256, key["crv"]?.jsonPrimitive?.content)
        assertEquals(CredentialEncryptionProfile.ALG_ECDH_ES, key["alg"]?.jsonPrimitive?.content)
        assertEquals(CredentialEncryptionProfile.KEY_USE_ENC, key["use"]?.jsonPrimitive?.content)
        assertNotNull(key["kid"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() })
        assertNotNull(key["x"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() })
        assertNotNull(key["y"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() })
        assertNull(key["d"])

        val responseEncryption = assertNotNull(metadata.credentialResponseEncryption)
        assertEquals(CredentialEncryptionProfile.responseAlgValuesSupported, responseEncryption.algValuesSupported)
        assertEquals(CredentialEncryptionProfile.encValuesSupported, responseEncryption.encValuesSupported)
        assertFalse(responseEncryption.encryptionRequired)
        assertNull(responseEncryption.zipValuesSupported)
    }

    private object NoopIssuanceSessionRepository : IssuanceSessionRepository {
        override suspend fun save(session: IssuanceSession): IssuanceSession = session
        override suspend fun get(sessionId: String): IssuanceSession? = null
        override suspend fun list(): List<IssuanceSession> = emptyList()
        override suspend fun remove(sessionId: String) = Unit
        override suspend fun take(sessionId: String): IssuanceSession? = null
    }

    private companion object {
        const val CREDENTIAL_ENCRYPTION_KEY =
            """{"type":"jwk","jwk":{"kty":"EC","d":"ZSHgIcRvbwV9s224kHUaFqkEPShCAdwXocGl_w3M42Q","crv":"P-256","kid":"issuer2-credential-encryption-key","x":"GWKpdL3jPoPJ5wKgSA-jxS2jgp-ZUDE6sIQbeB86vF0","y":"F3xAwH96_xVciV7mFQslU_eRQgP-5pSZiNf8bjMoGfo"}}"""
    }
}
