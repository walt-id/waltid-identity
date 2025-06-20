package id.walt.issuer.issuance.openapi.oidcapi

import io.github.smiley4.ktoropenapi.config.RequestConfig
import io.github.smiley4.ktoropenapi.config.RouteConfig

fun getStandardVersionDocs(): RouteConfig.() -> Unit = {
    summary = "Signs credential with JWT and starts an OIDC credential exchange flow."
    description = "This endpoint issues a W3C Verifiable Credential, and returns an issuance URL "
    request { standardVersionPathParameter() }
}

fun getCredentialOfferUriDocs(): RouteConfig.() -> Unit = {
    summary = "Gets a credential offer based on the session id"
    request {
        standardVersionPathParameter()
        queryParameter<String>("id") { required = true }
    }
}

fun RequestConfig.standardVersionPathParameter() = pathParameter<String>("standardVersion") {
    description = "The value of the standard version. Supported values are: draft13 and draft11"
    example("Example") { value = "draft13" }
    required = true
}

fun RequestConfig.typePathParameter() = pathParameter<String>("type") {
    description = "The value of the credential type."
    example("Example") { value = "identity_credential" }
    required = true
}
