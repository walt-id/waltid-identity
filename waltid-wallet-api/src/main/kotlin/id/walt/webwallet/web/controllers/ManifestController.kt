package id.walt.webwallet.web.controllers

import id.walt.webwallet.manifests.Manifest
import io.github.smiley4.ktorswaggerui.dsl.get
import io.github.smiley4.ktorswaggerui.dsl.route
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import kotlinx.serialization.json.JsonObject

fun Application.manifest() = walletRoute {
    route("info", {
        tags = listOf("WalletCredential exchange manifest")
    }) {
        get("display", {
            summary =
                "Get offer display info, if available, otherwise empty object"//<--TODO: decide empty response type
            request {
                queryParameter<String>("offer") {
                    required = true
                    allowEmptyValue = false
                    description = "Offer request URI"
                }
            }
            response {
                HttpStatusCode.OK to {
                    body<JsonObject> {
                        description = "The display info json object"
                    }
                }
            }
        }) {
            context.respond<JsonObject>(manifestCall(call.parameters["offer"]) {
                Manifest.new(it).display()
            })
        }
        get("issuer", {
            summary = "Get offer issuer info, if available, otherwise empty object"//<--TODO: decide empty response type
            request {
                queryParameter<String>("offer") {
                    required = true
                    allowEmptyValue = false
                    description = "Offer request URI"
                }
            }
            response {
                HttpStatusCode.OK to {
                    body<JsonObject> {
                        description = "The manifest issuer info json object"
                    }
                }
            }
        }) {
            context.respond<JsonObject>(manifestCall(call.parameters["offer"]) {
                Manifest.new(it).issuer()
            })
        }
    }
}

internal suspend fun manifestCall(offerRequestUrl: String?, method: suspend (String) -> JsonObject?): JsonObject =
    offerRequestUrl?.let {
        method.invoke(it) ?: JsonObject(emptyMap())
    } ?: throw IllegalArgumentException("No offer request url provided.")