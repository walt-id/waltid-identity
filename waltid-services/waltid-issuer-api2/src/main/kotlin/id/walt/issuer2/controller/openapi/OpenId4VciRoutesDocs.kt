package id.walt.issuer2.controller.openapi

import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.JsonObject

object OpenId4VciRoutesDocs {
    fun credentialIssuerMetadata(): RouteConfig.() -> Unit = {
        summary = "Get Credential Issuer metadata"
        response {
            HttpStatusCode.OK to {
                description = "OpenID4VCI Credential Issuer metadata"
                body<JsonObject>()
            }
        }
    }

    fun authorizationServerMetadata(): RouteConfig.() -> Unit = {
        summary = "Get Authorization Server metadata"
        response {
            HttpStatusCode.OK to {
                description = "OAuth Authorization Server metadata"
                body<JsonObject>()
            }
        }
    }

    fun jwtVcIssuerMetadata(): RouteConfig.() -> Unit = {
        summary = "Get SD-JWT VC issuer metadata"
        response {
            HttpStatusCode.OK to {
                description = "SD-JWT VC issuer metadata"
                body<JsonObject>()
            }
        }
    }

    fun vctTypeMetadata(): RouteConfig.() -> Unit = {
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
        summary = "External OAuth callback"
        description = "Callback endpoint used by the configured external OAuth provider for authorization-code issuance."
        response {
            HttpStatusCode.Found to {
                description = "Redirect back to the wallet client with the OpenID4VCI authorization code"
            }
        }
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
        description = "The credential endpoint."
        response {
            HttpStatusCode.OK to {
                description = "Credential response"
                body<JsonObject>()
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