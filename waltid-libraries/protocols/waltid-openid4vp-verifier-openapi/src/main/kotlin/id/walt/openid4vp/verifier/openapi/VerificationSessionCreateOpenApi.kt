package id.walt.openid4vp.verifier.openapi

import id.walt.openid4vp.verifier.VerificationSessionCreator.VerificationSessionCreationResponse
import id.walt.openid4vp.verifier.VerificationSessionCreator.VerificationSessionSetup
import id.walt.openid4vp.verifier.openapi.Verifier2OpenApiExamples.exampleOf
import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.ktor.http.*

object VerificationSessionCreateOpenApi {

    val createDocs: RouteConfig.() -> Unit = {
        summary = "Create new verification session"
        request {
            body<VerificationSessionSetup> {
                example("Basic example") { value = Verifier2OpenApiExamples.basicExample }
                example("W3C + path") { value = exampleOf(Verifier2OpenApiExamples.w3cPlusPath) }
                example("Empty meta") { value = exampleOf(Verifier2OpenApiExamples.emptyMeta) }
                example("Nested presentation request") {
                    value = exampleOf(Verifier2OpenApiExamples.nestedPresentationRequestW3C)
                }
                example("Nested presentation request + multiple claims") {
                    value = exampleOf(Verifier2OpenApiExamples.nestedPresentationRequestWithMultipleClaims)
                }
                example("W3C type values") { value = exampleOf(Verifier2OpenApiExamples.w3cTypeValues) }
                example("W3C without claims") { value = exampleOf(Verifier2OpenApiExamples.W3CWithoutClaims) }
                example("W3C with claims and values") { value = exampleOf(Verifier2OpenApiExamples.W3CWithClaimsAndValues) }
            }
        }
        response {
            HttpStatusCode.Created to {
                body<VerificationSessionCreationResponse>()
            }
        }
    }

}
