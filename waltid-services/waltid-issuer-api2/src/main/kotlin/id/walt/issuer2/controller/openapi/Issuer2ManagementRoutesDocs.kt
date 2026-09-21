package id.walt.issuer2.controller.openapi

import id.walt.issuer2.models.CredentialOfferCreateRequest
import id.walt.issuer2.models.CredentialOfferCreateResponse
import id.walt.issuer2.models.MultiCredentialOfferCreateRequest
import id.walt.issuer2.models.MultiCredentialOfferCreateResponse
import id.walt.issuer2.domain.CredentialProfile
import id.walt.issuer2.domain.IssuanceSession
import io.github.smiley4.ktoropenapi.config.RouteConfig
import id.walt.issuer2.models.singleIssuanceSessionDescriptor
import io.github.smiley4.ktoropenapi.config.descriptors.array
import io.github.smiley4.ktoropenapi.config.descriptors.SerialTypeDescriptor
import io.github.smiley4.ktoropenapi.config.descriptors.anyOf
import io.github.smiley4.ktoropenapi.config.descriptors.type
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
            mappings, authorized transaction data types, ID token claim mappings, x5 chains,
            and webhook URLs for a single issuance session.
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

            Choose one of the following request formats:

            - Single-profile offer: send `profileId` with optional `runtimeOverrides`.
            - Multi-credential offer: send a non-empty `credentials` array. Each entry contains a
              `profileId` and optional `runtimeOverrides`. Select different profiles to offer different
              credential formats, or repeat a profile with different `credentialData` overrides to offer
              different datasets.

            The batch limit is advertised as `batch_credential_issuance.batch_size` in issuer metadata.

            When using `credentials`, put overrides inside each entry and omit top-level `profileId`
            and `runtimeOverrides` entirely, rather than setting them to null. A `profileId` request
            returns a response containing `profileId`. A `credentials` request returns the offer response
            without profile fields or a `credentials` array, even when only one entry was supplied.

            Both request formats support pre-authorized and authorization-code issuance flows, with offers
            returned by reference or by value. Runtime overrides can
            be applied per credential for one offer only. Supported
            override fields are: issuerDid, credentialData, mapping, selectiveDisclosure,
            idTokenClaimsMapping, mDocNameSpacesDataMappingConfig, authorizedTransactionDataTypes,
            msoData, x5Chain, notifications, and credentialStatus.
            runtimeOverrides.msoData is rejected unless the profile credential configuration
            format is mso_mdoc.
            For mDoc profiles, put namespace value functions in mapping (including <date>,
            <date-in>, and <date-before> for ISO full-date fields such as issue_date) and MSO
            validity functions in msoData. Top-level W3C-style mapping keys are ignored for mDoc.
            credentialData is applied as a partial object patch over the configured profile data:
            nested objects are merged, while primitive, array, and null values replace the configured value.
            Each offered credential uses its configured `credentialStatus` for all copies issued from it.
            When multiple proofs are supplied, those copies share the same status entry; revoking that
            entry revokes every credential referencing it. Supply `runtimeOverrides.credentialStatus`
            on a single-profile offer or inside each `credentials[]` item to override the profile default.
            Different items can use different entries. The caller allocates and manages status entries;
            OSS embeds them without allocating new entries per copy. Shared references make copies linkable.
            Redeem different offered items through separate Credential Requests; multiple proofs request
            copies of the selected item. Repeated issuance of an item also uses its configured status.
            Authorization-code offers include issuer_state by default. Set issuerStateMode to OMIT only
            for profile-based offers without runtime overrides. AUTHORIZED offers with runtimeOverrides and
            issuerStateMode OMIT are rejected with Bad Request.
            Offer/session expiry is configured with expiresInSeconds. The default is 5 minutes.
            Use -1 for no expiry.
        """.trimIndent()
        request {
            body(anyOf(type<CredentialOfferCreateRequest>(), type<MultiCredentialOfferCreateRequest>())) {
                example("[authorized][single][by-reference]") {
                    value = Issuer2RequestExamples.PROFILE_AUTHORIZED_OFFER_BY_REFERENCE
                }
                example("[authorized][single][by-value]") {
                    value = Issuer2RequestExamples.PROFILE_AUTHORIZED_OFFER_BY_VALUE
                }
                example("[authorized][single][by-value][issuer_state omitted]") {
                    value = Issuer2RequestExamples.PROFILE_AUTHORIZED_OFFER_BY_VALUE_WITHOUT_ISSUER_STATE
                }
                example("[authorized][single][by-reference][mDoc credentialData override]") {
                    value = Issuer2RequestExamples.AUTHORIZED_MDOC_MDL_OFFER_WITH_CREDENTIAL_DATA_OVERRIDE
                }
                example("[authorized][multiple][by-reference][same dataset, different formats][EUDI PID]") {
                    value = Issuer2RequestExamples.PROFILE_AUTHORIZED_MULTI_CREDENTIAL_OFFER_BY_REFERENCE
                }
                example("[authorized][multiple][by-value][same format, different datasets][SD-JWT VC]") {
                    value = Issuer2RequestExamples.PROFILE_AUTHORIZED_MULTI_CREDENTIAL_OFFER_BY_VALUE
                }
                example("[authorized][multiple][by-reference][runtime overrides]") {
                    value = Issuer2RequestExamples.PROFILE_AUTHORIZED_MULTI_CREDENTIAL_OFFER_WITH_RUNTIME_OVERRIDES
                }
                example("[pre-authorized][single][by-reference]") {
                    value = Issuer2RequestExamples.PROFILE_PRE_AUTHORIZED_OFFER_BY_REFERENCE
                }
                example("[pre-authorized][single][shared status][W3C]") {
                    value = Issuer2RequestExamples.PROFILE_PRE_AUTHORIZED_OFFER_WITH_SHARED_W3C_STATUS
                }
                example("[pre-authorized][single][shared status][SD-JWT]") {
                    value = Issuer2RequestExamples.PROFILE_PRE_AUTHORIZED_OFFER_WITH_SHARED_SD_JWT_STATUS
                }
                example("[pre-authorized][single][shared status][mdoc]") {
                    value = Issuer2RequestExamples.PROFILE_PRE_AUTHORIZED_OFFER_WITH_SHARED_MDOC_STATUS
                }
                example("[pre-authorized][multiple][different statuses per item]") {
                    value = Issuer2RequestExamples.PROFILE_PRE_AUTHORIZED_MULTI_CREDENTIAL_OFFER_WITH_DISTINCT_STATUSES
                }
                example("[authorized][multiple][different statuses per item]") {
                    value = Issuer2RequestExamples.PROFILE_AUTHORIZED_MULTI_CREDENTIAL_OFFER_WITH_DISTINCT_STATUSES
                }
                example("[pre-authorized][single][by-value]") {
                    value = Issuer2RequestExamples.PROFILE_PRE_AUTHORIZED_OFFER_BY_VALUE
                }
                example("[pre-authorized][single][by-reference][provided tx_code]") {
                    value = Issuer2RequestExamples.PROFILE_PRE_AUTHORIZED_OFFER_WITH_PROVIDED_TX_CODE
                }
                example("[pre-authorized][single][by-reference][generated tx_code]") {
                    value = Issuer2RequestExamples.PROFILE_PRE_AUTHORIZED_OFFER_WITH_GENERATED_TX_CODE
                }
                example("[pre-authorized][single][by-reference][expires in 2 minutes]") {
                    value = Issuer2RequestExamples.PROFILE_PRE_AUTHORIZED_OFFER_WITH_2_MIN_EXPIRY
                }
                example("[pre-authorized][single][by-reference][no expiry]") {
                    value = Issuer2RequestExamples.PROFILE_PRE_AUTHORIZED_OFFER_WITHOUT_EXPIRY
                }
                example("[pre-authorized][single][by-reference][credentialData override]") {
                    value = Issuer2RequestExamples.PROFILE_PRE_AUTHORIZED_OFFER_WITH_CREDENTIAL_DATA_OVERRIDE
                }
                example("[pre-authorized][single][by-reference][issuerKey override]") {
                    value = Issuer2RequestExamples.PROFILE_PRE_AUTHORIZED_OFFER_WITH_ISSUER_KEY_OVERRIDE
                }
                example("[pre-authorized][single][by-reference][selective disclosure override]") {
                    value = Issuer2RequestExamples.PROFILE_PRE_AUTHORIZED_OFFER_WITH_SELECTIVE_DISCLOSURE_OVERRIDE
                }
                example("[pre-authorized][single][by-reference][mDoc credentialData override]") {
                    value = Issuer2RequestExamples.PRE_AUTHORIZED_MDOC_PHOTO_ID_OFFER_WITH_CREDENTIAL_DATA_OVERRIDE
                }
                example("[pre-authorized][single][by-reference][authorized transaction types override]") {
                    value = Issuer2RequestExamples.PROFILE_PRE_AUTHORIZED_OFFER_WITH_AUTHORIZED_TRANSACTION_DATA_TYPES_OVERRIDE
                }
                example("[pre-authorized][multiple][by-reference]") {
                    value = Issuer2RequestExamples.PROFILE_PRE_AUTHORIZED_MULTI_CREDENTIAL_OFFER
                }
                example("[pre-authorized][multiple][by-value]") {
                    value = Issuer2RequestExamples.PROFILE_PRE_AUTHORIZED_MULTI_CREDENTIAL_OFFER_BY_VALUE
                }
                example("[pre-authorized][multiple][by-reference][runtime overrides]") {
                    value = Issuer2RequestExamples.PROFILE_PRE_AUTHORIZED_MULTI_CREDENTIAL_OFFER_WITH_RUNTIME_OVERRIDES
                }
            }
        }
        response {
            HttpStatusCode.Created to {
                description = "Credential offer created"
                body(anyOf(type<CredentialOfferCreateResponse>(), type<MultiCredentialOfferCreateResponse>())) {
                    example("Offer response by reference") {
                        value = Issuer2RequestExamples.CREDENTIAL_OFFER_RESPONSE_BY_REFERENCE
                    }
                    example("Offer response by value") {
                        value = Issuer2RequestExamples.CREDENTIAL_OFFER_RESPONSE_BY_VALUE
                    }
                    example("Offer response by value with issuer_state included") {
                        value = Issuer2RequestExamples.CREDENTIAL_OFFER_RESPONSE_BY_VALUE_WITH_ISSUER_STATE
                    }
                    example("Multiple-format offer response by reference") {
                        value = Issuer2RequestExamples.MULTI_CREDENTIAL_OFFER_RESPONSE_BY_REFERENCE
                    }
                    example("Multiple-dataset offer response by value") {
                        value = Issuer2RequestExamples.MULTI_CREDENTIAL_OFFER_RESPONSE_BY_VALUE
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
                body(array(anyOf(SerialTypeDescriptor(singleIssuanceSessionDescriptor), type<IssuanceSession>())))
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
                body(anyOf(SerialTypeDescriptor(singleIssuanceSessionDescriptor), type<IssuanceSession>()))
            }
        }
    }

    fun sessionEvents(): RouteConfig.() -> Unit = {
        summary = "Receive issuance session update events via Server-Sent Events (SSE)"
        description = """
            Establishes an SSE connection to receive real-time updates about an issuance session.

            Events:
            - `credential_offer_created`, `credential_offer_retrieved`
            - `pushed_authorization_request_succeeded`, `pushed_authorization_request_failed`
            - `authorization_request_succeeded`, `authorization_request_failed`
            - `token_request_<grant>_succeeded`, `token_request_<grant>_failed`
            - `credential_request_failed`
            - `credential_request_<format>_succeeded`, `credential_request_<format>_failed`
            - `issuance_status_changed`

            Token grants are `authorization_code`, `pre_authorized_code`, and `refresh_token`.
            A token request whose `grant_type` is missing, malformed, or unsupported emits
            `token_request_failed` because no supported grant can be identified.
            Credential formats are grouped as `sd_jwt_vc`, `w3c_vc`, and `mso_mdoc`. Once the
            trusted session configuration is resolved, failures use the format-specific event;
            earlier failures use `credential_request_failed`.

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

    fun issuerEvents(): RouteConfig.() -> Unit = {
        summary = "Receive issuer protocol events via Server-Sent Events (SSE)"
        description = """
            Streams protocol outcomes across the issuer. Each event contains a requestId and may contain
            a correlated issuance session. Failed events expose error and error_description directly.

            Events without session correlation are available only on this issuer-level stream. Correlated
            events are also sent to the corresponding session stream and configured session webhook.
            Nonce request events are always uncorrelated and are available only on this issuer-level stream.
        """.trimIndent()
        response {
            HttpStatusCode.OK to {
                description = "SSE connection established. Events are streamed as text/event-stream."
                body<String>()
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
        "openBadgeCredential",
        "identityCredentialSdJwt",
        "isoPhotoId",
    )
    private const val MAX_PROFILE_EXAMPLES = 3
}
