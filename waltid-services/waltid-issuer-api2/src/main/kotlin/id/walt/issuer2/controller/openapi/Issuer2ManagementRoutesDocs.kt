package id.walt.issuer2.controller.openapi

import id.walt.issuer2.models.CredentialOfferCreateRequest
import id.walt.issuer2.models.CredentialOfferCreateResponse
import id.walt.issuer2.domain.CredentialProfile
import id.walt.issuer2.domain.IssuanceSession
import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.ktor.http.HttpStatusCode

object Issuer2ManagementRoutesDocs {
    const val CREDENTIAL_ISSUANCE_TAG = "Issuer Service API v2 - Credential Issuance"

    fun listProfiles(profileExamples: List<CredentialProfile>): RouteConfig.() -> Unit = {
        summary = "List credential profiles"
        description = """
            List credential profiles loaded from issuer2-profiles.conf.

            Profiles are deployment templates used by credential-offer creation. Use the
            returned profileId in POST /issuer2/credential-offers. Runtime overrides may
            provide credential data, mappings, selective disclosure, mDoc namespace data
            mappings, ID token claim mappings, x5 chains, and webhook URLs for a single
            issuance session.
        """.trimIndent()
        response {
            HttpStatusCode.OK to {
                description = "Configured credential profiles"
                body<List<CredentialProfile>> {
                    example("Configured W3C, SD-JWT VC, and mDoc profiles") {
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
        description = """
            Create a profile-derived OpenID4VCI credential offer URL and the backing issuance session.

            Supports pre-authorized and authorization-code issuance flows. The offer can be returned
            by reference or by value. Runtime overrides can be applied for one offer only. Supported
            override fields are: issuerDid, credentialData, mapping, selectiveDisclosure,
            idTokenClaimsMapping, mDocNameSpacesDataMappingConfig, x5Chain, and notifications.
            credentialData is applied as a partial object patch over the configured profile data:
            nested objects are merged, while primitive, array, and null values replace the configured value.
            Authorization-code offers include issuer_state by default. Set issuerStateMode to OMIT only
            for profile-based offers without runtime overrides.
            Offer/session expiry is configured with expiresInSeconds. The default is 5 minutes.
            Use -1 for no expiry.
        """.trimIndent()
        request {
            body<CredentialOfferCreateRequest> {
                example("[authorized][by-reference]") {
                    value = Issuer2RequestExamples.PROFILE_AUTHORIZED_OFFER_BY_REFERENCE
                }
                example("[authorized][by-value]") {
                    value = Issuer2RequestExamples.PROFILE_AUTHORIZED_OFFER_BY_VALUE
                }
                example("[authorized][by-value][issuer_state omitted]") {
                    value = Issuer2RequestExamples.PROFILE_AUTHORIZED_OFFER_BY_VALUE_WITHOUT_ISSUER_STATE
                }
                example("[pre-authorized][by-reference]") {
                    value = Issuer2RequestExamples.PROFILE_PRE_AUTHORIZED_OFFER_BY_REFERENCE
                }
                example("[pre-authorized][by-value]") {
                    value = Issuer2RequestExamples.PROFILE_PRE_AUTHORIZED_OFFER_BY_VALUE
                }
                example("[pre-authorized][by-reference][provided tx_code]") {
                    value = Issuer2RequestExamples.PROFILE_PRE_AUTHORIZED_OFFER_WITH_PROVIDED_TX_CODE
                }
                example("[pre-authorized][by-reference][generated tx_code]") {
                    value = Issuer2RequestExamples.PROFILE_PRE_AUTHORIZED_OFFER_WITH_GENERATED_TX_CODE
                }
                example("[pre-authorized][by-reference][expires in 2 minutes]") {
                    value = Issuer2RequestExamples.PROFILE_PRE_AUTHORIZED_OFFER_WITH_2_MIN_EXPIRY
                }
                example("[pre-authorized][by-reference][no expiry]") {
                    value = Issuer2RequestExamples.PROFILE_PRE_AUTHORIZED_OFFER_WITHOUT_EXPIRY
                }
                example("[pre-authorized][by-reference][override credentialData]") {
                    value = Issuer2RequestExamples.PROFILE_PRE_AUTHORIZED_OFFER_WITH_CREDENTIAL_DATA_OVERRIDE
                }
                example("[pre-authorized][by-reference][override issuerKey]") {
                    value = Issuer2RequestExamples.PROFILE_PRE_AUTHORIZED_OFFER_WITH_ISSUER_KEY_OVERRIDE
                }
                example("[pre-authorized][by-reference][override selective disclosure]") {
                    value = Issuer2RequestExamples.PROFILE_PRE_AUTHORIZED_OFFER_WITH_SELECTIVE_DISCLOSURE_OVERRIDE
                }
                example("[pre-authorized][by-reference][override mDoc photo ID credentialData]") {
                    value = Issuer2RequestExamples.PRE_AUTHORIZED_MDOC_PHOTO_ID_OFFER_WITH_CREDENTIAL_DATA_OVERRIDE
                }
                example("[authorized][by-reference][override mDoc mDL credentialData]") {
                    value = Issuer2RequestExamples.AUTHORIZED_MDOC_MDL_OFFER_WITH_CREDENTIAL_DATA_OVERRIDE
                }
            }
        }
        response {
            HttpStatusCode.Created to {
                description = "Credential offer created"
                body<CredentialOfferCreateResponse> {
                    example("Offer response by reference") {
                        value = Issuer2RequestExamples.CREDENTIAL_OFFER_RESPONSE_BY_REFERENCE
                    }
                    example("Offer response by value") {
                        value = Issuer2RequestExamples.CREDENTIAL_OFFER_RESPONSE_BY_VALUE
                    }
                    example("Offer response by value with issuer_state included") {
                        value = Issuer2RequestExamples.CREDENTIAL_OFFER_RESPONSE_BY_VALUE_WITH_ISSUER_STATE
                    }
                    example("Pre-authorized offer response with generated tx_code") {
                        value = Issuer2RequestExamples.PRE_AUTHORIZED_CREDENTIAL_OFFER_RESPONSE_WITH_GENERATED_TX_CODE
                    }
                    example("Pre-authorized offer response with provided tx_code") {
                        value = Issuer2RequestExamples.PRE_AUTHORIZED_CREDENTIAL_OFFER_RESPONSE_WITH_PROVIDED_TX_CODE
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
        summary = "Receive issuance session update events via Server-Sent Events (SSE)"
        description = """
            Establishes an SSE connection to receive real-time updates about an issuance session.

            Events:
            - `resolved_credential_offer` - Wallet has resolved the credential offer
            - `requested_token` - Wallet has requested an access token
            - `sdjwt_issue` - SD-JWT VC credential has been issued
            - `jwt_issue` - JWT VC credential has been issued
            - `generated_mdoc` - mDoc credential has been generated
            - `issuance_status` - Session status has changed

            Events use the same KtorSessionUpdate envelope as webhook notifications.
        """.trimIndent()
        request {
            pathParameter<String>("sessionId") {
                description = "Issuance session identifier returned as offerId when creating a credential offer"
                example("Session ID") {
                    value = "550e8400-e29b-41d4-a716-446655440000"
                }
            }
        }
        response {
            HttpStatusCode.OK to {
                description = "SSE connection established. Events are streamed as text/event-stream."
                body<String> {
                    description = "Server-Sent Events stream containing issuance session update events"
                }
            }
            HttpStatusCode.NotFound to {
                description = "Issuance session not found"
            }
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