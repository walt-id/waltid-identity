package id.walt.policies2.vc

import id.walt.credentials.CredentialParser
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.policies2.vc.policies.ExpirationDatePolicy
import id.walt.policies2.vc.policies.NotBeforePolicy
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.add
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class WrappedJwtDatePolicyTest {
    private suspend fun credential(
        exp: Long? = null,
        nbf: Long? = null,
        iat: Long? = null,
        innerExpiry: String? = null,
        innerStart: String? = null,
        v2: Boolean = false,
    ) = JWKKey.generate(KeyType.Ed25519).let { key ->
        val payload = buildJsonObject {
            put("iss", "https://issuer.example")
            exp?.let { put("exp", it) }
            nbf?.let { put("nbf", it) }
            iat?.let { put("iat", it) }
            putJsonObject("vc") {
                putJsonArray("@context") {
                    add(if (v2) "https://www.w3.org/ns/credentials/v2" else "https://www.w3.org/2018/credentials/v1")
                }
                putJsonArray("type") { add("VerifiableCredential") }
                put("issuer", "https://issuer.example")
                innerExpiry?.let { put(if (v2) "validUntil" else "expirationDate", it) }
                innerStart?.let { put(if (v2) "validFrom" else "issuanceDate", it) }
                putJsonObject("credentialSubject") { put("id", "did:example:holder") }
            }
        }
        val jwt = key.signJws(payload.toString().encodeToByteArray())
        assertTrue(key.verifyJws(jwt).isSuccess)
        CredentialParser.parseOnly(jwt)
    }

    @Test
    fun issuedAtRemainsFallbackWhenStartDatesArePresent() = runTest {
        val policy = NotBeforePolicy()
        assertTrue(policy.verify(credential(iat = 4102444800, nbf = 1577836800,
            innerStart = "2020-01-01T00:00:00Z")).isSuccess)
        assertTrue(policy.verify(credential(iat = 1577836800, nbf = 4102444800,
            innerStart = "2020-01-01T00:00:00Z")).isFailure)
        assertTrue(policy.verify(credential(iat = 4102444800)).isFailure)
        assertTrue(policy.verify(credential(iat = 1577836800)).isSuccess)
    }

    @Test
    fun exactExpirationInstantIsExpiredForJwtAndIsoDates() = runTest {
        val instant = Instant.parse("2030-01-01T00:00:00Z")
        val clock = object : Clock { override fun now() = instant }
        val expiration = ExpirationDatePolicy(clock = clock)
        assertTrue(expiration.verify(credential(exp = instant.epochSeconds)).isFailure)
        assertTrue(expiration.verify(credential(innerExpiry = instant.toString())).isFailure)
        assertTrue(expiration.verify(credential(exp = instant.epochSeconds + 1)).isSuccess)
        assertTrue(NotBeforePolicy(clock = clock).verify(credential(nbf = instant.epochSeconds)).isSuccess)
        val serialized = Json.encodeToString(ExpirationDatePolicy(requireField = true, clock = clock))
        assertFalse("clock" in serialized)
        assertTrue(Json.decodeFromString<ExpirationDatePolicy>(serialized).verify(credential()).isFailure)
    }

    @Test
    fun successPayloadReportsStrictestValidityDate() = runTest {
        val instant = Instant.parse("2030-01-01T00:00:00Z")
        val clock = object : Clock { override fun now() = instant }
        val expiration = ExpirationDatePolicy(clock = clock)
        val notBefore = NotBeforePolicy(clock = clock)
        val earlierExpiry = Instant.parse("2035-01-01T00:00:00Z").epochSeconds
        val laterStart = Instant.parse("2025-01-01T00:00:00Z").epochSeconds
        for (v2 in listOf(false, true)) {
            val credential = credential(exp = earlierExpiry, nbf = laterStart,
                innerExpiry = "2040-01-01T00:00:00Z", innerStart = "2020-01-01T00:00:00Z", v2 = v2)
            assertEquals(JsonPrimitive(earlierExpiry), expiration.verify(credential).getOrThrow().jsonObject["date_seconds"])
            assertEquals(JsonPrimitive(laterStart), notBefore.verify(credential).getOrThrow().jsonObject["date_seconds"])
        }
    }

    @Test
    fun outerOnlyDatesAreChecked() = runTest {
        val valid = credential(exp = 4102444800, nbf = 1577836800)
        for (result in listOf(ExpirationDatePolicy().verify(valid), NotBeforePolicy().verify(valid))) {
            assertTrue(result.isSuccess)
            assertEquals(JsonPrimitive(true), result.getOrThrow().jsonObject["claim_checked"])
        }
        assertTrue(ExpirationDatePolicy().verify(credential(exp = 1577836800)).isFailure)
        assertTrue(NotBeforePolicy().verify(credential(nbf = 4102444800)).isFailure)
        assertTrue(ExpirationDatePolicy(requireField = true).verify(valid).isSuccess)
        assertTrue(NotBeforePolicy(requireField = true).verify(valid).isSuccess)
    }

    @Test
    fun innerAndOuterDatesMustBothPass() = runTest {
        for (v2 in listOf(false, true)) {
            assertTrue(ExpirationDatePolicy().verify(credential(
                exp = 1577836800, innerExpiry = "2100-01-01T00:00:00Z", v2 = v2,
            )).isFailure)
            assertTrue(NotBeforePolicy().verify(credential(
                nbf = 4102444800, innerStart = "2020-01-01T00:00:00Z", v2 = v2,
            )).isFailure)
            assertTrue(ExpirationDatePolicy().verify(credential(
                exp = 4102444800, innerExpiry = "2020-01-01T00:00:00Z", v2 = v2,
            )).isFailure)
            assertTrue(NotBeforePolicy().verify(credential(
                nbf = 1577836800, innerStart = "2100-01-01T00:00:00Z", v2 = v2,
            )).isFailure)
            val valid = credential(
                exp = 4102444800, nbf = 1577836800,
                innerExpiry = "2100-01-01T00:00:00Z", innerStart = "2020-01-01T00:00:00Z", v2 = v2,
            )
            assertTrue(ExpirationDatePolicy().verify(valid).isSuccess)
            assertTrue(NotBeforePolicy().verify(valid).isSuccess)
        }
    }

    @Test
    fun absentDatesRespectRequireField() = runTest {
        val undated = credential()
        for (result in listOf(ExpirationDatePolicy().verify(undated), NotBeforePolicy().verify(undated))) {
            assertTrue(result.isSuccess)
            assertEquals(JsonPrimitive(false), result.getOrThrow().jsonObject["claim_checked"])
        }
        assertTrue(ExpirationDatePolicy(requireField = true).verify(undated).isFailure)
        assertTrue(NotBeforePolicy(requireField = true).verify(undated).isFailure)
    }
}
