package id.walt.credentials.status

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.time.Instant
import kotlin.time.Duration.Companion.seconds

class CredentialStatusResolverTest {
    @Test
    fun readsAreLocalAndForceSeesAChangedSignedList() = runTest {
        val issuer = StatusTestIssuer.create()
        val credential = issuer.credential()
        var body = issuer.list()
        var requests = 0
        var at = Instant.fromEpochSeconds(1_800_000_000)
        val client = HttpClient(MockEngine(MockEngineConfig().apply { dispatcher = StandardTestDispatcher(testScheduler); addHandler {
            requests++
            respond(body, headers = Headers.build {
                append(HttpHeaders.ContentType, "application/statuslist+jwt")
                append(HttpHeaders.CacheControl, "max-age=600")
            })
        } }))
        val resolver = CredentialStatusResolver(client, backgroundScope, { at })
        try {
            assertIs<CredentialStatusEntry.NotChecked>(resolver.discover(credential).entries.single())
            assertEquals(0, requests)
            val initial = assertIs<CredentialStatusEntry.Observed>(resolver.resolve(credential).metadata.entries.single()).observation
            assertEquals(StatusMeaning.CLEAR, initial.meaning)
            assertEquals(at + 30.seconds, initial.freshUntil)
            at += 1.seconds
            body = issuer.list(value = 1u)
            val cached = assertIs<CredentialStatusEntry.Observed>(resolver.resolve(credential).metadata.entries.single()).observation
            assertEquals(initial.checkedAt, cached.checkedAt)
            assertEquals(1, requests)
            val forced = assertIs<CredentialStatusEntry.Observed>(resolver.resolve(credential, force = true).metadata.entries.single()).observation
            assertEquals(StatusMeaning.INVALID, forced.meaning)
            assertEquals(at, forced.checkedAt)
            assertEquals(2, requests)
        } finally { resolver.close(); client.close() }
    }

    @Test
    fun referenceAuthenticationPrecedesAnyStatusRequestAndSignerAuthorizationIsTyped() = runTest {
        val issuer = StatusTestIssuer.create()
        var requests = 0
        var body = issuer.list()
        val client = HttpClient(MockEngine(MockEngineConfig().apply { dispatcher = StandardTestDispatcher(testScheduler); addHandler { requests++; respond(body,
            headers = Headers.build { append(HttpHeaders.ContentType, "application/statuslist+jwt") }) } }))
        val resolver = CredentialStatusResolver(client, backgroundScope, { Instant.fromEpochSeconds(1_800_000_000) })
        try {
            val expired = resolver.resolve(issuer.credential(expiry = 1_700_000_000)).metadata.entries.single()
            assertEquals(StatusFailure.CREDENTIAL_EXPIRED, assertIs<CredentialStatusEntry.Unavailable>(expired).reason)
            assertEquals(0, requests)
            val other = StatusTestIssuer.create()
            body = other.list()
            val rejected = resolver.resolve(issuer.credential(), force = true).metadata.entries.single()
            assertEquals(StatusFailure.UNAUTHORIZED_SIGNER, assertIs<CredentialStatusEntry.Unavailable>(rejected).reason)
            assertEquals(1, requests)
        } finally { resolver.close(); client.close() }
    }

    @Test
    fun concurrentCredentialsShareTheRequestAndNoStoreCannotBeReused() = runTest {
        val issuer = StatusTestIssuer.create()
        val credentials = listOf(issuer.credential(0), issuer.credential(1))
        val body = issuer.list()
        var requests = 0
        val client = HttpClient(MockEngine(MockEngineConfig().apply { dispatcher = StandardTestDispatcher(testScheduler); addHandler {
            requests++; delay(20)
            respond(body, headers = Headers.build {
                append(HttpHeaders.ContentType, "application/statuslist+jwt")
                append(HttpHeaders.CacheControl, "no-store")
            })
        } }))
        val resolver = CredentialStatusResolver(client, backgroundScope, { Instant.fromEpochSeconds(1_800_000_000) })
        try {
            val first = async { resolver.resolve(credentials[0], force = true) }
            val second = async { resolver.resolve(credentials[1], force = true) }
            assertEquals(1, first.await().nonPersistentReferenceIds.size)
            assertEquals(1, second.await().nonPersistentReferenceIds.size)
            assertEquals(1, requests)
            resolver.resolve(credentials[0])
            assertEquals(2, requests)
        } finally { resolver.close(); client.close() }
    }
    @Test
    fun forceBatchReusesAnImmediateNoStoreResponseOnlyInsideThatBatch() = runTest {
        val issuer = StatusTestIssuer.create()
        val body = issuer.list()
        var requests = 0
        val client = HttpClient(MockEngine(MockEngineConfig().apply {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler { requests++; respond(body, headers = Headers.build {
                append(HttpHeaders.ContentType, "application/statuslist+jwt")
                append(HttpHeaders.CacheControl, "no-store")
            }) }
        }))
        val resolver = CredentialStatusResolver(client, backgroundScope, { Instant.fromEpochSeconds(1_800_000_000) })
        try {
            val batch = resolver.newRefreshSession()
            for (index in 0..1) assertIs<CredentialStatusEntry.Observed>(resolver.resolve(issuer.credential(index), force = true, session = batch).metadata.entries.single())
            assertEquals(1, requests)
            batch.close()
            assertFailsWith<IllegalStateException> { resolver.resolve(issuer.credential(), force = true, session = batch) }
            val next = resolver.newRefreshSession()
            try { resolver.resolve(issuer.credential(), force = true, session = next) } finally { next.close() }
            assertEquals(2, requests)
        } finally { resolver.close(); client.close() }
    }

    @Test
    fun multibitMinimumAppliesToCachedBytesAndEverySignedValidityBoundStillApplies() = runTest {
        val issuer = StatusTestIssuer.create()
        var body = issuer.w3cList()
        var requests = 0
        val client = HttpClient(MockEngine(MockEngineConfig().apply {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler { requests++; respond(body, headers = Headers.build { append(HttpHeaders.ContentType, "application/vc+jwt") }) }
        }))
        val at = Instant.fromEpochSeconds(1_800_000_000)
        val resolver = CredentialStatusResolver(client, backgroundScope, { at })
        try {
            // Warm the expanded-byte cache with the valid one-bit 16KiB list.
            assertEquals(StatusMeaning.CLEAR, resolver.resolve(issuer.w3cCredential()).metadata.entries.single().lastObservation()?.meaning)
            val twoBit = issuer.w3cCredential(size = 2, reference = kotlinx.serialization.json.JsonArray(listOf(
                kotlinx.serialization.json.JsonPrimitive("https://status.example/explanation"))))
            body = issuer.w3cList(size = 2)
            assertIs<CredentialStatusEntry.Unavailable>(resolver.resolve(twoBit, force = true).metadata.entries.single())
            // Two-bit entries need 32KiB, and a signed multi-purpose list can match a non-first purpose.
            body = issuer.w3cList(size = 2, bytes = 32_768, purposes = listOf("suspension", "revocation"), id = null)
            assertEquals(StatusMeaning.CLEAR, resolver.resolve(twoBit, force = true).metadata.entries.single().lastObservation()?.meaning)
            body = issuer.w3cList(size = 1, bytes = 32_768, declaredSize = 1)
            assertIs<CredentialStatusEntry.Unavailable>(resolver.resolve(twoBit, force = true).metadata.entries.single())
            body = issuer.w3cList(size = 2, bytes = 32_768, validUntil = "2026-01-01T00:00:00Z")
            assertEquals(StatusFailure.INVALID_VALIDITY,
                assertIs<CredentialStatusEntry.Unavailable>(resolver.resolve(twoBit, force = true).metadata.entries.single()).reason)
            val beforeMalformed = requests
            val malformed = resolver.resolve(issuer.w3cCredential(index = kotlinx.serialization.json.JsonPrimitive(0)), force = true)
            assertEquals(StatusFailure.REFERENCE_MALFORMED, assertIs<CredentialStatusEntry.Unavailable>(malformed.metadata.entries.single()).reason)
            assertEquals(beforeMalformed, requests, "Malformed local references must not request an issuer list")
        } finally { resolver.close(); client.close() }
    }

    @Test
    fun signedBitstringListEnforcesPurposeIdentityAndMinimumSizeAndKeepsPartialCoverage() = runTest {
        val issuer = StatusTestIssuer.create()
        var body = issuer.w3cList()
        val client = HttpClient(MockEngine(MockEngineConfig().apply {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler { respond(body, headers = Headers.build { append(HttpHeaders.ContentType, "application/vc+jwt") }) }
        }))
        val resolver = CredentialStatusResolver(client, backgroundScope, { Instant.fromEpochSeconds(1_800_000_000) })
        try {
            val credential = issuer.w3cCredential(includeUnsupported = true)
            val initial = resolver.resolve(credential).metadata
            assertEquals(CredentialStatusKind.PARTIAL, initial.presentation(Instant.fromEpochSeconds(1_800_000_000)).kind)
            assertEquals(StatusMeaning.CLEAR, initial.entries.mapNotNull { it.lastObservation() }.single().meaning)
            for (invalid in listOf(issuer.w3cList(purpose = "suspension"), issuer.w3cList(id = "https://status.example/another"), issuer.w3cList(bytes = 16))) {
                body = invalid
                assertIs<CredentialStatusEntry.Unavailable>(resolver.resolve(credential, force = true).metadata.entries.single { it !is CredentialStatusEntry.Unsupported })
            }
            body = issuer.w3cList(value = 1u)
            val revoked = resolver.resolve(credential, force = true).metadata.presentation(Instant.fromEpochSeconds(1_800_000_000))
            assertEquals(CredentialStatusKind.REVOKED, revoked.kind)
            assertEquals(CredentialStatusCoverage.PARTIAL, revoked.coverage)
        } finally { resolver.close(); client.close() }
    }

    @Test
    fun conditionalRevalidationCannotExtendASignedExpiration() = runTest {
        val issuer = StatusTestIssuer.create()
        val body = issuer.list(expiry = 1_800_000_010, ttl = 600)
        var at = Instant.fromEpochSeconds(1_800_000_000)
        var requests = 0
        val client = HttpClient(MockEngine(MockEngineConfig().apply {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler { request -> requests++
                if (requests == 1) respond(body, headers = Headers.build {
                    append(HttpHeaders.ContentType, "application/statuslist+jwt"); append(HttpHeaders.ETag, "v1"); append(HttpHeaders.CacheControl, "max-age=600")
                }) else {
                    assertEquals("v1", request.headers[HttpHeaders.IfNoneMatch])
                    respond("", HttpStatusCode.NotModified, Headers.build { append(HttpHeaders.CacheControl, "max-age=600") })
                }
            }
        }))
        val resolver = CredentialStatusResolver(client, backgroundScope, { at })
        try {
            assertEquals(Instant.fromEpochSeconds(1_800_000_010), resolver.resolve(issuer.credential()).metadata.entries.single().lastObservation()?.freshUntil)
            at += 5.seconds
            val checked = resolver.resolve(issuer.credential(), force = true).metadata.entries.single().lastObservation()
            assertEquals(at, checked?.checkedAt)
            assertEquals(Instant.fromEpochSeconds(1_800_000_010), checked?.freshUntil)
            at += 5.seconds
            assertEquals(StatusFailure.INVALID_VALIDITY, assertIs<CredentialStatusEntry.Unavailable>(resolver.resolve(issuer.credential(), force = true).metadata.entries.single()).reason)
            assertEquals(3, requests)
        } finally { resolver.close(); client.close() }
    }

}
