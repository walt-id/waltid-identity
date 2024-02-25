package id.walt.webwallet.web.controllers

import id.walt.webwallet.service.DidWebRegistryService
import io.github.smiley4.ktorswaggerui.dsl.get
import io.github.smiley4.ktorswaggerui.dsl.route
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject

fun Application.didRegistry() = routing {
    route("did-registry", {
        tags = listOf("DID Web Registry")
    }) {
        get({
            summary = "List registered DIDs"
            response {
                HttpStatusCode.OK to {
                    description = "Array of (DID) strings"
                    body<List<String>>()
                }
            }
        }) {
            context.respond(runBlocking {
                DidWebRegistryService.listRegisteredDids()
            })
        }

        route("{id}/did.json", {
            tags = listOf("DID Web Registry")
            request {
                pathParameter<String>("id") {
                    description = "ID"
                }
            }
        }) {
            get({
                summary = "Show a specific DID"

                response {
                    HttpStatusCode.OK to {
                        description = "The DID document"
                        body<JsonObject>()
                    }
                }
            }) {

                val id = context.parameters["id"] ?: throw IllegalArgumentException("No ID supplied")

                context.respond(
                    DidWebRegistryService.loadRegisteredDid(id)
                )
            }
        }
    }
}
