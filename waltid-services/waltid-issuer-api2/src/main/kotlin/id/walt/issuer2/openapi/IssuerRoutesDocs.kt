package id.walt.issuer2.openapi

import id.walt.issuer2.CredentialOfferCreateRequestBody
import id.walt.issuer2.ProfileDetails
import id.walt.issuer2.ProfileSummary
import id.walt.issuer2.models.*
import id.walt.ktornotifications.core.KtorSessionNotifications
import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import id.walt.openid4vci.metadata.issuer.CredentialIssuerMetadata
import id.walt.openid4vci.metadata.oauth.AuthorizationServerMetadata
import id.walt.openid4vci.offers.CredentialOffer
import id.walt.openid4vci.offers.TxCode
import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.ktor.http.*
import kotlinx.serialization.json.JsonObject

object IssuerRoutesDocs {

    fun getCredentialIssuerMetadataDocs(): RouteConfig.() -> Unit = {
        summary = "Credential Issuer Metadata"
        description = """
            Returns the OpenID4VCI Credential Issuer Metadata as defined in the OpenID4VCI 1.0 specification.
            
            This endpoint provides information about:
            - The credential issuer identifier
            - Supported credential configurations
            - Credential endpoint URL
            - Nonce endpoint URL
            - Display metadata for the issuer
            
            Wallets use this metadata to discover the issuer's capabilities and supported credential types.
        """.trimIndent()
        response {
            HttpStatusCode.OK to {
                description = "Credential Issuer Metadata"
                body<CredentialIssuerMetadata>()
            }
        }
    }

    fun getAuthorizationServerMetadataDocs(): RouteConfig.() -> Unit = {
        summary = "Authorization Server Metadata"
        description = """
            Returns the OAuth 2.0 Authorization Server Metadata as defined in RFC 8414.
            
            This endpoint provides information about:
            - The authorization server issuer identifier
            - Authorization endpoint URL
            - Token endpoint URL
            - Supported grant types (authorization_code, pre-authorized_code)
            - Supported response types
            
            Wallets use this metadata to understand how to authenticate and obtain access tokens.
        """.trimIndent()
        response {
            HttpStatusCode.OK to {
                description = "Authorization Server Metadata"
                body<AuthorizationServerMetadata>()
            }
        }
    }

    fun getOpenIdConfigurationDocs(): RouteConfig.() -> Unit = {
        summary = "OpenID Provider Metadata"
        description = """
            Returns the OpenID Provider Configuration (same as Authorization Server Metadata).
            
            This is an alias endpoint for compatibility with OpenID Connect clients.
        """.trimIndent()
        response {
            HttpStatusCode.OK to {
                description = "OpenID Provider Configuration"
                body<AuthorizationServerMetadata>()
            }
        }
    }

    fun getCredentialOfferDocs(): RouteConfig.() -> Unit = {
        summary = "Get Credential Offer"
        description = """
            Retrieve a credential offer by session ID.
            
            This endpoint is used when the credential offer was created with `valueMode: BY_REFERENCE`.
            The wallet resolves the credential_offer_uri to this endpoint to retrieve the actual offer.
            
            **Flow:**
            1. Issuer creates offer with BY_REFERENCE mode
            2. Wallet scans QR code containing credential_offer_uri
            3. Wallet calls this endpoint to get the actual credential offer
            4. Wallet proceeds with the OpenID4VCI flow
        """.trimIndent()
        request {
            queryParameter<String>("id") {
                description = "Session ID (the offer ID returned when creating a credential offer)"
                required = true
                example("Session ID") {
                    value = "550e8400-e29b-41d4-a716-446655440000"
                }
            }
        }
        response {
            HttpStatusCode.OK to {
                description = "Credential offer retrieved successfully"
                body<CredentialOffer>()
            }
            HttpStatusCode.NotFound to {
                description = "Session not found"
            }
            HttpStatusCode.Gone to {
                description = "Session expired"
            }
            HttpStatusCode.BadRequest to {
                description = "Missing session id"
            }
        }
    }

    fun getAuthorizationEndpointDocs(): RouteConfig.() -> Unit = {
        summary = "Authorization Endpoint"
        description = """
            OAuth 2.0 Authorization Endpoint for the authorization code flow.
            
            This endpoint handles authorization requests from wallets when using the 
            authorization code grant type. It validates the request and issues an 
            authorization code that can be exchanged for an access token.
            
            **Supported Parameters:**
            - client_id: Client identifier
            - response_type: Must be "code"
            - redirect_uri: Where to redirect after authorization
            - scope: Requested scopes
            - state: Client state for CSRF protection
            - issuer_state: Links to the credential offer session
            - code_challenge: PKCE code challenge
            - code_challenge_method: PKCE method (S256)
        """.trimIndent()
        request {
            queryParameter<String>("client_id") {
                description = "Client identifier"
                required = true
            }
            queryParameter<String>("response_type") {
                description = "Must be 'code'"
                required = true
            }
            queryParameter<String>("redirect_uri") {
                description = "Redirect URI for the authorization response"
                required = false
            }
            queryParameter<String>("scope") {
                description = "Requested scopes"
                required = false
            }
            queryParameter<String>("state") {
                description = "Client state for CSRF protection"
                required = false
            }
            queryParameter<String>("issuer_state") {
                description = "Links to the credential offer session"
                required = false
            }
            queryParameter<String>("code_challenge") {
                description = "PKCE code challenge"
                required = false
            }
            queryParameter<String>("code_challenge_method") {
                description = "PKCE method (S256)"
                required = false
            }
        }
    }

    fun getTokenEndpointDocs(): RouteConfig.() -> Unit = {
        summary = "Token Endpoint"
        description = """
            OAuth 2.0 Token Endpoint for exchanging authorization codes or pre-authorized codes for access tokens.
            
            **Supported Grant Types:**
            - `authorization_code`: Exchange an authorization code for an access token
            - `urn:ietf:params:oauth:grant-type:pre-authorized_code`: Exchange a pre-authorized code for an access token
            
            **Pre-authorized Code Flow:**
            When using the pre-authorized code grant, include:
            - grant_type: urn:ietf:params:oauth:grant-type:pre-authorized_code
            - pre-authorized_code: The code from the credential offer
            - tx_code: Transaction code if required by the offer
        """.trimIndent()
        request {
            body<String> {
                description = "URL-encoded form data with grant_type and code parameters"
            }
        }
        response {
            HttpStatusCode.OK to {
                description = "Access token response"
                body<JsonObject>()
            }
            HttpStatusCode.BadRequest to {
                description = "Invalid request or invalid grant"
                body<JsonObject>()
            }
        }
    }

    fun getNonceEndpointDocs(): RouteConfig.() -> Unit = {
        summary = "Nonce Endpoint"
        description = """
            Returns a fresh c_nonce for credential requests.
            
            The nonce is used to bind the credential to the holder's key proof.
            Wallets must include this nonce in their proof when requesting credentials.
        """.trimIndent()
        response {
            HttpStatusCode.OK to {
                description = "Nonce response"
                body<JsonObject>()
            }
        }
    }

    fun getCredentialEndpointDocs(): RouteConfig.() -> Unit = {
        summary = "Credential Endpoint"
        description = """
            Issue a credential to the wallet.
            
            This endpoint is called by the wallet after obtaining an access token.
            The wallet provides a proof of possession and the credential configuration ID.
            
            **Request Requirements:**
            - Authorization header with Bearer token
            - credential_configuration_id: The credential type to issue
            - proof: Proof of possession (JWT or other supported format)
            
            **Response:**
            - credential: The issued credential (JWT, SD-JWT, or mDOC)
            - c_nonce: Fresh nonce for subsequent requests
            - c_nonce_expires_in: Nonce expiration time in seconds
        """.trimIndent()
        request {
            body<JsonObject> {
                description = "Credential request with proof and credential_configuration_id"
            }
        }
        response {
            HttpStatusCode.OK to {
                description = "Credential issued successfully"
                body<JsonObject>()
            }
            HttpStatusCode.BadRequest to {
                description = "Invalid request"
                body<JsonObject>()
            }
            HttpStatusCode.Unauthorized to {
                description = "Invalid or missing access token"
            }
        }
    }

    fun getListProfilesDocs(): RouteConfig.() -> Unit = {
        summary = "List Profiles"
        description = """
            List all configured credential profiles.
            
            Profiles define the credential types that can be issued, including:
            - Credential configuration (format, claims, display)
            - Issuer key and DID
            - Default credential data
            - Mapping rules for dynamic data
        """.trimIndent()
        response {
            HttpStatusCode.OK to {
                description = "List of credential profiles"
                body<List<ProfileSummary>> {
                    example("Profile list") {
                        value = listOf(
                            ProfileSummary(
                                profileId = "identity-credential-sdjwt",
                                name = "Identity Credential (SD-JWT)",
                                credentialConfigurationId = "identity_credential"
                            ),
                            ProfileSummary(
                                profileId = "openbadge-credential-w3c",
                                name = "OpenBadge Credential (W3C JWT)",
                                credentialConfigurationId = "OpenBadgeCredential_jwt_vc_json"
                            ),
                            ProfileSummary(
                                profileId = "mdl-credential",
                                name = "Mobile Driving License (mDL)",
                                credentialConfigurationId = "org.iso.18013.5.1.mDL"
                            )
                        )
                    }
                }
            }
        }
    }

    fun getViewProfileDocs(): RouteConfig.() -> Unit = {
        summary = "View Profile"
        description = """
            Get details of a specific credential profile.
            
            Returns the full profile configuration including:
            - Profile metadata (ID, name)
            - Credential configuration ID
            - Issuer DID
            - Associated credential configuration from metadata
        """.trimIndent()
        request {
            pathParameter<String>("profileId") {
                description = "Profile ID"
                required = true
                example("SD-JWT Profile") {
                    value = "identity-credential-sdjwt"
                }
            }
        }
        response {
            HttpStatusCode.OK to {
                description = "Profile details"
                body<ProfileDetails>()
            }
            HttpStatusCode.NotFound to {
                description = "Profile not found"
            }
        }
    }

    fun getCreateCredentialOfferDocs(): RouteConfig.() -> Unit = {
        summary = "Create Credential Offer"
        description = """
            Create a profile-derived credential offer URL.
            
            The profile defines the credential format, configuration, key material, and base data.
            Optional runtime overrides can be applied for this offer only.
            
            **Authentication Methods:**
            - `PRE_AUTHORIZED`: Direct issuance without user authentication (default)
            - `AUTHORIZED`: Requires user authentication via authorization code flow
            
            **Value Modes:**
            - `BY_REFERENCE`: Credential offer URI points to this server (default)
            - `BY_VALUE`: Credential offer is embedded in the URI
            
            **Transaction Code (tx_code):**
            For pre-authorized flow, you can require a PIN/transaction code:
            - Provide `txCode` configuration and optionally `txCodeValue`
            - If `txCodeValue` is not provided, one will be generated
            
            **Expiration:**
            - Default: 300 seconds (5 minutes)
            - Use -1 for no expiration
            
            **Runtime Overrides:**
            The following fields can be overridden per offer:
            - issuerKey, issuerDid, x5Chain
            - credentialData, mapping
            
            **Webhook Notifications:**
            Configure webhook callbacks to receive real-time updates about the issuance session:
            - OFFER_CREATED: When the credential offer is created
            - OFFER_RESOLVED: When the wallet resolves the credential offer
            - TOKEN_REQUESTED: When the wallet requests an access token
            - CREDENTIAL_ISSUED: When the credential is successfully issued
            - SESSION_EXPIRED: When the session expires
            
            Webhook authentication options:
            - No auth (public webhook)
            - Basic auth (username/password)
            - Bearer token
        """.trimIndent()
        request {
            pathParameter<String>("profileId") {
                description = "Profile ID to create the offer from"
                required = true
                example("SD-JWT Profile") {
                    value = "identity-credential-sdjwt"
                }
            }
            body<CredentialOfferCreateRequestBody> {
                example("[pre-authorized][by-reference] Simple") {
                    value = CredentialOfferCreateRequestBody(
                        authMethod = AuthenticationMethod.PRE_AUTHORIZED,
                        valueMode = CredentialOfferValueMode.BY_REFERENCE,
                    )
                }
                example("[pre-authorized][by-value]") {
                    value = CredentialOfferCreateRequestBody(
                        authMethod = AuthenticationMethod.PRE_AUTHORIZED,
                        valueMode = CredentialOfferValueMode.BY_VALUE,
                    )
                }
                example("[pre-authorized][with tx_code]") {
                    value = CredentialOfferCreateRequestBody(
                        authMethod = AuthenticationMethod.PRE_AUTHORIZED,
                        valueMode = CredentialOfferValueMode.BY_REFERENCE,
                        txCode = TxCode(
                            inputMode = "numeric",
                            length = 6,
                            description = "Please enter the PIN sent to your email"
                        ),
                    )
                }
                example("[pre-authorized][with provided tx_code value]") {
                    value = CredentialOfferCreateRequestBody(
                        authMethod = AuthenticationMethod.PRE_AUTHORIZED,
                        valueMode = CredentialOfferValueMode.BY_REFERENCE,
                        txCode = TxCode(
                            inputMode = "numeric",
                            length = 6,
                            description = "Please enter the PIN"
                        ),
                        txCodeValue = "123456",
                    )
                }
                example("[pre-authorized][custom expiry 2 minutes]") {
                    value = CredentialOfferCreateRequestBody(
                        authMethod = AuthenticationMethod.PRE_AUTHORIZED,
                        expiresInSeconds = 120,
                    )
                }
                example("[pre-authorized][no expiry]") {
                    value = CredentialOfferCreateRequestBody(
                        authMethod = AuthenticationMethod.PRE_AUTHORIZED,
                        expiresInSeconds = -1,
                    )
                }
                example("[authorized][by-reference]") {
                    value = CredentialOfferCreateRequestBody(
                        authMethod = AuthenticationMethod.AUTHORIZED,
                        valueMode = CredentialOfferValueMode.BY_REFERENCE,
                    )
                }
                example("[authorized][by-value]") {
                    value = CredentialOfferCreateRequestBody(
                        authMethod = AuthenticationMethod.AUTHORIZED,
                        valueMode = CredentialOfferValueMode.BY_VALUE,
                    )
                }
                example("[pre-authorized][with webhook notification]") {
                    value = CredentialOfferCreateRequestBody(
                        authMethod = AuthenticationMethod.PRE_AUTHORIZED,
                        valueMode = CredentialOfferValueMode.BY_REFERENCE,
                        notifications = KtorSessionNotifications(
                            webhook = KtorSessionNotifications.VerificationSessionWebhookNotification(
                                url = io.ktor.http.Url("https://example.com/webhook"),
                            )
                        ),
                    )
                }
                example("[pre-authorized][with webhook + bearer auth]") {
                    value = CredentialOfferCreateRequestBody(
                        authMethod = AuthenticationMethod.PRE_AUTHORIZED,
                        valueMode = CredentialOfferValueMode.BY_REFERENCE,
                        notifications = KtorSessionNotifications(
                            webhook = KtorSessionNotifications.VerificationSessionWebhookNotification(
                                url = io.ktor.http.Url("https://example.com/webhook"),
                                bearerToken = "your-secret-token",
                            )
                        ),
                    )
                }
            }
        }
        response {
            HttpStatusCode.Created to {
                description = "Credential offer created successfully"
                body<IssuanceSessionCreationResponse> {
                    example("Pre-authorized offer response") {
                        value = IssuanceSessionCreationResponse(
                            sessionId = "550e8400-e29b-41d4-a716-446655440000",
                            credentialOffer = "openid-credential-offer://?credential_offer_uri=http%3A%2F%2Flocalhost%3A7004%2Fcredential-offer%3Fid%3D550e8400-e29b-41d4-a716-446655440000",
                            expiresAt = kotlinx.datetime.Clock.System.now(),
                        )
                    }
                    example("Pre-authorized offer with tx_code") {
                        value = IssuanceSessionCreationResponse(
                            sessionId = "550e8400-e29b-41d4-a716-446655440000",
                            credentialOffer = "openid-credential-offer://?credential_offer_uri=http%3A%2F%2Flocalhost%3A7004%2Fcredential-offer%3Fid%3D550e8400-e29b-41d4-a716-446655440000",
                            txCodeValue = "483921",
                            expiresAt = kotlinx.datetime.Clock.System.now(),
                        )
                    }
                    example("By-value offer response") {
                        value = IssuanceSessionCreationResponse(
                            sessionId = "550e8400-e29b-41d4-a716-446655440000",
                            credentialOffer = "openid-credential-offer://?credential_offer=%7B%22credential_issuer%22%3A%22http%3A%2F%2Flocalhost%3A7004%22%2C%22credential_configuration_ids%22%3A%5B%22identity_credential%22%5D%2C%22grants%22%3A%7B%22urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Apre-authorized_code%22%3A%7B%22pre-authorized_code%22%3A%22abc123%22%7D%7D%7D",
                            expiresAt = kotlinx.datetime.Clock.System.now(),
                        )
                    }
                }
            }
            HttpStatusCode.NotFound to {
                description = "Profile not found"
            }
            HttpStatusCode.BadRequest to {
                description = "Invalid request"
            }
        }
    }

    fun getSessionDocs(): RouteConfig.() -> Unit = {
        summary = "Get Session"
        description = """
            Get the status of an issuance session.
            
            Returns the full session details including:
            - Current status (ACTIVE, CREDENTIAL_OFFER_RESOLVED, TOKEN_REQUESTED, CREDENTIAL_ISSUED, etc.)
            - Credential offer details
            - Expiration time
            - Transaction code (if applicable)
        """.trimIndent()
        request {
            pathParameter<String>("sessionId") {
                description = "Session ID (the offer ID returned when creating a credential offer)"
                required = true
                example("Session ID") {
                    value = "550e8400-e29b-41d4-a716-446655440000"
                }
            }
        }
        response {
            HttpStatusCode.OK to {
                description = "Session details"
                body<IssuanceSession>()
            }
            HttpStatusCode.NotFound to {
                description = "Session not found"
            }
        }
    }

    fun getSessionEventsDocs(): RouteConfig.() -> Unit = {
        summary = "Session Events (SSE)"
        description = """
            Receive real-time updates about an issuance session via Server-Sent Events (SSE).
            
            **Use Cases:**
            - Monitor issuance session progress in real-time
            - Receive notifications when wallet resolves credential offer
            - Track token requests and credential issuance status
            
            **Events:**
            - Session status changes
            - Credential offer resolution
            - Token requests
            - Credential issuance
            
            **Important Notes:**
            - Connection remains open until client disconnects or session completes
            - Events are sent as JSON objects in SSE format
        """.trimIndent()
        request {
            pathParameter<String>("sessionId") {
                description = "Session ID (the offer ID returned when creating a credential offer)"
                required = true
                example("Session ID") {
                    value = "550e8400-e29b-41d4-a716-446655440000"
                }
            }
        }
        response {
            HttpStatusCode.OK to {
                description = "SSE connection established, events will be streamed"
            }
            HttpStatusCode.NotFound to {
                description = "Session not found"
            }
        }
    }
}
