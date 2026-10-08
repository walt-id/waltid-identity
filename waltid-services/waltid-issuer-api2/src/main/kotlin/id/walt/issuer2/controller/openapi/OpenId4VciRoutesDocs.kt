package id.walt.issuer2.controller.openapi

import id.walt.openid4vci.errors.CredentialError
import id.walt.openid4vci.errors.OAuthError
import id.walt.openid4vci.metadata.issuer.CredentialIssuerMetadataJwt
import id.walt.openid4vci.requests.credential.encryption.CredentialEncryptionProfile
import id.walt.openid4vci.responses.par.PushedAuthorizationResponse
import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.JsonObject

object OpenId4VciRoutesDocs {
    const val OPENID4VCI_TAG = "Issuer Service API v2 - OpenID4VCI"

    fun credentialIssuerMetadata(): RouteConfig.() -> Unit = {
        tags = listOf(OPENID4VCI_TAG)
        summary = "Get Credential Issuer metadata"
        request {
            headerParameter<String>("Accept") {
                required = false
                description =
                    "Use application/jwt or application/openidvci-issuer-metadata+jwt for signed metadata; defaults to application/json."
            }
        }
        response {
            HttpStatusCode.OK to {
                description = "Unsigned JSON or signed OpenID4VCI Credential Issuer metadata"
                body<JsonObject> {
                    mediaTypes(ContentType.Application.Json)
                }
                body<String> {
                    mediaTypes(
                        ContentType.parse(CredentialIssuerMetadataJwt.MEDIA_TYPE),
                        ContentType.parse(CredentialIssuerMetadataJwt.TYPED_MEDIA_TYPE),
                    )
                }
            }
        }
    }

    fun authorizationServerMetadata(): RouteConfig.() -> Unit = {
        tags = listOf(OPENID4VCI_TAG)
        summary = "Get Authorization Server metadata"
        response {
            HttpStatusCode.OK to {
                description = "OAuth Authorization Server metadata"
                body<JsonObject>()
            }
        }
    }

    fun jwtVcIssuerMetadata(): RouteConfig.() -> Unit = {
        tags = listOf(OPENID4VCI_TAG)
        summary = "Get SD-JWT VC issuer metadata"
        response {
            HttpStatusCode.OK to {
                description = "SD-JWT VC issuer metadata"
                body<JsonObject>()
            }
        }
    }

    fun vctTypeMetadata(): RouteConfig.() -> Unit = {
        tags = listOf(OPENID4VCI_TAG)
        summary = "Get SD-JWT VC type metadata"
        description = "Resolve configured self-hosted SD-JWT VC type metadata through " +
                "`/.well-known/vct/{type}` or `/openid4vci/{type}`. Unknown types return 404. " +
                "OpenID4VCI protocol path names are reserved and cannot be used for self-hosted VCT URLs."
        request {
            pathParameter<String>("type")
        }
        response {
            HttpStatusCode.OK to {
                description = "SD-JWT VC type metadata"
                body<JsonObject>()
            }
            HttpStatusCode.NotFound to {
                description = "The type has no self-hosted metadata or uses a reserved protocol path name."
            }
        }
    }

    fun jwks(): RouteConfig.() -> Unit = {
        summary = "Get issuer JWKS"
        response {
            HttpStatusCode.OK to {
                description = "Issuer public keys"
                body<JsonObject>()
            }
        }
    }

    fun credentialOffer(): RouteConfig.() -> Unit = {
        summary = "Get credential offer"
        description = "Resolve a credential offer by issuance session ID."
        request {
            queryParameter<String>("id")
        }
        response {
            HttpStatusCode.OK to {
                description = "Credential offer"
                body<JsonObject>()
            }
        }
    }

    fun pushedAuthorizationRequest(): RouteConfig.() -> Unit = {
        summary = "Pushed Authorization Request (PAR)"
        description = "RFC 9126 PAR endpoint: submit authorization parameters and receive a request_uri."
        request {
            body<Map<String, List<String>>> {
                description = "Authorization request parameters (form-encoded)"
                mediaTypes(ContentType.Application.FormUrlEncoded)
            }
        }
        response {
            HttpStatusCode.Created to {
                description = "PAR response with request_uri"
                body<PushedAuthorizationResponse>()
            }
            HttpStatusCode.BadRequest to {
                description = "Invalid PAR request"
                body<OAuthError>()
            }
            HttpStatusCode.InternalServerError to {
                description = "PAR processing failed"
                body<OAuthError>()
            }
        }
    }

    fun authorize(): RouteConfig.() -> Unit = {
        summary = "Authorization endpoint"
        description = "The authorization endpoint"
        response {
            HttpStatusCode.Found to {
                description = "Redirect containing authorization response parameters"
            }
        }
    }

    fun externalOAuthCallback(): RouteConfig.() -> Unit = {
        hidden = true
    }

    fun externalLogin(): RouteConfig.() -> Unit = {
        hidden = true
    }

    fun token(): RouteConfig.() -> Unit = {
        summary = "Token endpoint"
        description = "The token endpoint. A DPoP proof binds the issued access token to the proof key."
        request {
            headerParameter<String>("DPoP") {
                required = false
                description = "RFC 9449 DPoP proof JWT for this token request"
            }
        }
        response {
            HttpStatusCode.OK to {
                description = "Access token response"
                body<JsonObject>()
            }
            HttpStatusCode.BadRequest to {
                description = "Invalid token request"
                body<OAuthError>()
            }
            HttpStatusCode.Unauthorized to {
                description = "Client authentication failed"
                body<OAuthError>()
            }
            HttpStatusCode.InternalServerError to {
                description = "Token processing failed"
                body<OAuthError>()
            }
        }
    }

    fun credential(): RouteConfig.() -> Unit = {
        summary = "Credential endpoint"
        description = """
            Issue one or more credentials for a single credential target. Plaintext JSON and encrypted
            JWT requests are accepted.

            Select one authorized credential target using either `credential_identifier` or
            `credential_configuration_id`. All credentials returned by one request share that target's
            format and dataset. Send separate requests for different formats or datasets.

            When sending `proofs`, choose one proof type supported by the selected configuration's
            `proof_types_supported`; do not combine `jwt` and `attestation` in the same request.

            - `proofs.jwt`: an array of holder-signed JWTs with protected header `typ: openid4vci-proof+jwt`.
              The nested-attestation examples include the holder's public `jwk` (or DID URL `kid` for W3C) and the attester-signed
              compact JWT in the protected `key_attestation` header. The outer signing key must appear
              in that attestation's `attested_keys`. The outer payload has an integer `iat`, the issuer's
              `nonce`, and an `aud` string exactly matching the Credential Issuer Identifier; audience
              arrays are rejected. If `iss` is supplied, use the wallet's client ID; omit it for anonymous
              pre-authorized access. A nested attestation is required when JWT metadata includes
              `key_attestations_required`, and optional otherwise.
            - `proofs.attestation`: an array containing exactly one attester-signed key-attestation JWT.
              It has no outer holder signature. Its protected header has `typ: key-attestation+jwt`,
              and its payload contains `iat`, `nonce`, and a non-empty `attested_keys` array of public JWKs.
              The examples also include `exp`, which is required for nested key attestations.

            Obtain `c_nonce` from the advertised `nonce_endpoint` before requesting an attestation
            from the Wallet Provider, and put that value in the attestation's `nonce`. For nested
            attestations, the outer proof must also carry a valid issuer nonce. Use algorithms advertised
            for the selected proof type; both signatures in the nested examples use ES256.
            Both attestation approaches use `keyAttestationConfig` trust, independently of OAuth
            client-attestation trust. With X.509 trust, the attester supplies its leaf-first `x5c` chain.

            W3C JWT credentials require a verified holder DID for each selected key. The W3C examples
            put a DID verification method URL in each attested JWK's `kid`. This library convention
            is supported for both nested and standalone attestations; OpenID4VCI does not prescribe it.
            The issuer resolves each reference, checks the public key matches the attested JWK,
            and requires the DID method to be advertised in `cryptographic_binding_methods_supported`.
            The attestation's header `kid` or `x5c` identifies its signer and cannot supply a holder DID.

            For plain JWT proofs, each proof requests one credential; multiple proofs require
            `batch_credential_issuance` and must respect its advertised `batch_size`.
            For either attestation approach, this issuer selects each distinct attested holder key.
            The attestation examples carry two attested keys
            in one attestation and therefore request two credentials, including when nested in one JWT.

            Examples are illustrative and cannot be submitted unchanged. The attestation examples
            have decodable JWT headers and payloads, but placeholder signatures and an `x5c` certificate
            placeholder. Obtain a real signed attestation for your wallet keys, use fresh timestamps
            and nonces, replace the issuer URL and credential selector, and sign any outer JWT proof.
            A `credential_identifier` from the token response can replace `credential_configuration_id`.
            See [OpenID4VCI proof types](https://openid.net/specs/openid-4-verifiable-credential-issuance-1_0-final.html#appendix-F).

            Multiple proofs when batch issuance is disabled, or more proofs than the advertised limit,
            return `invalid_credential_request`. Invalid proof signatures return `invalid_proof`.
            Each offered credential uses its configured `credentialStatus` for all copies issued from it.
            When multiple proofs are supplied, those copies share the same status entry. Revoking that
            entry revokes every credential referencing it, including copies issued by later requests.
            Shared status references also make the copies linkable.
            Different `credentials[]` items can supply different statuses, each used for its corresponding item.
            These rules apply to both single-profile and multi-credential offers.

            When several authorized datasets share a configuration, selecting it by
            `credential_configuration_id` is ambiguous and returns `invalid_credential_request`.
            Request `authorization_details` during authorization or token exchange to obtain dataset
            identifiers, then send a separate request using `credential_identifier` for each dataset.
        """.trimIndent()
        request {
            headerParameter<String>("Authorization") {
                required = true
                description = "Bearer or DPoP access-token authorization"
            }
            headerParameter<String>("DPoP") {
                required = false
                description = "Required when presenting a DPoP-bound access token"
            }
            body<JsonObject> {
                description = "Credential request"
                mediaTypes(ContentType.Application.Json)
                example("[SD-JWT][jwt + key_attestation][two attested keys]") {
                    value = Issuer2RequestExamples.SD_JWT_CREDENTIAL_REQUEST_WITH_KEY_ATTESTATION
                }
                example("[mdoc][jwt + key_attestation][two attested keys]") {
                    value = Issuer2RequestExamples.MDOC_CREDENTIAL_REQUEST_WITH_KEY_ATTESTATION
                }
                example("[SD-JWT][attestation][two attested keys]") {
                    value = Issuer2RequestExamples.SD_JWT_CREDENTIAL_REQUEST_WITH_ATTESTATION_PROOF
                }
                example("[mdoc][attestation][two attested keys]") {
                    value = Issuer2RequestExamples.MDOC_CREDENTIAL_REQUEST_WITH_ATTESTATION_PROOF
                }
                example("[W3C][jwt + key_attestation][two holder DIDs]") {
                    value = Issuer2RequestExamples.W3C_CREDENTIAL_REQUEST_WITH_KEY_ATTESTATION
                }
                example("[W3C][attestation][two holder DIDs]") {
                    value = Issuer2RequestExamples.W3C_CREDENTIAL_REQUEST_WITH_ATTESTATION_PROOF
                }
                example("[batch][credential_configuration_id][two JWT proofs]") {
                    value = Issuer2RequestExamples.BATCH_CREDENTIAL_REQUEST_BY_CONFIGURATION_ID
                }
                example("[batch][credential_identifier][two JWT proofs]") {
                    value = Issuer2RequestExamples.BATCH_CREDENTIAL_REQUEST_BY_CREDENTIAL_IDENTIFIER
                }
            }
            body<String> {
                description = "Encrypted Credential Request as compact JWE"
                mediaTypes(ContentType.parse(CredentialEncryptionProfile.MEDIA_TYPE_JWT))
            }
        }
        response {
            HttpStatusCode.OK to {
                description = "Credential response"
                body<JsonObject> {
                    example("Batch credential response") {
                        value = Issuer2RequestExamples.BATCH_CREDENTIAL_RESPONSE
                    }
                }
                body<String> {
                    mediaTypes(ContentType.parse(CredentialEncryptionProfile.MEDIA_TYPE_JWT))
                }
            }
            HttpStatusCode.BadRequest to {
                description = "Invalid credential request"
                body<CredentialError>()
            }
            HttpStatusCode.Unauthorized to {
                description = "Credential authorization failed"
                body<OAuthError>()
            }
            HttpStatusCode.Forbidden to {
                description = "Credential access is insufficiently scoped"
                body<OAuthError>()
            }
            HttpStatusCode.UnsupportedMediaType to {
                description = "Unsupported credential request media type"
                body<CredentialError>()
            }
            HttpStatusCode.InternalServerError to {
                description = "Credential processing failed"
                body<OAuthError>()
            }
        }
    }

    fun nonce(): RouteConfig.() -> Unit = {
        summary = "Nonce endpoint"
        description = "Return a signed OpenID4VCI c_nonce. This endpoint is not protected by an access token."
        response {
            HttpStatusCode.OK to {
                description = "Nonce response"
                body<JsonObject>()
            }
            HttpStatusCode.InternalServerError to {
                description = "Nonce generation failed"
                body<OAuthError>()
            }
        }
    }
}
