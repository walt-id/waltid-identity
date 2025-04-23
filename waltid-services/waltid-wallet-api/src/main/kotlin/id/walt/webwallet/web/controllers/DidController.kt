@file:OptIn(ExperimentalUuidApi::class)

package id.walt.webwallet.web.controllers

import id.walt.webwallet.db.models.WalletDid
import id.walt.webwallet.web.controllers.DidCreation.didCreate
import id.walt.webwallet.web.controllers.auth.getWalletService
import io.github.smiley4.ktoropenapi.delete
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.github.smiley4.ktoropenapi.route
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlin.uuid.ExperimentalUuidApi

fun Application.dids() = walletRoute {
    route("dids", {
        tags = listOf("DIDs")
    }) {
        get({
            summary = "List DIDs"
            response {
                HttpStatusCode.OK to {
                    description = "Array of (DID) strings"
                    body<List<WalletDid>>()
                }
            }
        }) {
            call.respond(call.getWalletService().run { runBlocking { listDids() } })
        }

        route("{did}", {
            request {
                pathParameter<String>("did") {
                    description = "The DID"
                    example("walt.id did:web") {
                        value = "did:web:walt.id"
                    }
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
                call.respond(
                    call.getWalletService().loadDid(
                        call.parameters["did"] ?: throw IllegalArgumentException("No DID supplied")
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
                val success = call.getWalletService().deleteDid(
                    call.parameters["did"] ?: throw IllegalArgumentException("No DID supplied")
                )

                call.respond(
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
                    example("walt.id did:web") {
                        value = "did:web:walt.id"
                        summary = "walt.id did:web"
                    }
                }
            }
            response { HttpStatusCode.Accepted to { description = "Default DID updated" } }
        }) {
            call.getWalletService().setDefault(
                call.parameters["did"] ?: throw IllegalArgumentException("No DID supplied")
            )
            call.respond(HttpStatusCode.Accepted)
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
                HttpStatusCode.OK to { description = "DID created" }
                HttpStatusCode.Conflict to { description = "DID already exists" }
                HttpStatusCode.BadRequest to { description = "DID could not be created" }

            }
        }) {
            didCreate()
        }
    }
}
