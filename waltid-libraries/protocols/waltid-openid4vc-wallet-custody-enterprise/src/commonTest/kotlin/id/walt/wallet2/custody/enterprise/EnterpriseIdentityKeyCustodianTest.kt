package id.walt.wallet2.custody.enterprise

import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.toSpkiDer
import id.walt.crypto2.serialization.BinaryData
import id.walt.wallet2.mobile.identity.*
import id.walt.crypto2.keys.KeyUseAuthorizationPolicy
import id.walt.crypto2.keys.PlatformKeyFacts
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.request.get
import io.ktor.http.*
import io.ktor.http.content.TextContent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class EnterpriseIdentityKeyCustodianTest {
    private val jwk = """{"kty":"EC","crv":"P-256","x":"_owZzgkFGR68KYqSRXklMfJvDOziRgY56Lw5y39waoI","y":"anebTPlpuKDlOcf2L7PTCtaqj4DjDx0Siq_WiiznLqA","d":"885_2uV-GjENh_HrvebzKL4Kmc28rfTWWJzyneS4_9I"}"""
    private val key get() = EncodedKey.Jwk(BinaryData(jwk.encodeToByteArray()), true)
    private val identity = SigningIdentity("identity", "key-1", "did:jwk:fixture", "{}",
        SigningIdentityKeyStorage.EncryptedDatabase, KeyUseAuthorizationPolicy.None, PlatformKeyFacts())
    private val url = Url("https://enterprise.example/v1/org.kms")

    @Test fun importsOriginalKeyAndReturnsOnlyPublicReceipt() = runTest {
        val client = HttpClient(MockEngine { request ->
            assertEquals("https://enterprise.example/v1/org.kms.key-1/kms-service-api/keys/import/jwk", request.url.toString())
            assertEquals(HttpMethod.Post, request.method)
            assertEquals(jwk, (request.body as TextContent).text)
            respond("""{"key":{"type":"jwk","jwk":$jwk}}""", HttpStatusCode.Created)
        })
        try {
            EnterpriseIdentityKeyCustodian(client, url).use { adapter ->
                repeat(2) {
                    val receipt = adapter.importKey(identity, key)
                    assertEquals("https://enterprise.example/v1/org.kms.key-1", receipt.keyReference)
                    assertFalse(receipt.publicJwk.contains("\"d\""))
                    assertTrue(receipt.publicJwk.contains("_owZzg"))
                }
            }
        } finally { client.close() }
    }

    @Test fun categorizesFailuresWithoutReturningResponseSecretsOrFollowingRedirects() = runTest {
        val cases = mapOf(301 to IdentityProviderFailure.Rejected, 401 to IdentityProviderFailure.InteractionRequired,
            403 to IdentityProviderFailure.Rejected, 409 to IdentityProviderFailure.Conflict,
            429 to IdentityProviderFailure.TemporarilyUnavailable, 503 to IdentityProviderFailure.TemporarilyUnavailable)
        for ((status, expected) in cases) {
            var requests = 0
            val client = HttpClient(MockEngine {
                requests++
                respond("private response must not escape", HttpStatusCode.fromValue(status), headersOf(HttpHeaders.Location, "https://elsewhere.example"))
            })
            try {
                EnterpriseIdentityKeyCustodian(client, url).use { adapter ->
                    val error = assertFailsWith<IdentityProviderException> { adapter.importKey(identity, key) }
                    assertEquals(expected, error.failure)
                    assertFalse(error.message.orEmpty().contains("private response"))
                    assertEquals(1, requests)
                }
            } finally { client.close() }
        }
    }

    @Test fun rejectsMalformedResponses() = runTest {
        for (body in listOf("{}", "!", """{"key":{"jwk":{"kty":"EC","crv":"P-256"}}}""")) {
            assertResponseFailure(body, IdentityProviderFailure.Rejected)
        }
    }

    @Test fun rejectsOversizedResponses() = runTest {
        assertResponseFailure(" ".repeat(65537), IdentityProviderFailure.Rejected)
    }

    @Test fun reportsConflictForADifferentValidKey() = runTest {
        // The P-256 generator is a valid public key. Changing just one coordinate can create
        // an invalid point that native providers reject before the adapter compares keys.
        val differentJwk = """{"kty":"EC","crv":"P-256","x":"axfR8uEsQkf4vOblY6RA8ncDfYEt6zOg9KE5RdiYwpY","y":"T-NC4v4af5uO5-tKfA-eFivOM1drMV7Oy7ZAaDe_UfU"}"""
        val differentKey = EncodedKey.Jwk(BinaryData(differentJwk.encodeToByteArray()), false)
        val spec = KeySpec.Ec(EcCurve.P256)
        assertNotEquals(key.toSpkiDer(spec), differentKey.toSpkiDer(spec))
        assertResponseFailure("""{"key":{"jwk":$differentJwk}}""", IdentityProviderFailure.Conflict)
    }

    private suspend fun assertResponseFailure(body: String, expected: IdentityProviderFailure) {
        val client = HttpClient(MockEngine { respond(body) })
        try {
            EnterpriseIdentityKeyCustodian(client, url).use { adapter ->
                assertEquals(expected, assertFailsWith<IdentityProviderException> { adapter.importKey(identity, key) }.failure)
            }
        } finally { client.close() }
    }

    @Test fun validatesDestinationAndPreservesHostClientOwnership() = runTest {
        val client = HttpClient(MockEngine { respond("ok") })
        try {
            for (invalid in listOf("http://enterprise.example/v1/kms", "https://user:secret@enterprise.example/v1/kms",
                "https://enterprise.example/v1/kms?token=x", "https://enterprise.example/v1/kms/")) {
                assertFailsWith<IllegalArgumentException> { EnterpriseIdentityKeyCustodian(client, Url(invalid)) }
            }
            EnterpriseIdentityKeyCustodian(client, url).close()
            assertEquals(HttpStatusCode.OK, client.get("https://enterprise.example/health").status)
        } finally { client.close() }
    }

    @Test fun cancellationIsNotConvertedToRetryableProviderFailure() = runTest {
        val client = HttpClient(MockEngine { throw CancellationException("test cancellation") })
        try {
            EnterpriseIdentityKeyCustodian(client, url).use { adapter ->
                assertFailsWith<CancellationException> { adapter.importKey(identity, key) }
            }
        } finally { client.close() }
    }
}
