package id.waltid.openid4vc.wallet.legacy

import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.openid4vci.CredentialFormat
import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import id.walt.openid4vci.metadata.issuer.CredentialIssuerMetadata
import id.walt.openid4vci.metadata.issuer.toSignedJwt
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class LegacyIssuerMetadataResolverTest {
    private val issuer = "https://issuer.example"
    private val credentialIssuerMetadata = CredentialIssuerMetadata(
        credentialIssuer = issuer,
        credentialEndpoint = "$issuer/credential",
        credentialConfigurationsSupported = mapOf(
            "pid" to CredentialConfiguration(CredentialFormat.SD_JWT_VC),
        ),
    )

    @Test
    fun projectsUnsignedModernMetadataToLegacyProviderMetadata() = runTest {
        val metadata = LegacyIssuerMetadataResolver(client(credentialIssuerMetadataJson())).resolve(issuer)

        assertEquals(issuer, metadata.credentialIssuer)
        assertEquals("$issuer/token", metadata.tokenEndpoint)
        assertEquals("$issuer/credential", metadata.credentialEndpoint)
        assertEquals(CredentialFormat.SD_JWT_VC.value, metadata.credentialConfigurationsSupported?.get("pid")?.format?.value)
    }

    @Test
    fun verifiesConfiguredSignerBeforeProjectingSignedMetadata() = runTest {
        val key = JWKKey.generate(KeyType.Ed25519)
        val jwt = credentialIssuerMetadata.toSignedJwt(key)
        val resolver = LegacyIssuerMetadataResolver(
            client(jwt, signed = true),
            ConfiguredIssuerMetadataTrustResolver(
                listOf(
                    TrustedIssuerMetadataSigner(
                        issuer = issuer,
                        publicJwk = key.getPublicKey().exportJWK(),
                        keyId = key.getKeyId(),
                        algorithm = "EdDSA",
                    ),
                ),
            ),
        )

        val metadata = resolver.resolve(issuer)

        assertEquals(issuer, metadata.credentialIssuer)
        assertNotNull(metadata.credentialConfigurationsSupported?.get("pid"))
    }

    @Test
    fun rejectsSignedMetadataWithoutConfiguredSigner() = runTest {
        val key = JWKKey.generate(KeyType.Ed25519)
        val jwt = credentialIssuerMetadata.toSignedJwt(key)

        assertFailsWith<Exception> {
            LegacyIssuerMetadataResolver(client(jwt, signed = true)).resolve(issuer)
        }
    }

    private fun credentialIssuerMetadataJson() = Json.encodeToString(
        CredentialIssuerMetadata.serializer(),
        credentialIssuerMetadata,
    )

    private fun client(metadataBody: String, signed: Boolean = false): HttpClient = HttpClient(MockEngine) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        engine {
            addHandler { request ->
                if (request.url.encodedPath.endsWith("openid-credential-issuer")) {
                    respond(
                        content = metadataBody,
                        status = HttpStatusCode.OK,
                        headers = io.ktor.http.headersOf(
                            "Content-Type",
                            if (signed) "application/jwt" else ContentType.Application.Json.toString(),
                        ),
                    )
                } else {
                    respond(
                        content = authorizationServerMetadataJson(),
                        status = HttpStatusCode.OK,
                        headers = io.ktor.http.headersOf("Content-Type", ContentType.Application.Json.toString()),
                    )
                }
            }
        }
    }

    private fun authorizationServerMetadataJson() = """
        {
          "issuer": "$issuer",
          "token_endpoint": "$issuer/token",
          "response_types_supported": ["code"],
          "grant_types_supported": ["urn:ietf:params:oauth:grant-type:pre-authorized_code"]
        }
    """.trimIndent()
}
