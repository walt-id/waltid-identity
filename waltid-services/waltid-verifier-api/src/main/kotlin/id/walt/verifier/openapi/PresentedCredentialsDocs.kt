package id.walt.verifier.openapi

import id.walt.verifier.oidc.models.presentedcredentials.PresentedCredentialsViewMode
import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.ktor.http.*

object PresentedCredentialsDocs {

    fun getPresentedCredentialsDocs(): RouteConfig.() -> Unit = {
        tags = listOf("Credential Verification")
        summary = "Retrieve decoded credentials associated with a successfully verified presentation session"
        description =
                "Returns a structured, verbose representation of all credentials presented\n" +
                "in a successfully verified presentation session. This endpoint is only available\n" +
                "for sessions whose `vp_token` was verified with a positive result (`verificationResult == true`).\n" +
                "\n" +
                "Credentials are grouped by format and returned in both decoded and raw forms."

        request {
            pathParameter<String>("id") {
                description = "The identifier of the presentation session whose credentials should be retrieved."
                required = true
            }

            queryParameter<PresentedCredentialsViewMode>("viewMode") {
                description = "The view mode (default == simple)"
                required = false
            }
        }

        response {

            HttpStatusCode.OK to {
//                body<PresentationSessionPresentedCredentials> {
//                    description = "Map of credential formats to lists of decoded presented credentials."
//                    required = true
//
//                    example(
//                        name = "OpenBadge W3C VC (no disclosures)"
//                    ) {
//                        value = openBadgeNoDisclosuresResponse
//                    }
//
//                    example(
//                        name = "OpenBadge W3C VC with disclosures"
//                    ) {
//                        value = openBadgeWithDisclosuresResponse
//                    }
//
//                    example(
//                        name = "University Degree W3C VC (no disclosures)"
//                    ) {
//                        value = uniDegreeNoDisclosuresResponse
//                    }
//
//                    example(
//                        name = "University Degree W3C VC with disclosures"
//                    ) {
//                        value = uniDegreeWithDisclosuresResponse
//                    }
//
//                    example(
//                        name = "Sd Jwt VC"
//                    ) {
//                        value = sdJwtVcResponse
//                    }
//
//                    example(
//                        name = "mDL with all required fields"
//                    ) {
//                        value = mDLExampleResponse
//                    }
//
//                }
            }
        }
    }
}