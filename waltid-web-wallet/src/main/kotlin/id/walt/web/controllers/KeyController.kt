package id.walt.web.controllers

import id.walt.crypto.keys.KeyType
import io.github.smiley4.ktorswaggerui.dsl.delete
import io.github.smiley4.ktorswaggerui.dsl.get
import io.github.smiley4.ktorswaggerui.dsl.post
import io.github.smiley4.ktorswaggerui.dsl.route
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.sql.transactions.transaction

fun Application.keys() = walletRoute {
    route("keys", {
        tags = listOf("Keys")
    }) {
        get({
            summary = "List Keys"
            response {
                HttpStatusCode.OK to {
                    description = "Array of (key) strings"
                    body<List<String>>()
                }
            }
        }) {
            context.respond(getWalletService().run { transaction { runBlocking { listKeys() } } })
        }

        post("generate", {
            summary = "Generate new key"
            request {
                queryParameter<String>("type") {
                    description = "Key type to use. Choose from: ${KeyType.entries.joinToString()}"
                }
            }
        }) {
            val type = call.request.queryParameters["type"]
                ?: KeyType.Ed25519.toString()

            val keyId = getWalletService().generateKey(type)
            context.respond(keyId)
        }


        post("import", {
            summary = "Import an existing key"
            request {
                body<String> { description = "Key in JWK or PEM format" }
            }
        }) {
            val body = call.receiveText()
            getWalletService().importKey(body)

            context.respond(HttpStatusCode.OK)
        }

        get("load/{alias}", {
            summary = "Show a specific key"
            request {
                pathParameter<String>("alias") {
                    description = "Key to show"

                }
            }
            response {
                HttpStatusCode.OK to {
                    description = "The key document (JSON)"
                    body<JsonObject>()
                }
            }
        }) {
            context.respond(
                getWalletService().loadKey(
                    context.parameters["alias"] ?: throw IllegalArgumentException("No key supplied")
                )
            )
        }

        get("export/{keyId}", {
            summary = "Load a specific key"

            request {
                pathParameter<String>("keyId") {
                    description = "the key id (or alias)"
                    example = "bc6fa6b0593648238c4616800bed7746"
                }
                queryParameter<String>("format") {
                    description = "Select format to export the key, e.g. 'JWK' / 'PEM'. JWK by default."
                    example = "JWK"
                    required = false
                }
                queryParameter<Boolean>("loadPrivateKey") {
                    description =
                        "Select if the secret private key should be loaded - take special care in this case! False by default."
                    example = false
                    required = false
                }
            }
        }) {
            val keyId = context.parameters["keyId"] ?: throw IllegalArgumentException("No key id provided.")

            val format = context.request.queryParameters["format"] ?: "JWK"
            val loadPrivateKey = context.request.queryParameters["loadPrivateKey"].toBoolean()

            context.respond(getWalletService().exportKey(keyId, format, loadPrivateKey))
        }

        delete("{keyId}", {
            summary = "Delete a specific key"
            request {
                pathParameter<String>("keyId") {
                    description = "the key id (or alias)"
                    example = "bc6fa6b0593648238c4616800bed7746"
                }
            }
            response {
                HttpStatusCode.Accepted to { description = "Key deleted" }
                HttpStatusCode.BadRequest to { description = "Key could not be deleted" }
            }
        }) {
            val keyId = context.parameters["keyId"] ?: throw IllegalArgumentException("No key id provided.")

            val success = getWalletService().deleteKey(keyId)

            context.respond(if (success) HttpStatusCode.Accepted else HttpStatusCode.BadRequest)
        }
    }
}
