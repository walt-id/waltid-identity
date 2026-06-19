package id.walt.openid4vp.conformance.testplans.runner.req

import id.walt.verifier2.data.VerificationSessionSetup
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Whether the test module expects the verifier to accept (positive test) or
 * reject (negative test) the presented credential.
 */
enum class ExpectedVerifierOutcome {
    /** Verifier must accept — session ends SUCCESSFUL */
    ACCEPT,

    /** Verifier must reject — session ends UNSUCCESSFUL (4xx response to the conformance suite) */
    REJECT,

    /**
     * Test may be skipped by the conformance suite (e.g. request-uri-method-post
     * when the verifier does not advertise request_uri_method=post).
     * If not skipped, treated as ACCEPT.
     */
    ACCEPT_OR_SKIP
}

@Serializable
data class TestPlanConfiguration(
    val testPlanCreationUrl: ParametersBuilder.() -> Unit,
    val testPlanCreationConfiguration: JsonObject,

    /**
     * Variant JSON string shared by all test modules in this plan.
     * The conformance suite requires the same variant parameters for every module.
     */
    val moduleVariant: String,

    /**
     * Per-module expected outcome. Every module returned by the plan creation
     * response must have an entry here. Unknown modules cause an error.
     */
    val moduleOutcomes: Map<String, ExpectedVerifierOutcome>,

    val verificationSessionSetup: VerificationSessionSetup
)
