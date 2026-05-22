package id.walt.issuer2.controller.openapi

import id.walt.issuer2.controller.dto.CreateCredentialOfferRequest
import id.walt.issuer2.controller.dto.CreateCredentialOfferResponse
import id.walt.issuer2.domain.CredentialProfile
import id.walt.issuer2.domain.IssuanceSession
import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.ktor.http.HttpStatusCode

object Issuer2ManagementRoutesDocs {
    fun listProfiles(): RouteConfig.() -> Unit = {
        summary = "List credential profiles"
        description = "List credential profiles loaded from issuer2-profiles.conf."
        response {
            HttpStatusCode.OK to {
                description = "Configured credential profiles"
                body<List<CredentialProfile>>()
            }
        }
    }

    fun getProfile(): RouteConfig.() -> Unit = {
        summary = "Get credential profile"
        description = "Get one credential profile by profile ID."
        request {
            pathParameter<String>("profileId")
        }
        response {
            HttpStatusCode.OK to {
                description = "Credential profile"
                body<CredentialProfile>()
            }
        }
    }

    fun createCredentialOffer(): RouteConfig.() -> Unit = {
        summary = "Create credential offer"
        description =
            "Create an issuance session and return an OpenID4VCI credential offer URI. " +
                "Supports pre-authorized and authorization-code issuance flows."
        request {
            body<CreateCredentialOfferRequest> {
                example("Pre-authorized credential offer") {
                    value = Issuer2RequestExamples.PRE_AUTHORIZED_CREDENTIAL_OFFER
                }
                example("Authorization-code credential offer") {
                    value = Issuer2RequestExamples.AUTHORIZATION_CODE_CREDENTIAL_OFFER
                }
            }
        }
        response {
            HttpStatusCode.Created to {
                description = "Credential offer created"
                body<CreateCredentialOfferResponse> {
                    example("Credential offer response") {
                        value = Issuer2RequestExamples.CREDENTIAL_OFFER_RESPONSE
                    }
                }
            }
        }
    }

    fun listSessions(): RouteConfig.() -> Unit = {
        summary = "List issuance sessions"
        description = "List currently stored issuance sessions."
        response {
            HttpStatusCode.OK to {
                description = "Issuance sessions"
                body<List<IssuanceSession>>()
            }
        }
    }

    fun getSession(): RouteConfig.() -> Unit = {
        summary = "Get issuance session"
        description = "Get one issuance session by session ID."
        request {
            pathParameter<String>("sessionId")
        }
        response {
            HttpStatusCode.OK to {
                description = "Issuance session"
                body<IssuanceSession>()
            }
        }
    }

    fun sessionEvents(): RouteConfig.() -> Unit = {
        summary = "Subscribe to issuance session events"
        description = "SSE endpoint for live updates of one issuance session."
        request {
            pathParameter<String>("sessionId")
        }
    }
}
