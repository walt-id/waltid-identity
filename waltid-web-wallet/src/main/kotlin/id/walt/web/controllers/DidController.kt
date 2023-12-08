package id.walt.web.controllers

import id.walt.web.controllers.DidCreation.didCreate
import io.github.smiley4.ktorswaggerui.dsl.delete
import io.github.smiley4.ktorswaggerui.dsl.get
import io.github.smiley4.ktorswaggerui.dsl.post
import io.github.smiley4.ktorswaggerui.dsl.route
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.sql.transactions.transaction

fun Application.dids() = walletRoute {
    route("dids", {
        tags = listOf("DIDs")
    }) {
        get({
            summary = "List DIDs"
            response {
                HttpStatusCode.OK to {
                    description = "Array of (DID) strings"
                    body<List<String>>()
                }
            }
        }) {
            context.respond(getWalletService().run { transaction { runBlocking { listDids() } } })
        }

        route("{did}", {
            request {
                pathParameter<String>("did") {
                    description = "The DID"
                    example = "did:web:walt.id"
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
                context.respond(
                    getWalletService().loadDid(
                        context.parameters["did"] ?: throw IllegalArgumentException("No DID supplied")
                    )
                )
            }

            delete({
                summary = "Delete a specific DID"
                response {
                    HttpStatusCode.Accepted to { description = "DID deleted" }
                    HttpStatusCode.BadRequest to { description = "DID could not be deleted" }
                }
            }) {
                val success = getWalletService().deleteDid(
                    context.parameters["did"] ?: throw IllegalArgumentException("No DID supplied")
                )

                context.respond(
                    if (success) HttpStatusCode.Accepted
                    else HttpStatusCode.BadRequest
                )
            }
        }



        post("default", {
            summary = "Set the default DID"
            description =
                "Set the default DID (which is e.g. preselected in DID selection dropdown at presentation modal)"
            request {
                queryParameter<String>("did") {
                    description = "DID to set as default DID"
                    example = "did:web:walt.id"
                }
            }
            response { HttpStatusCode.Accepted to { description = "Default DID updated" } }
        }) {
            getWalletService().setDefault(
                context.parameters["did"] ?: throw IllegalArgumentException("No DID supplied")
            )
            context.respond(HttpStatusCode.Accepted)
        }

        route("create", {
            request {
                queryParameter<String>("keyId") {
                    description =
                        "Optionally override a key ID to use (otherwise will generate a new one if not present)"
                }
                queryParameter<String?>("alias") {
                    description = "Optionally set key alias (otherwise will use hash of key if not present)"
                    required = false
                }
            }
            response {
                HttpStatusCode.OK to {
                    description = "DID created"
                }
            }
        }) {
            didCreate()
        }
    }
}
