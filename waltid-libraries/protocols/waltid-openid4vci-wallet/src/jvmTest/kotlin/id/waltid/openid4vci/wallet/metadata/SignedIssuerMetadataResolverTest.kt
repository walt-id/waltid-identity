package id.waltid.openid4vci.wallet.metadata

import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.openid4vci.CredentialFormat
import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import id.walt.openid4vci.metadata.issuer.CredentialIssuerMetadata
import id.walt.openid4vci.metadata.issuer.CredentialIssuerMetadataJwt
import id.walt.openid4vci.metadata.issuer.toSignedJwt
import id.walt.openid4vci.tokens.jwt.JwtHeaderParams
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SignedIssuerMetadataResolverTest {
    @Test
    fun trustedSignedMetadataRetainsTheExactJwtAndSigner() = runTest {
        val issuer = "https://issuer.example"
        val key = JWKKey.generate(KeyType.Ed25519)
        val jwt = CredentialIssuerMetadata(
            credentialIssuer = issuer,
            credentialEndpoint = "$issuer/credential",
            credentialConfigurationsSupported = mapOf("pid" to CredentialConfiguration(CredentialFormat.SD_JWT_VC)),
        ).toSignedJwt(key)
        val client = HttpClient(MockEngine) {
            engine {
                addHandler {
                    respond(jwt, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/jwt"))
                }
            }
        }
        val resolved = IssuerMetadataResolver(client) { compactJwt, expectedIssuer ->
            key.getPublicKey().verifyJws(compactJwt).getOrThrow()
            assertEquals(issuer, expectedIssuer)
            MetadataSigner(key.getKeyId(), "EdDSA", MetadataSignerTrustType.TRUSTED_ISSUER)
        }.resolveCredentialIssuerMetadata(issuer)

        val signed = assertIs<ResolvedCredentialIssuerMetadata.Signed>(resolved)
        assertEquals(jwt, signed.compactJwt)
        assertEquals(issuer, signed.metadata.credentialIssuer)
    }

    @Test
    fun trustResolverRunsBeforeMalformedPayloadClaimsAreRead() = runTest {
        val issuer = "https://issuer.example"
        val key = JWKKey.generate(KeyType.Ed25519)
        val jwt = key.signJws(
            """{"sub":7,"iat":"not-a-number"}""".encodeToByteArray(),
            headers = mapOf(JwtHeaderParams.TYPE to JsonPrimitive(CredentialIssuerMetadataJwt.TYPE)),
        )
        var trustResolverCalled = false
        val client = HttpClient(MockEngine) {
            engine {
                addHandler {
                    respond(jwt, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/jwt"))
                }
            }
        }

        assertFailsWith<Exception> {
            IssuerMetadataResolver(client) { _, _ ->
                trustResolverCalled = true
                throw IllegalStateException("untrusted signer")
            }.resolveCredentialIssuerMetadata(issuer)
        }

        assertTrue(trustResolverCalled)
    }
}
