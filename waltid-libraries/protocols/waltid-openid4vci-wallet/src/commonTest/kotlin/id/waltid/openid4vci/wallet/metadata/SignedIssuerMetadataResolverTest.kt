package id.waltid.openid4vci.wallet.metadata

import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.Base64Utils.decodeFromBase64Url
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.openid4vci.CredentialFormat
import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import id.walt.openid4vci.metadata.issuer.CredentialIssuerMetadata
import id.walt.openid4vci.metadata.issuer.CredentialIssuerMetadataJwt
import id.walt.openid4vci.metadata.issuer.toSignedJwt
import id.walt.openid4vci.tokens.jwt.JwtHeaderParams
import id.walt.openid4vci.tokens.jwt.JwtPayloadClaims
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SignedIssuerMetadataResolverTest {
    private val issuer = "https://issuer.example"

    @Test
    fun trustedSignedMetadataRetainsTheExactJwtAndSignerAndAdvertisesJwt() = runTest {
        val key = JWKKey.generate(KeyType.Ed25519)
        val jwt = validMetadata().toSignedJwt(key)
        var accept: String? = null

        val resolved = IssuerMetadataResolver(signedClient(jwt) { accept = it }) { compactJwt, expectedIssuer ->
            key.getPublicKey().verifyJws(compactJwt).getOrThrow()
            assertEquals(issuer, expectedIssuer)
            MetadataSigner(key.getKeyId(), "EdDSA", MetadataSignerTrustType.TRUSTED_ISSUER)
        }.resolveCredentialIssuerMetadata(issuer)

        val signed = assertIs<ResolvedCredentialIssuerMetadata.Signed>(resolved)
        assertEquals("application/jwt, application/json", accept)
        assertEquals(jwt, signed.compactJwt)
        assertEquals(issuer, signed.metadata.credentialIssuer)
        assertEquals(
            MetadataSigner(key.getKeyId(), "EdDSA", MetadataSignerTrustType.TRUSTED_ISSUER),
            signed.signer,
        )
    }

    @Test
    fun rejectedTrustResolverRejectsSignedMetadata() = runTest {
        val key = JWKKey.generate(KeyType.Ed25519)
        val jwt = validMetadata().toSignedJwt(key)

        assertFailsWith<Exception> {
            IssuerMetadataResolver(signedClient(jwt)) { _, _ -> error("untrusted signer") }
                .resolveCredentialIssuerMetadata(issuer)
        }
    }

    @Test
    fun malformedOrExpiredExpirationRejectsSignedMetadata() = runTest {
        val key = JWKKey.generate(KeyType.Ed25519)
        listOf(
            JsonPrimitive("tomorrow"),
            JsonPrimitive(Clock.System.now().epochSeconds),
            JsonPrimitive(Clock.System.now().epochSeconds - 1),
        ).forEach { expiry ->
            val jwt = key.signJws(
                payload(subject = issuer, expiry = expiry).toString().encodeToByteArray(),
                headers = signedMetadataHeaders(),
            )

            assertFailsWith<Exception> {
                trustedResolver(key, jwt).resolveCredentialIssuerMetadata(issuer)
            }
        }
    }

    @Test
    fun quotedNumericClaimsAndMalformedOptionalIssuerAreRejected() = runTest {
        val key = JWKKey.generate(KeyType.Ed25519)
        listOf(
            payload(issuedAt = JsonPrimitive(Clock.System.now().epochSeconds.toString())),
            payload(expiry = JsonPrimitive((Clock.System.now().epochSeconds + 60).toString())),
            payload(signedIssuer = JsonPrimitive(7)),
        ).forEach { claims ->
            val jwt = key.signJws(claims.toString().encodeToByteArray(), signedMetadataHeaders())

            assertFailsWith<Exception> {
                trustedResolver(key, jwt).resolveCredentialIssuerMetadata(issuer)
            }
        }
    }

    @Test
    fun invalidTypeOrDisallowedAlgorithmRejectsBeforeTrust() = runTest {
        val key = JWKKey.generate(KeyType.Ed25519)
        listOf(
            key.signJws(payload().toString().encodeToByteArray(), mapOf(JwtHeaderParams.TYPE to JsonPrimitive("wrong"))),
            key.signJws(payload().toString().encodeToByteArray(), signedMetadataHeaders()),
        ).forEachIndexed { index, jwt ->
            var trustCalled = false
            val candidate = if (index == 0) jwt else jwt.withAlgorithm("none")
            assertFailsWith<Exception> {
                IssuerMetadataResolver(signedClient(candidate)) { _, _ ->
                    trustCalled = true
                    MetadataSigner(null, "EdDSA", MetadataSignerTrustType.TRUSTED_ISSUER)
                }.resolveCredentialIssuerMetadata(issuer)
            }
            assertTrue(!trustCalled)
        }
    }

    @Test
    fun issuerAndSubjectMismatchRejectsSignedMetadata() = runTest {
        val key = JWKKey.generate(KeyType.Ed25519)
        val subjectMismatch = key.signJws(
            payload(subject = "https://other.example").toString().encodeToByteArray(),
            signedMetadataHeaders(),
        )
        val issuerMismatch = key.signJws(
            payload(metadataIssuer = "https://other.example").toString().encodeToByteArray(),
            signedMetadataHeaders(),
        )

        listOf(subjectMismatch, issuerMismatch).forEach { jwt ->
            assertFailsWith<Exception> {
                trustedResolver(key, jwt).resolveCredentialIssuerMetadata(issuer)
            }
        }
    }

    @Test
    fun trustResolverRunsBeforeMalformedPayloadClaimsAreRead() = runTest {
        val key = JWKKey.generate(KeyType.Ed25519)
        val jwt = key.signJws(
            """{"sub":7,"iat":"not-a-number"}""".encodeToByteArray(),
            signedMetadataHeaders(),
        )
        var trustResolverCalled = false

        assertFailsWith<Exception> {
            IssuerMetadataResolver(signedClient(jwt)) { _, _ ->
                trustResolverCalled = true
                error("untrusted signer")
            }.resolveCredentialIssuerMetadata(issuer)
        }

        assertTrue(trustResolverCalled)
    }

    private fun validMetadata() = CredentialIssuerMetadata(
        credentialIssuer = issuer,
        credentialEndpoint = "$issuer/credential",
        credentialConfigurationsSupported = mapOf("pid" to CredentialConfiguration(CredentialFormat.SD_JWT_VC)),
    )

    private fun payload(
        metadataIssuer: String = issuer,
        subject: String = issuer,
        issuedAt: JsonPrimitive = JsonPrimitive(Clock.System.now().epochSeconds),
        expiry: JsonPrimitive? = null,
        signedIssuer: JsonPrimitive? = null,
    ) = buildJsonObject {
        put("credential_issuer", metadataIssuer)
        put("credential_endpoint", "$metadataIssuer/credential")
        put("credential_configurations_supported", buildJsonObject { })
        put(JwtPayloadClaims.SUBJECT, subject)
        put(JwtPayloadClaims.ISSUED_AT, issuedAt)
        expiry?.let { put(JwtPayloadClaims.EXPIRATION, it) }
        signedIssuer?.let { put(JwtPayloadClaims.ISSUER, it) }
    }

    private fun signedMetadataHeaders() = mapOf(
        JwtHeaderParams.TYPE to JsonPrimitive(CredentialIssuerMetadataJwt.TYPE),
    )

    private fun signedClient(jwt: String, onRequest: (String?) -> Unit = {}): HttpClient = HttpClient(MockEngine) {
        engine {
            addHandler { request ->
                onRequest(request.headers[HttpHeaders.Accept])
                respond(jwt, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/jwt"))
            }
        }
    }

    private fun trustedResolver(key: JWKKey, jwt: String) = IssuerMetadataResolver(signedClient(jwt)) { compactJwt, _ ->
        key.getPublicKey().verifyJws(compactJwt).getOrThrow()
        MetadataSigner(key.getKeyId(), "EdDSA", MetadataSignerTrustType.TRUSTED_ISSUER)
    }

    private fun String.withAlgorithm(algorithm: String): String {
        val parts = split('.')
        val header = parts[0].decodeFromBase64Url().decodeToString()
            .replace("\"alg\":\"EdDSA\"", "\"alg\":\"$algorithm\"")
            .encodeToByteArray()
            .encodeToBase64Url()
        return "$header.${parts[1]}.${parts[2]}"
    }
}
