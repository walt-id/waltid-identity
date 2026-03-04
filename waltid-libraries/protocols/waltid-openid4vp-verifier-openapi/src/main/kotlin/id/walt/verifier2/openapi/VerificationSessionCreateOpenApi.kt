package id.walt.verifier2.openapi

import id.walt.verifier2.data.CrossDeviceFlowSetup
import id.walt.verifier2.data.DcApiAnnexCFlowSetup
import id.walt.verifier2.data.DcApiFlowSetup
import id.walt.verifier2.data.VerificationSessionSetup
import id.walt.verifier2.handlers.sessioncreation.VerificationSessionCreationResponse
import id.walt.verifier2.openapi.Verifier2OpenApiExamples.nestedPresentationRequestWithMultipleClaims
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

                // W3C VC Examples

                example("[openid4vp-http][w3c vc] default jwt_vc_json") { value = Verifier2OpenApiExamples.openid4vpHttpW3cVcDefault }
                example("[openid4vp-http][w3c vc] basic w3c policies (signature, expiration, not-before, allowed-issuer, regex)") {
                    value = Verifier2OpenApiExamples.openid4vpHttpW3cVcBasic
                }
                example("[openid4vp-http][w3c vc] credential status for TokenStatusList") {
                    value = Verifier2OpenApiExamples.openid4vpHttpW3cVcCredentialStatusTokenStatusList
                }
                example("[openid4vp-http][w3c vc] credential status for BitstringStatusList") {
                    value = Verifier2OpenApiExamples.openid4vpHttpW3cVcCredentialStatusBitstringStatusList
                }
                example("[openid4vp-http][w3c vc] credential status for multiple BitstringStatusList") {
                    value = Verifier2OpenApiExamples.openid4vpHttpW3cVcCredentialStatusMultipleBitstringStatusList
                }
                example("[openid4vp-http][w3c vc] webhook") { value = Verifier2OpenApiExamples.openid4vpHttpW3cVcWebhook }

                example("[openid4vp-http][w3c vc] presentation policies (jwt_vc_json/*)") {
                    value = Verifier2OpenApiExamples.openid4vpHttpW3cVcPresentation
                }

                example("[openid4vp-http][w3c vc] DCQL: W3C credential with path-based claims") {
                    value = Verifier2OpenApiExamples.w3cPlusPath
                }
                example("[openid4vp-http][w3c vc] DCQL: W3C credential with empty meta") { value = Verifier2OpenApiExamples.emptyMeta }
                example("[openid4vp-http][w3c vc] DCQL: Nested presentation request for W3C credentials") {
                    value = Verifier2OpenApiExamples.nestedPresentationRequestW3C
                }
                example("[openid4vp-http][w3c vc] DCQL: Nested presentation request with multiple claims") {
                    value = nestedPresentationRequestWithMultipleClaims
                }
                example("[openid4vp-http][w3c vc] DCQL: W3C credential with type values only") {
                    value = Verifier2OpenApiExamples.w3cTypeValues
                }
                example("[openid4vp-http][w3c vc] DCQL: W3C credential without claims") {
                    value = Verifier2OpenApiExamples.W3CWithoutClaims
                }
                example("[openid4vp-http][w3c vc] DCQL: W3C credential with claims and value constraints") {
                    value = Verifier2OpenApiExamples.W3CWithClaimsAndValues
                }

                // IETF SD-JWT VC Examples
                example("[openid4vp-http][ietf sd-jwt vc] default dc+sd-jwt") {
                    value = Verifier2OpenApiExamples.openid4vpHttpSdJwtVcDefault
                }
                example("[openid4vp-http][ietf sd-jwt vc] basic w3c policies (signature, expiration, not-before, allowed-issuer, regex)") {
                    value = Verifier2OpenApiExamples.openid4vpHttpSdJwtVcBasic
                }
                example("[openid4vp-http][ietf sd-jwt vc] presentation policies (dc+sd-jwt/*)") {
                    value = Verifier2OpenApiExamples.openid4vpHttpSdJwtVcPresentation
                }

                // ISO Examples

                example("[openid4vp-http][sd-jwt pid]") { value = CrossDeviceFlowSetup.EXAMPLE_SDJWT_PID }
                example("[openid4vp-http][iso pid]") { value = CrossDeviceFlowSetup.EXAMPLE_ISO_PID }
                example("[openid4vp-http][iso mdl & photo-id]") { value = CrossDeviceFlowSetup.EXAMPLE_MDL_OR_PHOTOID }
                example("[openid4vp-http][iso photo-id] vical") { value = Verifier2OpenApiExamples.openid4vpHttpIsoPhotoIdVical }

                example("[openid4vp-dc_api][iso mdl] unsigned & unencrypted") { value = DcApiFlowSetup.EXAMPLE_UNSIGNED_UNENCRYPTED_MDL }
                example("[openid4vp-dc_api][iso mdl] unsigned & encrypted") { value = DcApiFlowSetup.EXAMPLE_UNSIGNED_ENCRYPTED_MDL }
                example("[openid4vp-dc_api][iso mdl] signed & unencrypted") { value = DcApiFlowSetup.EXAMPLE_SIGNED_MDL }
                example("[openid4vp-dc_api][iso mdl] signed & encrypted") { value = DcApiFlowSetup.EXAMPLE_SIGNED_ENCRYPTED_MDL }
                example("[openid4vp-dc_api][iso photo-id] signed & encrypted") { value = DcApiFlowSetup.EXAMPLE_SIGNED_ENCRYPTED_PHOTOID }
                example("[openid4vp-dc_api][iso pid] signed & encrypted") { value = DcApiFlowSetup.EXAMPLE_SIGNED_ENCRYPTED_PID }

                // Annex-C examples
                example("[iso-18013-7-dc_api][iso mdl]") { value = DcApiAnnexCFlowSetup.EXTENDED_MDL_EXAMPLE }
                example("[iso-18013-7-dc_api][iso pid]") { value = DcApiAnnexCFlowSetup.EXTENDED_PID_EXAMPLE }
                example("[iso-18013-7-dc_api][iso photo-id]") { value = DcApiAnnexCFlowSetup.EXTENDED_PHOTOID_EXAMPLE }
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
