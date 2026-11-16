package id.walt.statuslist.token

import id.walt.statuslist.codec.TokenStatusListCodec
import kotlinx.serialization.json.*
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class IetfStatusListTokenTest {
    private val uri = "https://attester.example/status/1"
    private val issued = Instant.fromEpochSeconds(1_800_000_000)
    private val header = buildJsonObject { put("typ", "statuslist+jwt") }

    private fun payload(change: JsonObjectBuilder.() -> Unit = {}): JsonObject = buildJsonObject {
        put("sub", uri)
        put("iat", issued.epochSeconds)
        put("exp", issued.epochSeconds + 600)
        put("ttl", 60)
        putJsonObject("status_list") { put("bits", 2); put("lst", TokenStatusListCodec.encode(byteArrayOf(0x24))) }
        change()
    }

    private fun token(payload: JsonObject = payload(), type: String = "statuslist+jwt") =
        IetfStatusListToken.fromJwt(buildJsonObject { put("typ", type) }, payload, uri)

    @Test fun readsValidInvalidAndSuspendedEntries() {
        val list = token()
        assertEquals(2, list.bits)
        assertEquals(0u, list.readStatus(0))
        assertEquals(1u, list.readStatus(1))
        assertEquals(2u, list.readStatus(2))
    }

    @Test fun tokenTypeUsesJoseMediaTypeNormalization() {
        for (type in listOf("statuslist+jwt", "application/statuslist+jwt", "STATUSLIST+JWT", "Application/StatusList+JWT")) {
            assertEquals(1u, token(type = type).readStatus(1), type)
        }
    }

    @Test fun rejectsMissingAndOtherTokenTypes() {
        assertFailsWith<IllegalArgumentException> { IetfStatusListToken.fromJwt(JsonObject(emptyMap()), payload(), uri) }
        for (type in listOf("JWT", "statuslist+cwt", "text/statuslist+jwt", "statuslist+jwt;foo=bar", " statuslist+jwt")) {
            assertFailsWith<IllegalArgumentException>(type) { token(type = type) }
        }
    }

    @Test fun subjectMustBeTheExactReference() {
        for (value in listOf(JsonPrimitive("$uri/"), JsonPrimitive(uri.uppercase()), JsonNull, JsonPrimitive(1))) {
            assertFailsWith<IllegalArgumentException> { token(payload { put("sub", value) }) }
        }
        assertFailsWith<IllegalArgumentException> { token(JsonObject(payload() - "sub")) }
    }

    @Test fun issuanceTimeIsRequired() {
        assertFailsWith<IllegalArgumentException> { token(JsonObject(payload() - "iat")) }
    }

    @Test fun numericClaimsRejectStringsNullsAndNonFiniteValues() {
        for (claim in listOf("iat", "exp", "nbf", "ttl")) {
            for (bad in listOf(JsonPrimitive("1800000000"), JsonNull, JsonPrimitive(true), JsonArray(emptyList()),
                JsonPrimitive(Double.NaN), JsonPrimitive(Double.POSITIVE_INFINITY), JsonPrimitive(Double.NEGATIVE_INFINITY))) {
                assertFailsWith<IllegalArgumentException>("$claim=$bad") { token(payload { put(claim, bad) }) }
            }
        }
    }

    @Test fun expiryMustFollowIssuanceAndTtlMustBePositive() {
        for (exp in listOf(issued.epochSeconds, issued.epochSeconds - 1)) {
            assertFailsWith<IllegalArgumentException> { token(payload { put("exp", exp) }) }
        }
        for (ttl in listOf(0, -1)) {
            assertFailsWith<IllegalArgumentException> { token(payload { put("ttl", ttl) }) }
        }
    }

    @Test fun optionalTimeClaimsMayBeAbsent() {
        val list = token(JsonObject(payload() - setOf("exp", "nbf", "ttl")))
        assertTrue(list.validity.isValidAt(issued + 3600.seconds, issued))
        assertFalse(list.validity.isValidAt(issued + 3600.seconds, issued, maximumAgeSeconds = 300))
    }

    @Test fun expiryIsExclusiveAndRetainsFractions() {
        val list = token(payload { put("exp", issued.epochSeconds + 0.5) })
        for (skew in listOf(0L, 60L)) {
            assertTrue(list.validity.isValidAt(issued + 499.milliseconds, issued, clockSkewSeconds = skew))
            assertFalse(list.validity.isValidAt(issued + 500.milliseconds, issued, clockSkewSeconds = skew))
        }
    }

    @Test fun issuanceAndNotBeforeRejectFutureUseWithoutTruncation() {
        val list = token(payload { put("iat", issued.epochSeconds + 0.5); put("nbf", issued.epochSeconds + 0.75) })
        assertFalse(list.validity.isValidAt(issued + 499.milliseconds, issued))
        assertFalse(list.validity.isValidAt(issued + 749.milliseconds, issued))
        assertTrue(list.validity.isValidAt(issued + 750.milliseconds, issued))
    }

    @Test fun explicitClockSkewAcceptsASlightlyAheadProvider() {
        val list = token(payload { put("iat", issued.epochSeconds + 1); put("nbf", issued.epochSeconds + 1) })
        assertFalse(list.validity.isValidAt(issued, issued))
        assertTrue(list.validity.isValidAt(issued, issued, clockSkewSeconds = 60))
    }

    @Test fun clockSkewBoundaryIsInclusiveAndPreservesFractionalDates() {
        for (claim in listOf("iat", "nbf")) {
            val list = token(payload { put(claim, issued.epochSeconds + 60.5) })
            assertFalse(list.validity.isValidAt(issued + 499.milliseconds, issued, clockSkewSeconds = 60), claim)
            assertTrue(list.validity.isValidAt(issued + 500.milliseconds, issued, clockSkewSeconds = 60), claim)
        }
    }

    @Test fun clockSkewCannotBeNegative() {
        assertFailsWith<IllegalArgumentException> { token().validity.isValidAt(issued, issued, clockSkewSeconds = -1) }
    }

    @Test fun ttlStartsAtResolutionAndDoesNotReplaceSignedExpiry() {
        val list = token(payload { put("ttl", 0.5) })
        val resolved = issued + 100.seconds
        for (skew in listOf(0L, 60L)) {
            assertTrue(list.validity.isValidAt(resolved + 499.milliseconds, resolved, clockSkewSeconds = skew))
            assertFalse(list.validity.isValidAt(resolved + 500.milliseconds, resolved, clockSkewSeconds = skew))
            assertFalse(list.validity.isValidAt(issued + 600.seconds, issued + 600.seconds, clockSkewSeconds = skew))
        }
    }

    @Test fun maximumAgeIsIndependentAndItsBoundaryIsInclusive() {
        val list = token(payload { put("ttl", 1000) })
        for (skew in listOf(0L, 60L)) {
            assertTrue(list.validity.isValidAt(issued + 300.seconds, issued, 300, clockSkewSeconds = skew))
            assertFalse(list.validity.isValidAt(issued + 300.seconds + 1.milliseconds, issued, 300, clockSkewSeconds = skew))
        }
        assertFailsWith<IllegalArgumentException> { list.validity.isValidAt(issued, issued, 0) }
    }

    @Test fun supportsEachBitWidthAndPreservesOtherStatusValues() {
        for (bits in listOf(1, 2, 4, 8)) {
            val list = token(payload { putJsonObject("status_list") {
                put("bits", bits); put("lst", TokenStatusListCodec.encode(byteArrayOf(0xff.toByte())))
            } })
            assertEquals((1u shl bits) - 1u, list.readStatus(0))
        }
    }

    @Test fun rejectsUnsupportedOrNonIntegerBitWidths() {
        for (bits in listOf(JsonPrimitive(0), JsonPrimitive(3), JsonPrimitive(16), JsonPrimitive("2"), JsonPrimitive(2.5), JsonNull)) {
            assertFailsWith<IllegalArgumentException> { token(payload { putJsonObject("status_list") {
                put("bits", bits); put("lst", "eJwDAAAAAAE")
            } }) }
        }
    }

    @Test fun indicesCannotEscapeTheDecodedList() {
        val list = token()
        assertFailsWith<IllegalArgumentException> { list.readStatus(-1) }
        assertFailsWith<IndexOutOfBoundsException> { list.readStatus(4) }
        assertFailsWith<IndexOutOfBoundsException> { list.readStatus(Int.MAX_VALUE) }
    }

    @Test fun requiresAnEncodedListWithUnpaddedBase64url() {
        assertFailsWith<IllegalArgumentException> { token(JsonObject(payload() - "status_list")) }
        for (encoded in listOf("", "a+b", "a/b", "abcd=", "a b")) {
            assertFailsWith<IllegalArgumentException> { token(payload { putJsonObject("status_list") {
                put("bits", 2); put("lst", encoded)
            } }) }
        }
    }

    @Test fun decompressionIsBoundedByTheConsumer() {
        val payload = payload { putJsonObject("status_list") {
            put("bits", 2); put("lst", TokenStatusListCodec.encode(ByteArray(4096)))
        } }
        assertFailsWith<IllegalArgumentException> { IetfStatusListToken.fromJwt(header, payload, uri, maximumOutputBytes = 4095) }
        val list = IetfStatusListToken.fromJwt(header, payload, uri, maximumOutputBytes = 4096)
        assertEquals(0u, list.readStatus(16383))
        assertFailsWith<IndexOutOfBoundsException> { list.readStatus(16384) }
    }
}
