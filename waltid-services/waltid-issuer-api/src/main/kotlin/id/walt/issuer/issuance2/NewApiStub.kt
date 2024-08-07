package id.walt.issuer.issuance2

import id.walt.issuer.issuance.NewIssuanceRequest
import io.github.smiley4.ktorswaggerui.dsl.routing.post
import io.github.smiley4.ktorswaggerui.dsl.routing.route
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*

object NewApiStub {

    fun Application.newApi() {
        routing {
            route("openid4vc") {
                route("jwt") {
                    post("issue", {
                        summary = "Signs credential with JWT and starts an OIDC credential exchange flow."
                        description = "This endpoint issues a W3C Verifiable Credential, and returns an issuance URL "

                        request {
                            body<NewIssuanceRequest> {
                                description = "Issuance request"
                                //example("OpenBadgeCredential example", IssuanceExamples.openBadgeCredentialExample)
                                //example("UniversityDegreeCredential example", IssuanceExamples.universityDegreeCredential)
                                required = true
                            }
                        }

                        response {
                            "200" to {
                                description = "Credential signed (with the *proof* attribute added)"
                                body<String> {
                                    example("Issuance URL") {
                                        value =
                                            "openid-credential-offer://localhost/?credential_offer=%7B%22credential_issuer%22%3A%22http%3A%2F%2Flocalhost%3A8000%22%2C%22credentials%22%3A%5B%22VerifiableId%22%5D%2C%22grants%22%3A%7B%22authorization_code%22%3A%7B%22issuer_state%22%3A%22501414a4-c461-43f0-84b2-c628730c7c02%22%7D%7D%7D"
                                    }
                                }
                            }
                        }
                    }) {
                        val issuanceRequest = context.receive<NewIssuanceRequest>()
                        IssuanceOfferManager.makeOfferFor(issuanceRequest)
                    }
                }
            }
        }
    }
}
