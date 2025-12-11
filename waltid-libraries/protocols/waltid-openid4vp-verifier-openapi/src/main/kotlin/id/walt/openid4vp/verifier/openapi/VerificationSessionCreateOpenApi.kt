package id.walt.openid4vp.verifier.openapi

import id.walt.openid4vp.verifier.data.CrossDeviceFlowSetup
import id.walt.openid4vp.verifier.data.DcApiFlowSetup
import id.walt.openid4vp.verifier.data.VerificationSessionSetup
import id.walt.openid4vp.verifier.handlers.sessioncreation.VerificationSessionCreator.VerificationSessionCreationResponse
import id.walt.openid4vp.verifier.openapi.Verifier2OpenApiExamples.nestedPresentationRequestWithMultipleClaims
import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.ktor.http.*

object VerificationSessionCreateOpenApi {

    private const val DOCUMENTATION_URL_PLACEHOLDER =
        "<a href='https://docs.walt.id/enterprise-stack/services/verifier2-service/overview'>the docs</a>"

    val createDocs: RouteConfig.() -> Unit = {
        summary = "Create new verification session"
        description = """
            Creates a new verification session with a DCQL query specifying which credentials and claims are required, along with optional verification policies.
            
            **Use Cases:**
            - Request identity credentials from wallet holders
            - Verify specific claims from verifiable credentials
            - Apply verification policies (signature validation, revocation checks, status list validation)
            - Support multiple credential formats (JWT VC, SD-JWT VC, ISO mDoc)
            
            **Important Notes:**
            - Returns both bootstrap and full authorization request URLs for wallet interaction
            - Supports multiple verification policies including signature, revocation, status list, and VICAL validation
            - DCQL query allows requesting specific claims from credentials using JSON path notation
            - Sessions expire after 10 minutes if unused and are retained for 10 years by default
            - Session status can be tracked via SSE events endpoint
            
            For more information, see: $DOCUMENTATION_URL_PLACEHOLDER
        """.trimIndent()

        request {
            headerParameter<String>("X-Request-Id") {
                description = "Optional request ID for tracing"
            }
            body<VerificationSessionSetup> {
                required = true
                description = "Verification session setup with DCQL query and optional policies"

                example("Basic SD-JWT example") { value = Verifier2OpenApiExamples.basicExample }

                example("Cross device flow: mDL or Photo ID") { value = CrossDeviceFlowSetup.EXAMPLE_MDL_OR_PHOTOID }
                example("Cross device flow: dc+sd-jwt PID") { value = CrossDeviceFlowSetup.EXAMPLE_SDJWT_PID }
                //example("Same device flow") { value = SameDeviceFlow.EXAMPLE }
                example("DC API flow: Signed mDL") { value = DcApiFlowSetup.EXAMPLE_SIGNED_MDL }
                example("DC API flow: Signed & encrypted mDL") { value = DcApiFlowSetup.EXAMPLE_SIGNED_ENCRYPTED_MDL }
                example("DC API flow: Signed Photo ID") { value = DcApiFlowSetup.EXAMPLE_SIGNED_PHOTOID }

                example("DCQL example: Basic example") { value = Verifier2OpenApiExamples.basicExample }
                example("DCQL example: W3C credential with path-based claims") { value = Verifier2OpenApiExamples.w3cPlusPath }
                example("DCQL example: W3C credential with empty meta") { value = Verifier2OpenApiExamples.emptyMeta }
                example("DCQL example: Nested presentation request for W3C credentials") {
                    value = Verifier2OpenApiExamples.nestedPresentationRequestW3C
                }
                example("DCQL example: Nested presentation request with multiple claims") {
                    value = nestedPresentationRequestWithMultipleClaims
                }
                example("DCQL example: W3C credential with type values only") { value = Verifier2OpenApiExamples.w3cTypeValues }
                example("DCQL example: W3C credential without claims") { value = Verifier2OpenApiExamples.W3CWithoutClaims }
                example("DCQL example: W3C credential with claims and value constraints") {
                    value = Verifier2OpenApiExamples.W3CWithClaimsAndValues
                }

                example("Basic example with revoked-status-list policy") {
                    value = Verifier2OpenApiExamples.basicExampleWithRevokedStatusListPolicy
                }
                example("Basic example with credential-status policy for single BitstringStatusList") {
                    value = Verifier2OpenApiExamples.basicExampleWithRevokedStatusListPolicy
                }
                example("Basic example with credential-status policy for multiple BitstringStatusList") {
                    value = Verifier2OpenApiExamples.basicExampleWithStatusPolicyForMultipleBitstringStatusList
                }
                example("Basic example with credential-status policy for TokenStatusList") {
                    value = Verifier2OpenApiExamples.basicExampleWithStatusPolicyForTokenStatusList
                }

                example("VICAL: ISO mdocs with VICAL policy (only for ISO mDL/mdoc)") { value = Verifier2OpenApiExamples.VicalPolicyValues }
            }
        }
        response {
            HttpStatusCode.Created to {
                description = "Verification session created successfully"
                body<VerificationSessionCreationResponse> {
                    required = true
                    description = "Verification session creation response with session ID and authorization request URLs"
                }
            }
            HttpStatusCode.BadRequest to {
                description = "Invalid request - malformed DCQL query or invalid policy configuration"
            }
            HttpStatusCode.Unauthorized to {
                description = "Authentication required"
            }
            HttpStatusCode.Forbidden to {
                description = "Insufficient permissions to create verification sessions"
            }
        }
    }

}
