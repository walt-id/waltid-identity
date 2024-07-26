package id.walt.credentials.verification.policies

import id.walt.credentials.Claims
import id.walt.credentials.verification.CredentialWrapperValidatorPolicy
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import kotlin.test.Test

abstract class DatePolicyTestBase {

    protected abstract val sut: CredentialWrapperValidatorPolicy

    @DisplayName("given a vc with a valid claim, when verifying, then returns the correct result")
    @ParameterizedTest(name = "[{0}: {1}]")
    @MethodSource("vcSource")
    fun verifyVcResult(claim: Claims, instant: Instant, expected: (Result<Any>, Claims, Instant) -> Unit) = runTest {
        // given
        val vc = buildJson(claim.getValue(), instant.toString(), "vc")
        // when
        val result = sut.verify(vc, context = emptyMap())
        // then
        expected(result, claim, instant)
    }

    @DisplayName("given a jwt with a valid claim, when verifying, then returns the correct result")
    @ParameterizedTest(name = "[{0}: {1}]")
    @MethodSource("jwtSource")
    fun verifyJwtResult(claim: Claims, instant: Instant, expected: (Result<Any>, Claims, Instant) -> Unit) = runTest {
        // given
        val jwt = buildJson(claim.getValue(), instant.epochSeconds.toString())
        // when
        val result = sut.verify(jwt, context = emptyMap())
        // then
        expected(result, claim, instant)
    }

    @DisplayName("given data with missing any claim, when verifying, then returns the policy not available result")
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
}