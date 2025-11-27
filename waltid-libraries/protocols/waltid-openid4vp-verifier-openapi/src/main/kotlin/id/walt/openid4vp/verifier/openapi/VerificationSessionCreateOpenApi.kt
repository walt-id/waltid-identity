package id.walt.openid4vp.verifier.openapi

import id.walt.openid4vp.verifier.data.CrossDeviceFlowSetup
import id.walt.openid4vp.verifier.data.DcApiFlowSetup
import id.walt.openid4vp.verifier.data.VerificationSessionSetup
import id.walt.openid4vp.verifier.handlers.sessioncreation.VerificationSessionCreator.VerificationSessionCreationResponse
import id.walt.openid4vp.verifier.openapi.Verifier2OpenApiExamples.nestedPresentationRequestWithMultipleClaims
import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.ktor.http.*

object VerificationSessionCreateOpenApi {

    val createDocs: RouteConfig.() -> Unit = {
        summary = "Create new verification session"
        request {
            headerParameter<String>("X-Request-Id")
            body<VerificationSessionSetup> {
                example("Basic example") { value = Verifier2OpenApiExamples.basicExample }

                example("Cross device flow: mDL or Photo ID") { value = CrossDeviceFlowSetup.EXAMPLE_MDL_OR_PHOTOID }
                example("Cross device flow: dc+sd-jwt PID") { value = CrossDeviceFlowSetup.EXAMPLE_SDJWT_PID }
                //example("Same device flow") { value = SameDeviceFlow.EXAMPLE }
                example("DC API flow: Signed mDL") { value = DcApiFlowSetup.EXAMPLE_SIGNED_MDL }
                example("DC API flow: Signed & encrypted mDL") { value = DcApiFlowSetup.EXAMPLE_SIGNED_ENCRYPTED_MDL }
                example("DC API flow: Signed Photo ID") { value = DcApiFlowSetup.EXAMPLE_SIGNED_PHOTOID }

                example("DCQL example: Basic example") { value = Verifier2OpenApiExamples.basicExample }
                example("DCQL example: W3C + path") { value = Verifier2OpenApiExamples.w3cPlusPath }
                example("DCQL example: Empty meta") { value = Verifier2OpenApiExamples.emptyMeta }
                example("DCQL example: Nested presentation request") { value = Verifier2OpenApiExamples.nestedPresentationRequestW3C }
                example("DCQL example: Nested presentation request + multiple claims") { value = nestedPresentationRequestWithMultipleClaims }
                example("DCQL example: W3C type values") { value = Verifier2OpenApiExamples.w3cTypeValues }
                example("DCQL example: W3C without claims") { value = Verifier2OpenApiExamples.W3CWithoutClaims }
                example("DCQL example: W3C with claims and values") { value = Verifier2OpenApiExamples.W3CWithClaimsAndValues }

                example("VICAL: VICAL policy (only for ISO mDL/mdoc") { value = Verifier2OpenApiExamples.VicalPolicyValues }
            }
        }
        response {
            HttpStatusCode.Created to {
                body<VerificationSessionCreationResponse>()
            }
        }
    }

}
