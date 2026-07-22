package id.walt.policies2.vp.policies

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.EdwardsCurve
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.CryptographySoftwareKeyProvider
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class SdJwtBindingValidationTest {
    private val runtime = CryptoRuntime(listOf(CryptographySoftwareKeyProvider()))

    @Test
    fun `KB-JWT type and numeric iat are required`() = runTest {
        val verificationTime = Instant.fromEpochSeconds(1_700_000_000)
        val valid = jwt(
            payload = JsonObject(mapOf("iat" to JsonPrimitive(verificationTime.epochSeconds))),
            type = "kb+jwt",
        )

        requireKbJwtType(valid)
        assertEquals(
            verificationTime.epochSeconds.toDouble(),
            requireFreshKbJwtIssuedAt(valid, verificationTime, 5.minutes),
        )
        assertFailsWith<IllegalArgumentException> {
            requireKbJwtType(jwt(JsonObject(mapOf("iat" to JsonPrimitive(verificationTime.epochSeconds))), "JWT"))
        }
        val fractional = jwt(
            JsonObject(mapOf("iat" to JsonPrimitive(verificationTime.epochSeconds + 0.5))),
            "kb+jwt",
        )
        assertEquals(
            verificationTime.epochSeconds + 0.5,
            requireFreshKbJwtIssuedAt(fractional, verificationTime, 5.minutes),
        )
        assertFailsWith<IllegalArgumentException> {
            requireFreshKbJwtIssuedAt(jwt(JsonObject(emptyMap()), "kb+jwt"), verificationTime, 5.minutes)
        }
        assertFailsWith<IllegalArgumentException> {
            requireFreshKbJwtIssuedAt(
                jwt(JsonObject(mapOf("iat" to JsonPrimitive(verificationTime.epochSeconds.toString()))), "kb+jwt"),
                verificationTime,
                5.minutes,
            )
        }
    }

    @Test
    fun `SD-JWT disclosure algorithm must be SHA-256`() = runTest {
        requireSupportedSdAlgorithm(jwt(JsonObject(mapOf("_sd_alg" to JsonPrimitive("sha-256"))), "JWT"))
        assertFailsWith<IllegalArgumentException> {
            requireSupportedSdAlgorithm(jwt(JsonObject(mapOf("_sd_alg" to JsonPrimitive("sha-512"))), "JWT"))
        }
        assertFailsWith<IllegalArgumentException> {
            requireSupportedSdAlgorithm(jwt(JsonObject(emptyMap()), "JWT"))
        }
    }

    private suspend fun jwt(payload: JsonObject, type: String): String {
        val key = runtime.generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId("key"),
                spec = KeySpec.Edwards(EdwardsCurve.ED25519),
                usages = setOf(KeyUsage.SIGN),
            )
        )
        return CompactJws.sign(
            Json.encodeToString(payload).encodeToByteArray(),
            key,
            JwsAlgorithm.ED25519,
            JsonObject(mapOf("typ" to JsonPrimitive(type))),
        )
    }
}
