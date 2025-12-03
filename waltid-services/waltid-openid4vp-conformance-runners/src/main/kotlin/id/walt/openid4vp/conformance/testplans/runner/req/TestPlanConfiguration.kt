package id.walt.openid4vp.conformance.testplans.runner.req

import id.walt.openid4vp.verifier.data.VerificationSessionSetup
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class TestPlanConfiguration(
    val testPlanCreationUrl: ParametersBuilder.() -> Unit,
    val testPlanCreationConfiguration: JsonObject,
    val verificationSessionSetup: VerificationSessionSetup
)
