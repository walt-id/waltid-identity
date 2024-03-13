package id.walt.webwallet.web.controllers

import id.walt.webwallet.config.ConfigManager
import id.walt.webwallet.config.RuntimeConfig
import id.walt.webwallet.manifest.extractor.EntraMockManifestExtractor
import id.walt.webwallet.manifest.extractor.ManifestExtractor
import id.walt.webwallet.manifest.provider.ManifestProvider
import id.walt.webwallet.service.credentials.CredentialsService
import io.github.smiley4.ktorswaggerui.dsl.get
import io.github.smiley4.ktorswaggerui.dsl.route
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.util.*
import kotlinx.serialization.json.JsonObject

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
                        body<JsonObject> {
                            description = "The display info json object"
                        }
                    }
                    HttpStatusCode.NoContent to {
                        body<JsonObject> {
                            description = "The display info json object"
                        }
                    }
                }
            }) {
                val credentialService = CredentialsService()
                val manifest = callManifest(call.parameters) { getManifest(it, credentialService) }
                when (manifest) {
                    null -> context.respond(HttpStatusCode.NoContent)
                    else -> context.respond(manifest)
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
                        body<JsonObject> {
                            description = "The display info json object"
                        }
                    }
                }
            }) {
                val credentialService = CredentialsService()
                val manifest = callManifest(call.parameters) {
                    getManifest(it, credentialService)
                }?.toString()

                when (manifest) {
                    null -> context.respond(HttpStatusCode.NoContent)
                    else -> context.respond(ManifestProvider.new(manifest).display())
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
                        body<JsonObject> {
                            description = "The issuer info json object"
                        }
                    }
                }
            }) {
                val credentialService = CredentialsService()
                val manifest = callManifest(call.parameters) {
                    getManifest(it, credentialService)
                }?.toString()

                when (manifest) {
                    null -> context.respond(HttpStatusCode.NoContent)
                    else -> context.respond(ManifestProvider.new(manifest).issuer())
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
                        body<JsonObject> {
                            description = "The manifest issuer info json object"
                        }
                    }
                    HttpStatusCode.NoContent to {
                        body<JsonObject> {
                            description = "No/empty manifest"
                        }
                    }
                    HttpStatusCode.BadRequest to {
                        body<String> {
                            description = "Error message"
                        }
                    }
                }
            }) {
                val manifest = callManifest(call.parameters) { extractManifest(it) }

                when (manifest) {
                    null -> context.respond(HttpStatusCode.NoContent)
                    else -> context.respond(manifest)
                }
            }
        }
    }
}

internal suspend fun callManifest(parameters: Parameters, method: suspend (Parameters) -> JsonObject?): JsonObject? {
    val runtimeConfig = ConfigManager.getConfig<RuntimeConfig>()
    return if (runtimeConfig.mock) {
        EntraMockManifestExtractor().extract("")
    } else {
        return method.invoke(parameters)
    }
}

internal fun getManifest(parameters: Parameters, credentialsService: CredentialsService): JsonObject? {
    val walletId = parameters.getOrFail("walletId")
    val credentialId = parameters.getOrFail("credentialId")
    return credentialsService.get(kotlinx.uuid.UUID(walletId), credentialId)?.parsedManifest
}

internal suspend fun extractManifest(parameters: Parameters): JsonObject? {
    val offer = parameters.getOrFail("offer")
    return ManifestExtractor.new(offer)?.extract(offer)
}
