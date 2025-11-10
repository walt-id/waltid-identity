package id.walt.webwallet.web.controllers

import id.walt.webwallet.manifest.extractor.ManifestExtractor
import id.walt.webwallet.manifest.provider.ManifestProvider
import id.walt.webwallet.service.credentials.CredentialsService
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.route
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.util.*
import kotlinx.serialization.json.JsonObject
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

fun Application.manifest() = walletRoute {
    route("manifest", {
        tags = listOf("WalletCredential manifest")
    }) {
        route("{credentialId}") {
            get({
                summary =
                    "Get credential manifest, if available, otherwise null"
                request {
                    pathParameter<String>("credentialId") {
                        required = true
                        allowEmptyValue = false
                        description = "Credential id"
                    }
                }
                response {
                    HttpStatusCode.OK to {
                        description = "The display info json object"
                        body<JsonObject> {}
                    }
                    HttpStatusCode.NoContent to {
                        description = "The display info json object"
                        body<JsonObject> {}
                    }
                }
            }) {

                when (val manifest = getManifest(call.parameters, CredentialsService())) {
                    null -> call.respond(HttpStatusCode.NoContent)
                    else -> call.respond(manifest)
                }
            }
            get("display", {
                summary =
                    "Get offer display info, if available, otherwise empty object"//<--TODO: decide empty response type
                request {
                    pathParameter<String>("credentialId") {
                        required = true
                        allowEmptyValue = false
                        description = "Credential id"
                    }
                }
                response {
                    HttpStatusCode.OK to {
                        description = "The display info json object"
                        body<JsonObject> { }
                    }
                }
            }) {
                when (val manifest = getManifest(call.parameters, CredentialsService())?.toString()) {
                    null -> call.respond(HttpStatusCode.NoContent)
                    else -> call.respond(ManifestProvider.new(manifest).display())
                }
            }
            get("issuer", {
                summary =
                    "Get offer issuer info, if available, otherwise empty object"//<--TODO: decide empty response type
                request {
                    pathParameter<String>("credentialId") {
                        required = true
                        allowEmptyValue = false
                        description = "Credential id"
                    }
                }
                response {
                    HttpStatusCode.OK to {
                        description = "The issuer info json object"
                        body<JsonObject> {}
                    }
                }
            }) {
                when (val manifest = getManifest(call.parameters, CredentialsService())?.toString()) {
                    null -> call.respond(HttpStatusCode.NoContent)
                    else -> call.respond(ManifestProvider.new(manifest).issuer())
                }
            }
        }
        route("extract") {
            get({
                summary =
                    "Extract manifest info from issuance request offer, if available, otherwise empty object"//<--TODO: decide empty response type
                request {
                    queryParameter<String>("offer") {
                        required = true
                        allowEmptyValue = false
                        description = "Offer request URI"
                    }
                }
                response {
                    HttpStatusCode.OK to {
                        description = "The manifest issuer info json object"
                        body<JsonObject> { }
                    }
                    HttpStatusCode.NoContent to {
                        description = "No/empty manifest"
                        body<JsonObject> {}
                    }
                    HttpStatusCode.BadRequest to {
                        description = "Error message"
                        body<String> {}
                    }
                }
            }) {
                when (val manifest = extractManifest(call.parameters)) {
                    null -> call.respond(HttpStatusCode.NoContent)
                    else -> call.respond(manifest)
                }
            }
        }
    }
}


@OptIn(ExperimentalUuidApi::class)
internal fun getManifest(parameters: Parameters, credentialsService: CredentialsService): JsonObject? {
    val walletId = parameters.getOrFail("walletId")
    val credentialId = parameters.getOrFail("credentialId")
    return credentialsService.get(Uuid.parse(walletId), credentialId)?.parsedManifest
}

internal suspend fun extractManifest(parameters: Parameters): JsonObject? {
    val offer = parameters.getOrFail("offer")
    return ManifestExtractor.new(offer)?.extract(offer)
}
