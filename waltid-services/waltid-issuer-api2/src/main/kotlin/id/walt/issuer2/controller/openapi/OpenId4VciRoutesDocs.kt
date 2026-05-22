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

    fun openIdProviderMetadata(): RouteConfig.() -> Unit = {
        summary = "Get OpenID Provider metadata"
        response {
            HttpStatusCode.OK to {
                description = "OpenID Provider metadata"
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
        description = "OpenID4VCI authorization endpoint for authorization-code flow."
        response {
            HttpStatusCode.Found to {
                description = "Redirect containing authorization response parameters"
            }
        }
    }

    fun token(): RouteConfig.() -> Unit = {
        summary = "Token endpoint"
        description = "OAuth token endpoint for authorization-code and pre-authorized-code grants."
        response {
            HttpStatusCode.OK to {
                description = "Access token response"
                body<JsonObject>()
            }
        }
    }

    fun credential(): RouteConfig.() -> Unit = {
        summary = "Credential endpoint"
        description = "OpenID4VCI credential endpoint. Credential signing will be wired in a later implementation step."
        response {
            HttpStatusCode.OK to {
                description = "Credential response"
                body<JsonObject>()
            }
        }
    }

    fun nonce(): RouteConfig.() -> Unit = {
        summary = "Nonce endpoint"
        description = "Return a fresh credential nonce."
        response {
            HttpStatusCode.OK to {
                description = "Nonce response"
                body<JsonObject>()
            }
        }
    }
}
