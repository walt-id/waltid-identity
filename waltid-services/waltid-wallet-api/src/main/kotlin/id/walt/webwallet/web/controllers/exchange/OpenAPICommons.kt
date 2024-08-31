package id.walt.webwallet.web.controllers.exchange

import io.github.smiley4.ktorswaggerui.dsl.routes.OpenApiResponses
import io.ktor.http.*
import kotlinx.serialization.json.JsonObject

object OpenAPICommons {

    fun usePresentationRequestResponse(): OpenApiResponses.() -> Unit = {
        HttpStatusCode.OK to {
            description = "Successfully claimed credentials"
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
}