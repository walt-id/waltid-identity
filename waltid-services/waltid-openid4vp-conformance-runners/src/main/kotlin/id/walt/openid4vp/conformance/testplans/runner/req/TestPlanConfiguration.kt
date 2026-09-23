package id.walt.openid4vp.conformance.testplans.runner.req

import id.walt.verifier2.data.VerificationSessionSetup
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class TestPlanConfiguration(
    val testPlanCreationUrl: ParametersBuilder.() -> Unit,
    val testPlanCreationConfiguration: JsonObject,
    val moduleOutcomes: Map<String, ExpectedVerifierOutcome> = emptyMap(),
    /**
     * Whether to hand the suite the complete authorization request URL instead of the bootstrap one.
     *
     * `url_query` variants carry every parameter in the URL, so they need the full request. The
     * bootstrap URL is only a `request_uri` reference and deliberately omits `nonce`, `dcql_query`
     * and `response_uri` - the wallet is expected to fetch those - so using it for `url_query` fails
     * the suite's OID4VP-1FINAL-5.2 nonce check.
     */
    val presentUsingFullRequestUrl: Boolean = false,
    val verificationSessionSetup: VerificationSessionSetup
)
