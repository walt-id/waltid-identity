package id.walt.credentials.verification.policies

import id.walt.credentials.Claims
import id.walt.credentials.JwtClaims
import id.walt.credentials.VcClaims
import id.walt.credentials.verification.NotBeforePolicyException
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Named.named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.ArgumentsSources
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.test.Test
import kotlin.time.Duration.Companion.days

class NotBeforeDatePolicyTest {

    private val sut = NotBeforeDatePolicy()

    @DisplayName("given a vc with a not-before claim, when verifying, then returns the correct result")
    @ParameterizedTest(name = "[{0}: {1}]")
    @MethodSource("vcSource")
    fun verifyVcResult(claim: Claims, nbf: Instant, expected: (Result<Any>, Claims, Instant) -> Unit) = runTest {
        // given
        val vc = buildJson(claim.getValue(), nbf.toString(), "vc")
        // when
        val result = sut.verify(vc, context = emptyMap())
        // then
        expected(result, claim, nbf)
    }

    @DisplayName("given a jwt with a not-before claim, when verifying, then returns the correct result")
    @ParameterizedTest(name = "[{0}: {1}]")
    @MethodSource("jwtSource")
    fun verifyJwtResult(claim: Claims, nbf: Instant, expected: (Result<Any>, Claims, Instant) -> Unit) = runTest {
        // given
        val jwt = buildJson(claim.getValue(), nbf.epochSeconds.toString())
        // when
        val result = sut.verify(jwt, context = emptyMap())
        // then
        expected(result, claim, nbf)
    }

    @DisplayName("given data with missing not-before claim, when verifying, then returns the policy not available result")
    @Test
    fun verifyNotAvailableResult() = runTest {
        // given
        val data = buildJsonObject { }
        // when
        val result = sut.verify(data, context = emptyMap())
        // then
        assert(result.isSuccess)
        val json = result.getOrThrow() as JsonObject
        assert(json.containsKey("policy_available"))
        assert(!json["policy_available"]!!.jsonPrimitive.boolean)
    }

    private fun buildJson(claim: String, instant: String, root: String? = null): JsonObject = buildJsonObject {
        root?.let {
            putJsonObject(it) {
                put(claim, Json.encodeToJsonElement(instant))
            }
        } ?: put(claim, Json.encodeToJsonElement(instant))
    }

    companion object {
        private val vcClaims = listOf<Claims>(VcClaims.V1.NotBefore, VcClaims.V2.NotBefore)
        private val jwtClaims = listOf<Claims>(JwtClaims.NotBefore, JwtClaims.IssuedAt)

        @JvmStatic
        fun vcSource(): Stream<Arguments> = vcClaims.flatMap {
            listOf(
                arguments(
                    named(it.getValue(), it), named("past", Clock.System.now().minus(1.days)), ::assertSuccessResult
                ),
                arguments(
                    named(it.getValue(), it), named("future", Clock.System.now().plus(1.days)), ::assertFailureResult
                ),
            )
        }.let { Stream.of(*it.toTypedArray()) }

        @JvmStatic
        fun jwtSource(): Stream<Arguments> = jwtClaims.flatMap {
            listOf(
                arguments(
                    named(it.getValue(), it), named("past", Clock.System.now().minus(1.days)), ::assertSuccessResult
                ),
                arguments(
                    named(it.getValue(), it), named("future", Clock.System.now().plus(1.days)), ::assertFailureResult
                ),
            )
        }.let { Stream.of(*it.toTypedArray()) }

        private fun assertSuccessResult(result: Result<Any>, claim: Claims, nbf: Instant) {
            assert(result.isSuccess)
            val json = result.getOrThrow() as JsonObject
            assert(json.containsKey("policy_available"))
            assert(json["policy_available"]!!.jsonPrimitive.boolean)
            assert(json.containsKey("used_key"))
            assert(json["used_key"]!!.jsonPrimitive.content == claim.getValue())
            assert(json.containsKey("date_seconds"))
            assert(json["date_seconds"]!!.jsonPrimitive.content == nbf.epochSeconds.toString())
            assert(json.containsKey("available_since_seconds"))
            assert(json["available_since_seconds"]!!.jsonPrimitive.content == (Clock.System.now() - nbf).inWholeSeconds.toString())
        }

        private fun assertFailureResult(result: Result<Any>, claim: Claims, nbf: Instant) {
            assert(result.isFailure)
            assert(result.exceptionOrNull() is NotBeforePolicyException)
            val exception = result.exceptionOrNull() as NotBeforePolicyException
            assert(exception.policyAvailable)
            assert(exception.key == claim.getValue())
            assert(exception.date.epochSeconds == nbf.epochSeconds)
            assert(exception.availableInSeconds == (nbf - Clock.System.now()).inWholeSeconds)
        }
    }
}