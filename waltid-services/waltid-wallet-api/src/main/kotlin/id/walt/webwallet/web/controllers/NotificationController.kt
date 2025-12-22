@file:OptIn(ExperimentalTime::class)

package id.walt.webwallet.web.controllers

import id.walt.webwallet.service.WalletServiceManager
import id.walt.webwallet.service.push.PushManager
import id.walt.webwallet.usecase.notification.NotificationDTO
import id.walt.webwallet.usecase.notification.NotificationFilterParameter
import id.walt.webwallet.web.controllers.auth.getWalletId
import io.github.smiley4.ktoropenapi.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.util.*
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid


@OptIn(ExperimentalUuidApi::class)
object NotificationController {

    /*
    fun splitQuery(url: URI): Map<String?, List<String?>?> {
        return if (url.query.isEmpty()) {
            emptyMap<String?, List<String?>>()
        } else url.query.split("&")
    }*/

    private fun splitQueryParameter(it: String): Pair<String, String> {
        val idx = it.indexOf("=")
        val key = if (idx > 0) it.substring(0, idx) else it
        val value = if (idx > 0 && it.length > idx + 1) it.substring(idx + 1) else null
        return Pair(
            URLDecoder.decode(key, StandardCharsets.UTF_8),
            URLDecoder.decode(value, StandardCharsets.UTF_8)
        )
    }

    fun Application.notifications() {
        walletRoute {
            route("/api/notifications", {
                tags = listOf("NotificationController")
            }) {
                get({
                    summary = "Get notifications"
                    request {
                        queryParameter<String>("type") {
                            description = "Filter by notification type"
                            example("Receiving") { value = "Receive" }
                        }
                        queryParameter<String>("addedOn") {
                            description = "Filter by date the notification was created"
                            example("Current date") {
                                value = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
                            }
                        }
                        queryParameter<Boolean>("isRead") {
                            description = "Filter by 'isRead' status"
                        }
                        queryParameter<String>("sort") {
                            description = "Sort by date added: ASC or DESC"
                            example("Sort ascending") { value = "ASC" }
                            example("Sort descending") { value = "DESC" }
                        }
                        queryParameter<Boolean>("showPending") {
                            description = "Filter by 'pending' credentials"
                        }
                    }
                    response {
                        HttpStatusCode.OK to {
                            description = "Array of notification objects"
                            body<List<NotificationDTO>>()
                        }
                    }
                }) {
                    call.respond(
                        WalletServiceManager.notificationFilterUseCase.filter(
                            call.getWalletId(), NotificationFilterParameter(
                                type = call.request.queryParameters["type"],
                                isRead = call.request.queryParameters["isRead"]?.toBooleanStrictOrNull(),
                                addedOn = call.request.queryParameters["addedOn"],
                                sort = call.request.queryParameters["sort"] ?: "desc",
                                showPending = call.request.queryParameters["showPending"]?.toBooleanStrictOrNull(),
                            )
                        )
                    )
                }
                delete({
                    summary = "Delete all wallet notifications"
                    response {
                        HttpStatusCode.Accepted to { description = "Notifications deleted" }
                        HttpStatusCode.BadRequest to { description = "Notifications could not be deleted" }
                    }
                }) {
                    call.respond(if (WalletServiceManager.notificationUseCase.deleteAll(call.getWalletId()) > 0) HttpStatusCode.Accepted else HttpStatusCode.BadRequest)
                }
                put("status/{status}", {
                    summary = "Set notification read status"
                    request {
                        pathParameter<Boolean>("status") {
                            required = true
                            allowEmptyValue = false
                            description = "Notification read status"
                        }
                        body<List<String>> {
                            description = "The list of notification ids"
                            required = true
                        }
                    }
                    response {
                        HttpStatusCode.Accepted to { description = "Notification status updated" }
                        HttpStatusCode.BadRequest to { description = "Notification status could not be updated" }
                    }
                }) {
                    val ids = call.receive<List<String>>()
                    val status = call.parameters.getOrFail("status").toBoolean()
                    call.respond(
                        if (WalletServiceManager.notificationUseCase.setStatus(
                                *ids.map { Uuid.parse(it) }.toTypedArray(), isRead = status
                            ) > 0
                        ) HttpStatusCode.Accepted else HttpStatusCode.BadRequest
                    )
                }
                route("{id}", {
                    request {
                        pathParameter<String>("id") {
                            required = true
                            allowEmptyValue = false
                            description = "Notification id"
                        }
                    }
                }) {
                    get({
                        summary = "Get notification by id"
                        response {
                            HttpStatusCode.OK to {
                                description = "Notification object"
                                body<NotificationDTO>()
                            }
                        }
                    }) {
                        val id = call.parameters.getOrFail("id")
                        call.respond(WalletServiceManager.notificationUseCase.findById(Uuid.parse(id)).fold(onSuccess = {
                            it
                        }, onFailure = {
                            it.localizedMessage
                        }))
                    }
                    delete({
                        summary = "Delete notification by id"
                        response {
                            HttpStatusCode.Accepted to { description = "Notification deleted" }
                            HttpStatusCode.BadRequest to { description = "Notification could not be deleted" }
                        }
                    }) {
                        val id = call.parameters.getOrFail("id")
                        call.respond(if (WalletServiceManager.notificationUseCase.deleteById(Uuid.parse(id)) > 0) HttpStatusCode.Accepted else HttpStatusCode.BadRequest)
                    }
                }

                post("send", {
                    summary = "Experimental: Push notification system"
                    // TODO
                }) {
                    val id = call.request.queryParameters.getOrFail("id")
                    val type = call.request.queryParameters["type"] ?: "issuance"

                    //id = "did:key:z6Mkipa1mwZTvUaTCPkHsdKGWNWteQbpEmvcr9HFed9gS4Ye"

                    var offer = call.receiveText().trim()

                    if (offer[0] == '{') {
                        offer = Json.parseToJsonElement(offer).jsonObject["url"]!!.jsonPrimitive.content
                    }

                    println("Got notification for $id for: $offer")

                    val queries = URI(offer).query.split("&").groupBy(
                        keySelector = { splitQueryParameter(it).first }, valueTransform = { splitQueryParameter(it).second }
                    )

                    when (type) {
                        "issuance" -> {
                            val issuer = Url(queries["issuer"]!!.first()).host
                            val credentialTypes = queries["credential_type"]!!

                            PushManager.sendIssuanceNotification(id, issuer, credentialTypes, offer)
                        }

                        "verification" -> {
                            val remoteHost = Url(queries["redirect_uri"]!!.first()).host

                            PushManager.sendVerificationNotification(id, remoteHost, listOf("TODO"), offer)
                        }
                    }


                    call.respond(HttpStatusCode.OK, "Queued.")
                }
            }
        }
    }
}
