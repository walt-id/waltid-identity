package id.walt.issuer2.controller.openapi

import id.walt.issuer2.models.CredentialOfferCreateRequest
import id.walt.issuer2.models.CredentialOfferCreateResponse
import id.walt.issuer2.models.MultiCredentialOfferCreateRequest
import id.walt.issuer2.models.MultiCredentialOfferCreateResponse
import id.walt.issuer2.domain.CredentialProfile
import id.walt.issuer2.models.withRedactedIssuerKey
import io.github.smiley4.ktoropenapi.config.RouteConfig
import id.walt.issuer2.models.singleIssuanceSessionDescriptor
import id.walt.issuer2.models.multiIssuanceSessionDescriptor
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
            and webhook URLs for a single issuance session. Each profile reports its signing
            key as `{"type":"redacted"}`.
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

            The profile reports its signing key as `{"type":"redacted"}`.
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
            - Immediate authorization-code offers default to `issuerStateMode: OMIT`, matching Enterprise. Missing or null
              `issuerStateMode` omits `issuer_state` in both request formats and both delivery modes.
            - Set `deferred: true` to require backend approval using the returned `offerId`. Complete or deny
              with a bodyless POST to `/issuer2/credential-offers/{offerId}/deferred/complete` or `/deny`.
              Completion does not sign credentials. Deferred authorization-code offers default to INCLUDE
              and reject explicit OMIT. The seven-day deferred deadline begins at first token issuance.
            - For immediate offers, runtime overrides require explicit `issuerStateMode: INCLUDE` for authorization-code offers.
              Missing, null, or `OMIT` with overrides returns Bad Request.
            - Migration: send `"issuerStateMode": "INCLUDE"` to preserve previous OSS behavior and recover
              the original offer session. Without it, authorization creates a profile-derived session.
            - `expiresInSeconds` defaults to `300` (five minutes). Use `-1` for no expiry.

            **Runtime overrides**

            Overrides apply to this offer only. Omitted fields inherit the profile. `credentialData` is a
            partial patch: nested objects merge; primitive, array, and null values replace existing values.

            See [supported override fields](https://docs.walt.id/community-stack/issuer2/credential-offers/create-offer#available-override-fields).

            **mDoc mapping and validity**

            - Put namespace data functions such as `<date>`, `<date-in:365d>`, and `<date-before:30d>` in `mapping`.
              Put MSO validity functions in `msoData`. Offer creation returns HTTP 400 for top-level validity fields in `mapping`.
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
            - A status object or flat W3C status array retains fixed, shared behavior: copies and repeated
              issuance share the configured value. Shared status references make those copies linkable.
            - For SD-JWT VC/mdoc, a flat `credentialStatus` array supplies one complete IETF status value
              per copy. For W3C, nested arrays supply one group per copy, containing one or more status
              entries such as revocation and suspension. No `mode` or additional field is needed.
            - Preallocated groups require Redis persistence. Consumption is atomic and shared across
              profiles and sessions using the same Redis consumption ledger. Each reference retains
              its owning profile and issuance operation; another operation cannot reuse it.
              Insufficient remaining groups return
              `invalid_credential_request` before signing, without partially consuming a batch.
              Retries retain pending assignments; successful subsequent requests consume fresh groups.
            - Profile-configured groups work with offered and offerless authorization sessions.
              Sessions snapshot their configuration. Replenish profiles with fresh groups and restart
              the service without clearing Redis. Changing profile IDs does not reset consumption.
            - Override status with `runtimeOverrides.credentialStatus` on the single-profile request or each
              `credentials[]` entry. The caller allocates and manages status entries; OSS embeds the selected values.

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
        description = "List currently stored issuance sessions with issuer signing keys redacted."
        response {
            HttpStatusCode.OK to {
                description = "Issuance sessions"
                body(array(anyOf(SerialTypeDescriptor(singleIssuanceSessionDescriptor), SerialTypeDescriptor(multiIssuanceSessionDescriptor))))
            }
        }
    }

    fun getSession(): RouteConfig.() -> Unit = {
        summary = "Get issuance session"
        description = "Get one issuance session by session ID with issuer signing keys redacted."
        request {
            pathParameter<String>("sessionId")
        }
        response {
            HttpStatusCode.OK to {
                description = "Issuance session"
                body(anyOf(SerialTypeDescriptor(singleIssuanceSessionDescriptor), SerialTypeDescriptor(multiIssuanceSessionDescriptor)))
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
            .map(CredentialProfile::withRedactedIssuerKey)
    }

    private val preferredProfileExampleIds = listOf(
        "openBadgeCredential",
        "identityCredentialSdJwt",
        "isoPhotoId",
    )
    private const val MAX_PROFILE_EXAMPLES = 3
}
