@file:OptIn(ExperimentalUuidApi::class)

package id.walt.webwallet.web.controllers


import id.walt.webwallet.db.models.WalletDid
import id.walt.webwallet.web.controllers.DidCreation.didCreate
import id.walt.webwallet.web.controllers.auth.getWalletService
import id.walt.webwallet.web.model.DidImportRequest
import io.github.smiley4.ktoropenapi.delete
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.github.smiley4.ktoropenapi.route
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.response.*
import io.ktor.server.request.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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

        post("import", {
            summary = "Import an existing DID"
            description = "Import a DID (did:key, did:web, did:jwk) into the wallet. Requires private key (PEM or JWK) to verify ownership."
            request {
                body<DidImportRequest> {
                    description = "Payload containing the DID and required associated private key (JWK/PEM)."
                }
            }
            response {
                HttpStatusCode.Created to { description = "DID imported successfully" }
                HttpStatusCode.BadRequest to { description = "Invalid DID or missing/invalid key material" }
                HttpStatusCode.Conflict to { description = "DID already exists" }
                HttpStatusCode.UnsupportedMediaType to { description = "Unsupported key format" }
            }
        }) {
            val req = call.receive<DidImportRequest>()

                val key: Any = when (val k = req.key ?: throw BadRequestException("key is required (PEM or JWK)")) {
                    is JsonObject -> k
                    is JsonPrimitive -> if (k.isString) k.content else throw BadRequestException("key must be a string (PEM/JWK JSON) or object (JWK)")
                    else -> throw BadRequestException("key must be a string (PEM/JWK JSON) or object (JWK)")
                }
                val result = call.getWalletService().importDid(did = req.did, key = key, alias = req.alias)
                call.respond(result)

        }
    }
}
