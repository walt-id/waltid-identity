package id.walt.webwallet.web.controllers.exchange

import id.walt.webwallet.db.models.WalletCredential
import io.github.smiley4.ktorswaggerui.dsl.routes.OpenApiRequest
import io.github.smiley4.ktorswaggerui.dsl.routes.OpenApiResponses
import io.github.smiley4.ktorswaggerui.dsl.routes.OpenApiRoute
import io.ktor.http.*
import kotlinx.serialization.json.JsonObject

object OpenAPICommons {
    const val rootPath = "exchange"

    fun route(): OpenApiRoute.() -> Unit = {
        tags = listOf("Credential exchange")
    }

    fun usePresentationRequestResponse(): OpenApiResponses.() -> Unit = {
        HttpStatusCode.OK to {
            description = "Successfully presented credentials"
            body<JsonObject> {
                description = """{"redirectUri": String}"""
            }
        }
        HttpStatusCode.BadRequest to {
            description = "Presentation was not accepted"
            body<JsonObject> {
                description = """{"redirectUri": String?, "errorMessage": String}"""
            }
        }
    }

    fun useOfferRequestEndpointRequestParams(): OpenApiRequest.() -> Unit = {
        queryParameter<String>("did") { description = "The DID to issue the credential(s) to" }
        queryParameter<Boolean>("requireUserInput") { description = "Whether to claim as pending acceptance" }
        body<String> {
            description = "The offer request to use"
        }
    }

    fun useOfferRequestEndpointResponseParams(): OpenApiResponses.() -> Unit = {
        HttpStatusCode.OK to {
            body<List<WalletCredential>> {
                description = "List of credentials"
            }
        }
    }
}