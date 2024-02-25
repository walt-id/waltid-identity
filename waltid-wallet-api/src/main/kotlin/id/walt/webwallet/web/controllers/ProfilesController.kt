package id.walt.webwallet.web.controllers

import id.walt.webwallet.db.models.Profile
import id.walt.webwallet.service.profiles.ProfilesService
import id.walt.webwallet.web.WebBaseRoutes.authenticatedWebWalletRoute
import io.github.smiley4.ktorswaggerui.dsl.get
import io.github.smiley4.ktorswaggerui.dsl.put
import io.github.smiley4.ktorswaggerui.dsl.route
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import kotlinx.serialization.Serializable

fun Application.profiles() = authenticatedWebWalletRoute {
    @Serializable
    data class ProfileRequest(
        val preferredEmail: String,
        val preferredNumber: String,
        val countryCode: String,
        val preferredContactMethod: String,
    )

    route("profiles", {
        tags = listOf("Profiles")
    }) {
        get({
            summary = "Get the account profile data"
            response {
                HttpStatusCode.OK to {
                    body<Profile> {
                        description = "The profile data"
                    }
                }
            }
        }) {
            ProfilesService.get(getUserUUID())?.run {
                context.respond(this)
            } ?: context.respond(HttpStatusCode.NotFound)
        }
        put({
            summary = "Update profile"
            request {
                body<ProfileRequest> { description = "Profile object" }
            }
            response {
                HttpStatusCode.Created to { description = "Profile updated successfully" }
                HttpStatusCode.BadRequest to { description = "Error updating profile" }
            }
        }) {
            val profile = call.receive<ProfileRequest>()
            val result = if (ProfilesService.save(
                    Profile(
                        account = getUserUUID(),
                        preferredEmail = profile.preferredEmail,
                        preferredNumber = profile.preferredNumber,
                        countryCode = profile.countryCode,
                        preferredContactMethod = profile.preferredContactMethod,
                    )
                ) > 0
            ) HttpStatusCode.Created else HttpStatusCode.BadRequest
            context.respond(result)
        }
    }
}