package id.walt.policies

import id.walt.w3c.Claims
import id.walt.w3c.JwtClaims
import id.walt.w3c.VcClaims
import id.walt.policies.policies.NotBeforeDatePolicy
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Named.named
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import java.util.stream.Stream
import kotlin.time.Duration.Companion.days

class NotBeforeDatePolicyTest : DatePolicyTestBase() {

    override val sut: CredentialWrapperValidatorPolicy = NotBeforeDatePolicy()

    companion object {
        private val vcClaims = listOf<Claims>(VcClaims.V1.NotBefore, VcClaims.V2.NotBefore)
        private val jwtClaims = listOf<Claims>(JwtClaims.NotBefore, JwtClaims.IssuedAt)

        @JvmStatic
        fun vcSource(): Stream<Arguments> = vcClaims.flatMap {
            listOf(
                arguments(
                    named(it.getValue(), it),
                    named("past", Clock.System.now().minus(1.days)),
                    named("vc", "vc"),
                  Companion::assertSuccessResult
                ),
                arguments(
                    named(it.getValue(), it),
                    named("future", Clock.System.now().plus(1.days)),
                    named("vc", "vc"),
                  Companion::assertFailureResult
                ),
                arguments(
                    named(it.getValue(), it),
                    named("past", Clock.System.now().minus(1.days)),
                    named("root", null),
                  Companion::assertSuccessResult
                ),
                arguments(
                    named(it.getValue(), it),
                    named("future", Clock.System.now().plus(1.days)),
                    named("root", null),
                  Companion::assertFailureResult
                ),
            )
        }.let { Stream.of(*it.toTypedArray()) }

        @JvmStatic
        fun jwtSource(): Stream<Arguments> = jwtClaims.flatMap {
            listOf(
                arguments(
                    named(it.getValue(), it), named("past", Clock.System.now().minus(1.days)),
                  Companion::assertSuccessResult
                ),
                arguments(
                    named(it.getValue(), it), named("future", Clock.System.now().plus(1.days)),
                  Companion::assertFailureResult
                ),
            )
        }.let { Stream.of(*it.toTypedArray()) }

        @JvmStatic
        fun processOrderSource(): Stream<Arguments> = vcClaims.flatMap { vcClaim ->
            jwtClaims.flatMap { jwtClaim ->
                listOf(
                    arguments(
                        named(vcClaim.getValue(), vcClaim),
                        named("past", Clock.System.now().minus(1.days)),
                        named(jwtClaim.getValue(), jwtClaim),
                        named("future", Clock.System.now().plus(1.days)),
                        named("vc", "vc"),
                      Companion::assertSuccessResult
                    ),
                    arguments(
                        named(vcClaim.getValue(), vcClaim),
                        named("future", Clock.System.now().plus(1.days)),
                        named(jwtClaim.getValue(), jwtClaim),
                        named("past", Clock.System.now().minus(1.days)),
                        named("vc", "vc"),
                      Companion::assertFailureResult
                    ),
                    arguments(
                        named(vcClaim.getValue(), vcClaim),
                        named("past", Clock.System.now().minus(1.days)),
                        named(jwtClaim.getValue(), jwtClaim),
                        named("future", Clock.System.now().plus(1.days)),
                        named("root", null),
                      Companion::assertSuccessResult
                    ),
                    arguments(
                        named(vcClaim.getValue(), vcClaim),
                        named("future", Clock.System.now().plus(1.days)),
                        named(jwtClaim.getValue(), jwtClaim),
                        named("past", Clock.System.now().minus(1.days)),
                        named("root", null),
                      Companion::assertFailureResult
                    ),
                )
            }
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
            assert(json["available_since_seconds"]!!.jsonPrimitive.content.toLong() in withTolerance((Clock.System.now() - nbf).inWholeSeconds))
        }

        private fun assertFailureResult(result: Result<Any>, claim: Claims, nbf: Instant) {
            assert(result.isFailure)
            assert(result.exceptionOrNull() is NotBeforePolicyException)
            val exception = result.exceptionOrNull() as NotBeforePolicyException
            assert(exception.policyAvailable)
            assert(exception.key == claim.getValue())
            assert(exception.date.epochSeconds == nbf.epochSeconds)
            assert(exception.availableInSeconds in withTolerance((nbf - Clock.System.now()).inWholeSeconds))
        }
    }
}
