package id.walt.webwallet.web.controllers

import io.github.smiley4.ktorswaggerui.dsl.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.util.*
import kotlinx.serialization.json.JsonObject

fun Application.categories() = walletRoute {
    route("categories", {
        tags = listOf("WalletCategories")
    }) {
        get({
            summary = "List categories"
            response {
                HttpStatusCode.OK to {
                    description = "Array of categories"
                    body<List<JsonObject>>()
                }
            }
        }) {
            context.respond(getWalletService().listCategories())
        }
        route("{name}", {
            request {
                pathParameter<String>("name") {
                    description = "the category name"
                    example = "my-category"
                }
            }
        }) {
            post("add", {
                summary = "Add category"
                response {
                    HttpStatusCode.Created to { description = "Category added" }
                    HttpStatusCode.BadRequest to { description = "Category could not be added" }
                }
            }) {
                val name = call.parameters.getOrFail("name")
                runCatching { getWalletService().addCategory(name) }.onSuccess {
                    context.respond(if (it) HttpStatusCode.Created else HttpStatusCode.BadRequest)
                }.onFailure { context.respond(HttpStatusCode.BadRequest, it.localizedMessage) }
            }
            delete({
                summary = "Delete category"
                response {
                    HttpStatusCode.Accepted to { description = "Category deleted" }
                    HttpStatusCode.BadRequest to { description = "Category could not be deleted" }
                }
            }) {
                val name = call.parameters.getOrFail("name")
                runCatching { getWalletService().deleteCategory(name) }.onSuccess {
                    context.respond(if (it) HttpStatusCode.Accepted else HttpStatusCode.BadRequest)
                }.onFailure { context.respond(HttpStatusCode.BadRequest, it.localizedMessage) }
            }
            put("rename/{newName}", {
                summary = "Rename category"
                request {
                    pathParameter<String>("newName") {
                        description = "the category new name"
                        example = "new-category"
                    }
                }
                response {
                    HttpStatusCode.Accepted to { description = "Category renamed" }
                    HttpStatusCode.BadRequest to { description = "Category could not be renamed" }
                }
            }) {
                val oldName = call.parameters.getOrFail("name")
                val newName = call.parameters.getOrFail("newName")
                runCatching { getWalletService().renameCategory(oldName, newName) }.onSuccess {
                    context.respond(if (it) HttpStatusCode.Accepted else HttpStatusCode.BadRequest)
                }.onFailure { context.respond(HttpStatusCode.BadRequest, it.localizedMessage) }
            }
        }
    }
}
