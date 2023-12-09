package id.walt.web.controllers

import id.walt.service.push.PushManager
import io.github.smiley4.ktorswaggerui.dsl.post
import io.github.smiley4.ktorswaggerui.dsl.route
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.URL
import java.net.URLDecoder
import java.nio.charset.StandardCharsets


object NotificationController {

    /*
    fun splitQuery(url: URI): Map<String?, List<String?>?> {
        return if (url.query.isEmpty()) {
            emptyMap<String?, List<String?>>()
        } else url.query.split("&")
    }*/

    fun splitQueryParameter(it: String): Pair<String, String> {
        val idx = it.indexOf("=")
        val key = if (idx > 0) it.substring(0, idx) else it
        val value = if (idx > 0 && it.length > idx + 1) it.substring(idx + 1) else null
        return Pair(
            URLDecoder.decode(key, StandardCharsets.UTF_8),
            URLDecoder.decode(value, StandardCharsets.UTF_8)
        )
    }

    fun Application.notifications() {
        routing {
            route("/api/notifications", {
                tags = listOf("NotificationController")
            }) {
                post("send", {
                    summary = "Experimental: Push notification system"
                    // TODO
                }) {
                    var id = call.request.queryParameters["id"] ?: return@post call.respond(HttpStatusCode.OK)
                    val type = call.request.queryParameters["type"] ?: "issuance"

                    id = "did:key:z6Mkipa1mwZTvUaTCPkHsdKGWNWteQbpEmvcr9HFed9gS4Ye"

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
                            val issuer = URL(queries["issuer"]!!.first()).host
                            val credentialTypes = queries["credential_type"]!!

                            PushManager.sendIssuanceNotification(id, issuer, credentialTypes, offer)
                        }

                        "verification" -> {
                            val remoteHost = URL(queries["redirect_uri"]!!.first()).host

                            PushManager.sendVerificationNotification(id, remoteHost, listOf("TODO"), offer)
                        }
                    }


                    call.respond(HttpStatusCode.OK, "Queued.")
                }
            }
        }
    }
}
