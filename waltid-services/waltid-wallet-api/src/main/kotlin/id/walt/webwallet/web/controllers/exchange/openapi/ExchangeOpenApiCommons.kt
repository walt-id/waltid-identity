package id.walt.webwallet.web.controllers.exchange.openapi

import id.walt.webwallet.db.models.WalletCredential
import io.github.smiley4.ktoropenapi.config.ResponsesConfig
import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.ktor.http.*
import kotlinx.serialization.json.JsonObject

object ExchangeOpenApiCommons {
    const val EXCHANGE_ROOT_PATH = "exchange"

    fun exchangeRoute(): RouteConfig.() -> Unit = {
        tags = listOf("Credential exchange")
    }

    fun usePresentationRequestResponse(): ResponsesConfig.() -> Unit = {
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

    fun useOfferRequestEndpointResponseParams(): ResponsesConfig.() -> Unit = {
        HttpStatusCode.OK to {
            description = "List of credentials"
            body<List<WalletCredential>> {
            }
        }
    }
}
