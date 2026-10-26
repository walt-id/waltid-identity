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

            **Request format**

            - Single-profile offer: send `profileId` with optional `runtimeOverrides`.
            - Multi-credential offer: send a non-empty `credentials` array, with `profileId` and optional
              `runtimeOverrides` in each entry. Omit top-level `profileId` and `runtimeOverrides`, including null values.
            - Use different profiles for different formats, or repeat a profile with different data overrides.
              A multi-credential response has no `profileId` or `credentials` array, even for one entry.

            See [offer examples](https://docs.walt.id/community-stack/issuer2/credential-offers/create-offer#multiple-credentials).

            **Flow and expiry**

            - Both request formats support pre-authorized and authorization-code flows, by reference or by value.
            - Authorization-code offers include `issuer_state` by default. `issuerStateMode: OMIT` is allowed
              only for profile-based offers without runtime overrides; combining it with overrides returns Bad Request.
            - `expiresInSeconds` defaults to `300` (five minutes). Use `-1` for no expiry.

            **Runtime overrides**

            Overrides apply to this offer only. Omitted fields inherit the profile. `credentialData` is a
            partial patch: nested objects merge; primitive, array, and null values replace existing values.

            See [supported override fields](https://docs.walt.id/community-stack/issuer2/credential-offers/create-offer#available-override-fields).

            **mDoc mapping and validity**

            - Put namespace data functions such as `<date>`, `<date-in:365d>`, and `<date-before:30d>` in `mapping`.
              Put MSO validity functions in `msoData`; `mapping.validFrom` and `mapping.validUntil` are rejected.
            - Non-empty `runtimeOverrides.msoData` is accepted only for `mso_mdoc`. Blank validity values are rejected.
              Null `expectedUpdate` clears the profile value; null `validFrom` or `validUntil` inherits it.
            - Validity must satisfy `signed <= validFrom < validUntil`. `expectedUpdate` is optional;
              this issuer does not require it to fall within that window.
            - Without a configured `validUntil`, the default is 365 days, rounded to a 12-hour UTC bucket
              when rounding is enabled. Explicit `validUntil` timestamps and resolved data functions are not rounded.

            See [MSO validity configuration](https://docs.walt.id/community-stack/issuer2/configurations/config-files/issuer-profiles#mso-validity-msodata).

            **Multiple credentials, batches, and status**

            - Redeem different offer items through separate Credential Requests. Multiple proofs request
              copies of one selected item, subject to the advertised `batch_credential_issuance.batch_size` limit.
            - Copies and repeated issuance of an item share its configured `credentialStatus` entry.
              Revoking that entry revokes every credential referencing it; shared references also make copies linkable.
            - Override status with `runtimeOverrides.credentialStatus` on the single-profile request or each
              `credentials[]` entry. The caller allocates and manages status entries; OSS only embeds them.

            See [credential status and batch issuance](https://docs.walt.id/community-stack/issuer2/credential-offers/credential-status#batch-issuance).
        """.trimIndent()
        request {
            body(anyOf(type<CredentialOfferCreateRequest>(), type<MultiCredentialOfferCreateRequest>())) {
                Issuer2RequestExamples.credentialOfferRequestExamples().forEach { (name, exampleValue) ->
                    example(name) {
                        value = exampleValue
                    }
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
