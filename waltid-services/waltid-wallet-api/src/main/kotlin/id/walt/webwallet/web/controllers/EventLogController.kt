@file:OptIn(ExperimentalUuidApi::class)

package id.walt.webwallet.web.controllers

import id.walt.webwallet.service.WalletServiceManager.eventFilterUseCase
import id.walt.webwallet.service.WalletServiceManager.eventUseCase
import id.walt.webwallet.service.events.EventLogFilter
import id.walt.webwallet.service.events.EventLogFilterResult
import id.walt.webwallet.web.controllers.auth.getUserUUID
import id.walt.webwallet.web.controllers.auth.getWalletId
import io.github.smiley4.ktoropenapi.delete
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.route
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.util.*
import kotlin.uuid.ExperimentalUuidApi

fun Application.eventLogs() = walletRoute {
    route("eventlog", {
        tags = listOf("Event Log")
    }) {
        get({
            summary = "Retrieve event logs for currently signed in account wallet"
            request {
                queryParameter<Int>("limit") {
                    description = "Page size"
                    required = false
                }
                queryParameter<List<String>>("filter") {
                    description = "List of key=value pairs for filtering"
                    required = false
                }
                queryParameter<String>("startingAfter") {
                    description = "Starting after page"
                    // example = ""
                    required = false
                }
                queryParameter<String>("sortBy") {
                    description = "The property to sort by"
                    // example = ""
                    required = false
                }
                queryParameter<String>("sortOrder") {
                    description = "The sort order [ASC|DESC]"
                    example("Sort ascending") { value = "ASC" }
                    example("Sort descending") { value = "DESC" }
                    required = false
                }
            }
            response {
                HttpStatusCode.OK to {
                    description = "The event log result"
                    body<EventLogFilterResult> {}
                }
            }
        }) {
            val limit = call.request.queryParameters["limit"]?.toIntOrNull()
            val data = call.request.queryParameters.getAll("filter")
                ?.groupBy({ it.substringBefore("=") }, { it.substringAfter("=") }) ?: emptyMap()
            val startingAfter = call.request.queryParameters["startingAfter"]
            val sortBy = call.request.queryParameters["sortBy"]
            val sortOrder = call.request.queryParameters["sortOrder"]
            call.respond(
                eventFilterUseCase.filter(
                    accountId = call.getUserUUID(),
                    walletId = call.getWalletId(),
                    filter = EventLogFilter(
                        limit = limit,
                        startingAfter = startingAfter,
                        sortBy = sortBy,
                        sortOrder = sortOrder,
                        data = data,
                    )
                )
            )
        }
        delete("{id}", {
            summary = "Delete event log"
            request {
                pathParameter<Int>("id") {
                    required = true
                    allowEmptyValue = false
                    description = "Event log ID"
                }
            }
            response {
                HttpStatusCode.Accepted to { description = "Event log deleted" }
                HttpStatusCode.BadRequest to { description = "Event log could not be deleted" }
            }
        }) {
            val id = call.parameters.getOrFail("id").toInt()
            call.respond(HttpStatusCode.Accepted, eventUseCase.delete(id))
        }
    }
}
