@file:OptIn(ExperimentalUuidApi::class)

package id.walt.webwallet.web.controllers

import id.walt.webwallet.db.models.WalletCategoryData
import id.walt.webwallet.web.controllers.auth.getWalletService
import io.github.smiley4.ktoropenapi.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.util.*
import kotlin.uuid.ExperimentalUuidApi

fun Application.categories() = walletRoute {
    route("categories", {
        tags = listOf("WalletCategories")
    }) {
        get({
            summary = "List categories"
            response {
                HttpStatusCode.OK to {
                    description = "Array of categories"
                    body<List<WalletCategoryData>>()
                }
            }
        }) {
            call.respond(call.getWalletService().listCategories())
        }
        route("{name}", {
            request {
                pathParameter<String>("name") {
                    description = "the category name"
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
                runCatching { call.getWalletService().addCategory(name) }.onSuccess {
                    call.respond(if (it) HttpStatusCode.Created else HttpStatusCode.BadRequest)
                }.onFailure { call.respond(HttpStatusCode.BadRequest, it.localizedMessage) }
            }
            delete({
                summary = "Delete category"
                response {
                    HttpStatusCode.Accepted to { description = "Category deleted" }
                    HttpStatusCode.BadRequest to { description = "Category could not be deleted" }
                }
            }) {
                val name = call.parameters.getOrFail("name")
                runCatching { call.getWalletService().deleteCategory(name) }.onSuccess {
                    call.respond(if (it) HttpStatusCode.Accepted else HttpStatusCode.BadRequest)
                }.onFailure { call.respond(HttpStatusCode.BadRequest, it.localizedMessage) }
            }
            put("rename/{newName}", {
                summary = "Rename category"
                request {
                    pathParameter<String>("newName") {
                        description = "the category new name"
                    }
                }
                response {
                    HttpStatusCode.Accepted to { description = "Category renamed" }
                    HttpStatusCode.BadRequest to { description = "Category could not be renamed" }
                }
            }) {
                val oldName = call.parameters.getOrFail("name")
                val newName = call.parameters.getOrFail("newName")
                runCatching { call.getWalletService().renameCategory(oldName, newName) }.onSuccess {
                    call.respond(if (it) HttpStatusCode.Accepted else HttpStatusCode.BadRequest)
                }.onFailure { call.respond(HttpStatusCode.BadRequest, it.localizedMessage) }
            }
        }
    }
}
