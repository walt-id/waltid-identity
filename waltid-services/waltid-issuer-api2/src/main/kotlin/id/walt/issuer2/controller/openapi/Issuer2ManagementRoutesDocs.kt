package id.walt.issuer2.controller.openapi

import id.walt.issuer2.controller.dto.CreateCredentialOfferRequest
import id.walt.issuer2.controller.dto.CreateCredentialOfferResponse
import id.walt.issuer2.domain.CredentialProfile
import id.walt.issuer2.domain.IssuanceSession
import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.ktor.http.HttpStatusCode

object Issuer2ManagementRoutesDocs {
    fun listProfiles(profileExamples: List<CredentialProfile>): RouteConfig.() -> Unit = {
        summary = "List credential profiles"
        description = """
            List credential profiles loaded from issuer2-profiles.conf.

            Profiles are deployment templates used by credential-offer creation. Use the
            returned profileId in POST /issuer2/credential-offers. Runtime overrides may
            provide credential data, mappings, selective disclosure, mDOC namespace data
            mappings, ID token claim mappings, x5 chains, and webhook URLs for a single
            issuance session.
        """.trimIndent()
        response {
            HttpStatusCode.OK to {
                description = "Configured credential profiles"
                body<List<CredentialProfile>> {
                    example("Configured W3C, SD-JWT VC, and mDOC profiles") {
                        value = profileExamples
                    }
                }
            }
        }
    }

    fun getProfile(profileExamples: List<CredentialProfile>): RouteConfig.() -> Unit = {
        summary = "Get credential profile"
        description = """
            Get one credential profile by profile ID.

            The response is the exact profile loaded from issuer2-profiles.conf, including
            issuer key material for this first OSS issuer2 version.
        """.trimIndent()
        request {
            pathParameter<String>("profileId") {
                description = "Credential profile ID from GET /issuer2/profiles"
                profileExamples.firstOrNull()?.let { example("Profile ID") { value = it.profileId } }
            }
        }
        response {
            HttpStatusCode.OK to {
                description = "Credential profile"
                body<CredentialProfile> {
                    profileExamples.forEach { profile ->
                        example("${profile.profileId} profile") {
                            value = profile
                        }
                    }
                }
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

    internal fun selectProfileExamples(profiles: List<CredentialProfile>): List<CredentialProfile> {
        val byId = profiles.associateBy { it.profileId }
        val preferred = preferredProfileExampleIds.mapNotNull(byId::get)
        return (preferred + profiles.filterNot { it.profileId in preferredProfileExampleIds })
            .distinctBy { it.profileId }
            .take(MAX_PROFILE_EXAMPLES)
    }

    private val preferredProfileExampleIds = listOf(
        "universityDegree",
        "identityCredentialSdJwt",
        "isoPhotoId",
    )
    private const val MAX_PROFILE_EXAMPLES = 3
}