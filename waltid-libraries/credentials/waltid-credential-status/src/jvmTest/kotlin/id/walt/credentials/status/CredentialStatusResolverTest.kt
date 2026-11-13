package id.walt.credentials.status

import id.walt.credentials.formats.W3C11
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.time.Instant
import kotlin.time.Duration.Companion.seconds

class CredentialStatusResolverTest {
    @Test
    fun signedReferencesCannotBeHiddenOrReplacedByTheStoredProjection() = runTest {
        val issuer = StatusTestIssuer.create()
        val credential = assertIs<W3C11>(issuer.credential())
        val requests = mutableListOf<String>()
        val client = HttpClient(MockEngine(MockEngineConfig().apply {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler { request ->
                requests += request.url.toString()
                respond(issuer.list(value = 1u), headers = Headers.build { append(HttpHeaders.ContentType, "application/statuslist+jwt") })
            }
        }))
        val resolver = CredentialStatusResolver(client, backgroundScope, { Instant.fromEpochSeconds(1_800_000_000) })
        try {
            val identity = resolver.discover(credential).entries.single().referenceId
            val hidden = credential.copy(credentialData = JsonObject(credential.credentialData - "status"))
            val replaced = credential.copy(credentialData = JsonObject(credential.credentialData + ("status" to buildJsonObject {
                putJsonObject("status_list") { put("idx", 1); put("uri", "https://wrong-status.invalid/list") }
            })))
            for (projection in listOf(hidden, replaced)) {
                val before = requests.size
                val discovered = resolver.discover(projection)
                assertEquals(1, discovered.entries.size, "A stored projection must not hide an issuer-signed reference")
                assertEquals(identity, discovered.entries.single().referenceId)
                assertEquals(before, requests.size, "Canonical discovery remains local")
                val resolved = resolver.resolve(projection, force = true).metadata.entries.single()
                assertEquals(identity, resolved.referenceId)
                assertEquals(StatusMeaning.INVALID, resolved.meaning)
            }
            assertEquals(listOf(issuer.uri, issuer.uri), requests, "Only the canonical signed reference may be requested")
        } finally { resolver.close(); client.close() }
    }

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
            assertIs<Check.NotChecked>(assertIs<CredentialStatusEntry.Token>(resolver.discover(credential).entries.single()).check)
            assertEquals(0, requests)
            val initial = resolver.resolve(credential).metadata.entries.single()
            assertEquals(StatusMeaning.CLEAR, initial.meaning)
            assertEquals(at + 30.seconds, initial.lastObservation()?.freshUntil)
            at += 1.seconds
            body = issuer.list(value = 1u)
            val cached = resolver.resolve(credential).metadata.entries.single()
            assertEquals(initial.lastObservation()?.checkedAt, cached.lastObservation()?.checkedAt)
            assertEquals(1, requests)
            val forced = resolver.resolve(credential, force = true).metadata.entries.single()
            assertEquals(StatusMeaning.INVALID, forced.meaning)
            assertEquals(at, forced.lastObservation()?.checkedAt)
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
            val beginning = Instant.fromEpochSeconds(1_800_000_010)
            val future = resolver.resolve(issuer.credential(validFrom = beginning.epochSeconds)).metadata.entries.single()
            assertEquals(CheckFailure.NotYetValid(beginning), future.failure)
            assertEquals(0, requests)
            val expired = resolver.resolve(issuer.credential(expiry = 1_700_000_000)).metadata.entries.single()
            assertEquals(CheckFailure.Reason.CREDENTIAL_EXPIRED, expired.failure)
            assertEquals(0, requests)
            val other = StatusTestIssuer.create()
            body = other.list()
            val rejected = resolver.resolve(issuer.credential(), force = true).metadata.entries.single()
            assertEquals(CheckFailure.Reason.UNAUTHORIZED_SIGNER, rejected.failure)
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
            for (index in 0..1) assertEquals(StatusMeaning.CLEAR, resolver.resolve(issuer.credential(index), force = true, session = batch).metadata.entries.single().meaning)
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
            assertEquals(StatusMeaning.CLEAR, resolver.resolve(issuer.w3cCredential()).metadata.entries.single().meaning)
            val twoBit = issuer.w3cCredential(size = 2, reference = JsonArray(listOf(
                JsonPrimitive("https://status.example/explanation"))))
            body = issuer.w3cList(size = 2)
            assertNotNull(resolver.resolve(twoBit, force = true).metadata.entries.single().failure)
            // Two-bit entries need 32KiB, and a signed multi-purpose list can match a non-first purpose.
            body = issuer.w3cList(size = 2, bytes = 32_768, purposes = listOf("suspension", "revocation"), id = null)
            assertEquals(StatusMeaning.CLEAR, resolver.resolve(twoBit, force = true).metadata.entries.single().meaning)
            body = issuer.w3cList(size = 1, bytes = 32_768, declaredSize = 1)
            assertNotNull(resolver.resolve(twoBit, force = true).metadata.entries.single().failure)
            body = issuer.w3cList(size = 2, bytes = 32_768, validUntil = "2026-01-01T00:00:00Z")
            assertEquals(CheckFailure.Reason.INVALID_VALIDITY,
                assertNotNull(resolver.resolve(twoBit, force = true).metadata.entries.single().failure))
            val beforeMalformed = requests
            val malformed = resolver.resolve(issuer.w3cCredential(index = JsonPrimitive(0)), force = true)
            assertEquals(ReferenceProblem.MALFORMED, assertIs<CredentialStatusEntry.Malformed>(malformed.metadata.entries.single()).problem)
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
            assertEquals(StatusMeaning.CLEAR, initial.entries.single { it.lastObservation() != null }.meaning)
            for (invalid in listOf(issuer.w3cList(purpose = "suspension"), issuer.w3cList(id = "https://status.example/another"), issuer.w3cList(bytes = 16))) {
                body = invalid
                assertNotNull(resolver.resolve(credential, force = true).metadata.entries.single { it !is CredentialStatusEntry.Unsupported }.failure)
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
            assertEquals(CheckFailure.Reason.INVALID_VALIDITY, resolver.resolve(issuer.credential(), force = true).metadata.entries.single().failure)
            assertEquals(3, requests)
        } finally { resolver.close(); client.close() }
    }

    @Test
    fun malformedSiblingsRetainUsableReferencesAndAContainerLimitIsNotAbsence() = runTest {
        val issuer = StatusTestIssuer.create()
        val credential = issuer.w3cCredential(includeUnsupported = true, extraEntries = listOf(
            JsonPrimitive("not an entry"), buildJsonObject { put("type", "BitstringStatusListEntry") }))
        var requests = 0
        val client = HttpClient(MockEngine(MockEngineConfig().apply {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler { requests++; respond(issuer.w3cList(), headers = Headers.build { append(HttpHeaders.ContentType, "application/vc+jwt") }) }
        }))
        val resolver = CredentialStatusResolver(client, backgroundScope, { Instant.fromEpochSeconds(1_800_000_000) })
        try {
            val discovered = resolver.discover(credential)
            assertEquals(4, discovered.entries.size)
            assertEquals(2, discovered.entries.filterIsInstance<CredentialStatusEntry.Malformed>().size)
            assertEquals(0, requests)
            val resolved = resolver.resolve(credential).metadata
            assertEquals(StatusMeaning.CLEAR, resolved.entries.single { it is CredentialStatusEntry.Bitstring }.meaning)
            assertEquals(CredentialStatusKind.PARTIAL, resolved.presentation(Instant.fromEpochSeconds(1_800_000_000)).kind)
            assertEquals(1, requests)
            val bounded = CredentialStatusResolver(client, backgroundScope, { Instant.fromEpochSeconds(1_800_000_000) }, CredentialStatusLimits(maxReferencesPerCredential = 1))
            try {
                assertEquals(DiscoveryProblem.RESOURCE_LIMIT, assertIs<CredentialStatus.DiscoveryFailed>(bounded.resolve(credential).metadata).problem)
                val projection = assertIs<W3C11>(credential).copy(credentialData = JsonObject(credential.credentialData +
                    ("credentialStatus" to JsonArray(listOf(credential.credentialData.getValue("credentialStatus").jsonArray.first())))))
                assertEquals(DiscoveryProblem.RESOURCE_LIMIT, assertIs<CredentialStatus.DiscoveryFailed>(bounded.discover(projection)).problem)
                assertEquals(DiscoveryProblem.RESOURCE_LIMIT,
                    assertIs<CredentialStatus.DiscoveryFailed>(bounded.resolve(projection).metadata).problem,
                    "A failure discovered in canonical signed bytes must not become empty success")
                assertEquals(1, requests)
            } finally { bounded.close() }
        } finally { resolver.close(); client.close() }
    }

    @Test
    fun legacyRejectsMultibitEntriesAndDoesNotInterpretRefreshAsBitstringRefresh() = runTest {
        val issuer = StatusTestIssuer.create()
        var requests = 0
        val client = HttpClient(MockEngine(MockEngineConfig().apply {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler { requests++; respond(issuer.w3cList(legacy = true, purpose = "refresh", value = 1u),
                headers = Headers.build { append(HttpHeaders.ContentType, "application/vc+jwt") }) }
        }))
        val resolver = CredentialStatusResolver(client, backgroundScope, { Instant.fromEpochSeconds(1_800_000_000) })
        try {
            assertIs<CredentialStatusEntry.Malformed>(resolver.resolve(issuer.w3cCredential(legacy = true, size = 2)).metadata.entries.single())
            assertEquals(0, requests)
            val resolved = assertIs<CredentialStatusEntry.Legacy>(resolver.resolve(issuer.w3cCredential(legacy = true, purpose = "refresh")).metadata.entries.single())
            assertIs<LegacyPurpose.Extension>(resolved.purpose)
            assertEquals(StatusMeaning.APPLICATION_SPECIFIC, resolved.meaning)
            assertFalse(resolved.irreversible)
            assertEquals(1, requests)
        } finally { resolver.close(); client.close() }
    }

    @Test
    fun reservedSignedTokenValueRemainsReservedAfterAuthenticatedEvaluation() = runTest {
        val issuer = StatusTestIssuer.create()
        val client = HttpClient(MockEngine(MockEngineConfig().apply {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler { respond(issuer.list(value = 4u, bits = 4), headers = Headers.build { append(HttpHeaders.ContentType, "application/statuslist+jwt") }) }
        }))
        val resolver = CredentialStatusResolver(client, backgroundScope, { Instant.fromEpochSeconds(1_800_000_000) })
        try {
            val entry = assertIs<CredentialStatusEntry.Token>(resolver.resolve(issuer.credential()).metadata.entries.single())
            assertEquals(4u.toUByte(), assertIs<TokenStatus.Reserved>(assertNotNull(entry.check.lastObservation()).value).code)
            assertEquals(StatusMeaning.RESERVED, entry.meaning)
        } finally { resolver.close(); client.close() }
    }

}
