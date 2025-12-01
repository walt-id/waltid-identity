package id.walt.openid4vp.verifier.openapi

import id.walt.openid4vp.verifier.VerificationSessionCreator.VerificationSessionCreationResponse
import id.walt.openid4vp.verifier.VerificationSessionCreator.VerificationSessionSetup
import id.walt.openid4vp.verifier.openapi.Verifier2OpenApiExamples.exampleOf
import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.ktor.http.*

object VerificationSessionCreateOpenApi {

    private const val DOCUMENTATION_URL_PLACEHOLDER = "<a href='https://docs.walt.id/enterprise-stack/services/verifier2-service/overview'>the docs</a>"

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
                example("Basic SD-JWT example") { 
                    value = Verifier2OpenApiExamples.basicExample 
                }
                example("Basic example with revoked-status-list policy") { 
                    value = Verifier2OpenApiExamples.basicExampleWithRevokedStatusListPolicy 
                }
                example("Basic example with credential-status policy for single BitstringStatusList") { 
                    value = Verifier2OpenApiExamples.basicExampleWithStatusPolicyForSingleBitstringStatusList 
                }
                example("Basic example with credential-status policy for multiple BitstringStatusList") { 
                    value = Verifier2OpenApiExamples.basicExampleWithStatusPolicyForMultipleBitstringStatusList 
                }
                example("Basic example with credential-status policy for TokenStatusList") { 
                    value = Verifier2OpenApiExamples.basicExampleWithStatusPolicyForTokenStatusList 
                }
                example("W3C credential with path-based claims") { 
                    value = exampleOf(Verifier2OpenApiExamples.w3cPlusPath) 
                }
                example("W3C credential with empty meta") { 
                    value = exampleOf(Verifier2OpenApiExamples.emptyMeta) 
                }
                example("Nested presentation request for W3C credentials") {
                    value = exampleOf(Verifier2OpenApiExamples.nestedPresentationRequestW3C)
                }
                example("Nested presentation request with multiple claims") {
                    value = exampleOf(Verifier2OpenApiExamples.nestedPresentationRequestWithMultipleClaims)
                }
                example("W3C credential with type values only") { 
                    value = exampleOf(Verifier2OpenApiExamples.w3cTypeValues) 
                }
                example("W3C credential without claims") { 
                    value = exampleOf(Verifier2OpenApiExamples.W3CWithoutClaims) 
                }
                example("W3C credential with claims and value constraints") { 
                    value = exampleOf(Verifier2OpenApiExamples.W3CWithClaimsAndValues) 
                }
                example("ISO mDoc with VICAL policy") { 
                    value = exampleOf(Verifier2OpenApiExamples.VicalPolicyValues) 
                }
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
