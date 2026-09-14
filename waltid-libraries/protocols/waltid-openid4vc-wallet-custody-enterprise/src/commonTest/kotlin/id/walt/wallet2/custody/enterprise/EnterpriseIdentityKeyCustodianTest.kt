package id.walt.wallet2.custody.enterprise

import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.serialization.BinaryData
import id.walt.wallet2.mobile.identity.*
import id.walt.wallet2.persistence.keys.KeyUseAuthorizationPolicy
import id.walt.wallet2.persistence.keys.PlatformKeyFacts
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
    private val identity = WalletIdentity("identity", "key-1", "did:jwk:fixture", "{}",
        IdentityKeyStorage.EncryptedDatabase, KeyUseAuthorizationPolicy.None, PlatformKeyFacts())
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

    @Test fun rejectsMalformedOversizedAndDifferentKeyResponses() = runTest {
        val conflicting = jwk.replace("_owZzgkFGR68KYqSRXklMfJvDOziRgY56Lw5y39waoI", "anebTPlpuKDlOcf2L7PTCtaqj4DjDx0Siq_WiiznLqA")
        for ((body, expected) in listOf("{}" to IdentityProviderFailure.Rejected,
            "!" to IdentityProviderFailure.Rejected, " ".repeat(65537) to IdentityProviderFailure.Rejected,
            """{"key":{"jwk":$conflicting}}""" to IdentityProviderFailure.Conflict)) {
            val client = HttpClient(MockEngine { respond(body) })
            try {
                EnterpriseIdentityKeyCustodian(client, url).use { adapter ->
                    assertEquals(expected, assertFailsWith<IdentityProviderException> { adapter.importKey(identity, key) }.failure)
                }
            } finally { client.close() }
        }
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
