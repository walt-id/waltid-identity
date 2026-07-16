package id.walt.issuer2.controller.openapi

import id.walt.openid4vci.errors.OAuthError
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
        response {
            HttpStatusCode.OK to {
                description = "OpenID4VCI Credential Issuer metadata"
                body<JsonObject>()
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
        description = "Resolve self-hosted SD-JWT VC type metadata from a VCT URL path."
        request {
            pathParameter<String>("type")
        }
        response {
            HttpStatusCode.OK to {
                description = "SD-JWT VC type metadata"
                body<JsonObject>()
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
        description = "The token endpoint."
        response {
            HttpStatusCode.OK to {
                description = "Access token response"
                body<JsonObject>()
            }
        }
    }

    fun credential(): RouteConfig.() -> Unit = {
        summary = "Credential endpoint"
        description = "The credential endpoint. Accepts plaintext JSON requests and encrypted JWT requests."
        request {
            body<JsonObject> {
                description = "Credential request"
                mediaTypes(ContentType.Application.Json)
            }
            body<String> {
                description = "Encrypted Credential Request as compact JWE"
                mediaTypes(ContentType.parse(CredentialEncryptionProfile.MEDIA_TYPE_JWT))
            }
        }
        response {
            HttpStatusCode.OK to {
                description = "Credential response"
                body<JsonObject>()
                body<String> {
                    mediaTypes(ContentType.parse(CredentialEncryptionProfile.MEDIA_TYPE_JWT))
                }
            }
        }
    }

    fun nonce(): RouteConfig.() -> Unit = {
        summary = "Nonce endpoint"
        description = "Return a nonce."
        response {
            HttpStatusCode.OK to {
                description = "Nonce response"
                body<JsonObject>()
            }
        }
    }
}