package id.walt.verifier.openapi

import id.walt.oid4vc.data.OpenId4VPProfile
import id.walt.oid4vc.data.ResponseMode
import id.walt.verifier.DescriptorMappingFormParam
import id.walt.verifier.PresentationSubmissionFormParam
import id.walt.verifier.TokenResponseFormParam
import id.walt.verifier.defaultAuthorizeBaseUrl
import id.walt.verifier.oidc.SwaggerPresentationSessionInfo
import id.walt.w3c.utils.VCFormat
import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.ktor.http.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

object VerifierApiDocs {
    fun getVerifyDocs(): RouteConfig.() -> Unit = {
        tags = listOf("Credential Verification")
        summary = "Initialize OIDC presentation session"
        description =
            "Initializes an OIDC presentation session, with the given presentation definition and parameters. The URL returned can be rendered as QR code for the holder wallet to scan, or called directly on the holder if the wallet base URL is given."
        request {
            headerParameter<String>("authorizeBaseUrl") {
                description = "Base URL of wallet authorize endpoint, defaults to: $defaultAuthorizeBaseUrl"
                example("default authorize base url") {
                    value = defaultAuthorizeBaseUrl
                }
                required = false
            }
            headerParameter<ResponseMode>("responseMode") {
                description = "Response mode, for vp_token response, defaults to ${ResponseMode.direct_post}"
                example("direct post") {
                    value = ResponseMode.direct_post
                }
                required = false
            }
            headerParameter<String>("successRedirectUri") {
                description =
                    "Redirect URI to return when all policies passed. \"\$id\" will be replaced with the session id."
                // example = ""
                required = false
            }
            headerParameter<String>("errorRedirectUri") {
                description =
                    "Redirect URI to return when a policy failed. \"\$id\" will be replaced with the session id."
                // example = ""
                required = false
            }
            headerParameter<String>("statusCallbackUri") {
                description = "Callback to push state changes of the presentation process to"
                // example = ""
                required = false
            }
            headerParameter<String>("statusCallbackApiKey") {
                description = ""
                // example = ""
                required = false
            }
            headerParameter<String>("stateId") {
                description = ""
                // example = ""
                required = false
            }
            headerParameter<String?>("openId4VPProfile") {
                description =
                    "Optional header to set the profile of the VP request, available profiles: ${OpenId4VPProfile.entries.joinToString()}"
                // example = ""
                required = false
            }
            headerParameter<String?>("sessionTtl") {
                description =
                    "Optional header to set the sessionTtl of the VP request, in seconds"
                // example = ""
                required = false
            }
            body<JsonObject> {
                required = true
                description =
                    "Presentation definition, describing the presentation requirement for this verification session. ID of the presentation definition is automatically assigned randomly."
                //example("Verifiable ID example", verifiableIdPresentationDefinitionExample)
                example("Minimal example", VerifierApiExamples.minimal)
                example("Example with VP policies", VerifierApiExamples.vpPolicies)
                example("Example with VP & global VC policies", VerifierApiExamples.vpGlobalVcPolicies)
                example(
                    "Example with VP & specific credential policies",
                    VerifierApiExamples.vpRequiredCredentialsLogic
                )
                example(
                    "Example with VP, VC & specific credential policies",
                    VerifierApiExamples.vcVpIndividualPolicies
                )
                example(
                    "Example with Dynamic Policy applied to credential Data",
                    VerifierApiExamples.dynamicPolicy
                )
                example(
                    "Example with VP, VC & specific policies, and explicit input_descriptor(s)  (maximum example)",
                    VerifierApiExamples.maxExample
                )
                example(
                    "Example with presentation definition policy",
                    VerifierApiExamples.presentationDefinitionPolicy
                )
                example(
                    "Example with EBSI PDA1 Presentation Definition",
                    VerifierApiExamples.EbsiVerifiablePDA1
                )
                example("mDL Request presentation of all mandatory fields example") {
                    value = VerifierApiExamples.mDLRequiredFieldsExample
                }
                example("mDL Request presentation of birth date & validity fields only example") {
                    value = VerifierApiExamples.mDLBirthDateSelectiveDisclosureExample
                }
                example("mDL Request presentation of age over 18 attestation & validity fields only example (assumes `age_over_18` field exists in issued mDL)") {
                    value = VerifierApiExamples.mDLAgeOver18AttestationExample
                }
                addSdJwtVcExamples()
                addEbsiVectorInteropTestExamples()
                addCredentialStatusExamples()
            }
        }
        response {
            HttpStatusCode.OK to {
                description = "URL for the holder wallet to continue verification"
                body<String> {
                    mediaTypes(ContentType.Text.Plain)
                }
            }
        }

    }

    fun getVerifyStateDocs(): RouteConfig.() -> Unit = {
        tags = listOf("OIDC")
        summary = "Verify vp_token response, for a verification request identified by the state"
        description =
            "Called in direct_post response mode by the SIOP provider (holder wallet) with the verifiable presentation in the vp_token and the presentation_submission parameter, describing the submitted presentation. The presentation session is identified by the given state parameter."
        request {
            pathParameter<String>("state") {
                description =
                    "State, i.e. session ID, identifying the presentation session, this response belongs to."
                required = true
            }
            body<TokenResponseFormParam> {
                required = true
                mediaTypes = listOf(ContentType.Application.FormUrlEncoded)
                example("simple vp_token response") {
                    value = TokenResponseFormParam(
                        vp_token = JsonPrimitive("abc.def.ghi"),
                        presentation_submission = PresentationSubmissionFormParam(
                            id = "1",
                            definition_id = "1",
                            descriptor_map = listOf(
                                DescriptorMappingFormParam(
                                    "1",
                                    VCFormat.jwt_vc_json,
                                    "$.vc.type"
                                )
                            )
                        ),
                        response = null
                    )
                }
                example("direct_post.jwt response") {
                    value = TokenResponseFormParam(null, null, "ey...")
                }
            }
        }
    }

    fun getSessionDocs(): RouteConfig.() -> Unit = {
        tags = listOf("Credential Verification")
        summary = "Get info about OIDC presentation session, that was previously initialized"
        description =
            "Session info, containing current state and result information about an ongoing OIDC presentation session"
        request {
            pathParameter<String>("id") {
                description = "Session ID"
                required = true
            }
        }
        response {
            HttpStatusCode.OK to {
                // body<PresentationSessionInfo> { // cannot encode duration
                description = "Session info"
                body<SwaggerPresentationSessionInfo>()
            }
            HttpStatusCode.NotFound to {
                description = "Session not found or invalid"
                body<String> {
                    example("Session not found") {
                        value = "Invalid id provided (expired?): 123"
                    }
                }
            }
        }
    }

    fun getPdDocs(): RouteConfig.() -> Unit = {
        tags = listOf("OIDC")
        summary = "Get presentation definition object by ID"
        description =
            "Gets a presentation definition object, previously uploaded during initialization of OIDC presentation session."
        request {
            pathParameter<String>("id") {
                description = "ID of presentation definition object to retrieve"
                required = true
            }
        }
        response {
            HttpStatusCode.OK to {
                description = "Presentation definition"
                body<JsonObject>()
            }
            HttpStatusCode.NotFound to {
                description = "Presentation definition not found"
                body<String>()
            }
        }
    }

    fun getPolicyListDocs(): RouteConfig.() -> Unit = {
        tags = listOf("Credential Verification")
        summary = "List registered policies"
        response {
            HttpStatusCode.OK to {
                description = "List of registered policies"
                body<Map<String, String?>>()
            }
        }
    }

    fun getRequestDocs(): RouteConfig.() -> Unit = {
        tags = listOf("OIDC")
        summary = "Get request object for session by session id"
        description = "Gets the signed request object for the session given by the session id parameter"
        request {
            pathParameter<String>("id") {
                description = "ID of the presentation session"
                required = true
            }
        }
    }

}