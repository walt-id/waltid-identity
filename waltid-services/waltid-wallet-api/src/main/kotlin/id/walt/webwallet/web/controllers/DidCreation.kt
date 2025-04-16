package id.walt.webwallet.web.controllers

import id.walt.webwallet.web.controllers.auth.getWalletService
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.JsonPrimitive
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
object DidCreation {

    private const val DidKeyMethodName = "key"
    private const val DidJwkMethodName = "jwk"
    private const val DidWebMethodName = "web"
    private const val DidEbsiMethodName = "ebsi"
    private const val DidCheqdMethodName = "cheqd"
    private const val DidIotaMethodName = "iota"

    fun Route.didCreate() {
        post(DidKeyMethodName, {
            summary = "Create a did:key"
            request {
                queryParameter<Boolean?>("useJwkJcsPub") {
                    description = "Optionally set JWK JCS Pub format (for e.g. EBSI)"
                    required = false
                }
            }
        }) {
            call.getWalletService().createDid(
                DidKeyMethodName, extractDidCreateParameters(DidKeyMethodName, call.request.queryParameters)
            ).let { call.respond(it) }
        }

        post(DidJwkMethodName, {
            summary = "Create a did:jwk"
        }) {
            call.getWalletService().createDid(
                DidJwkMethodName, extractDidCreateParameters(DidJwkMethodName, call.request.queryParameters)
            ).let { call.respond(it) }
        }

        post(DidWebMethodName, {
            summary = "Create a did:web"
            request {
                queryParameter<String>("domain") {
                    description = "Domain to use to host did:web document at"
                }
                queryParameter<String>("path") {
                    description = "Path to host the did:web document at. Starting with a: \"/\""
                }
            }
        }) {
            val parameters = extractDidCreateParameters(DidWebMethodName, call.request.queryParameters)
            call.getWalletService().createDid(DidWebMethodName, parameters).let { call.respond(it) }
        }

        post(DidEbsiMethodName, {
            summary = "Create a did:ebsi"
            request {
                queryParameter<Int>("version") { description = "Version 2 (NaturalPerson) or 1 (LegalEntity)" }
                queryParameter<String>("bearerToken") { description = "Required for v1 (LegalEntity)" }
            }
        }) {
            call.getWalletService().createDid(
                DidEbsiMethodName, extractDidCreateParameters(DidEbsiMethodName, call.request.queryParameters)
            ).let { call.respond(it) }
        }

        post(DidCheqdMethodName, {
            summary = "Create a did:cheqd"
            request {
                queryParameter<String>("network") { description = "testnet or mainnet" }
            }
        }) {
            call.getWalletService().createDid(
                DidCheqdMethodName, extractDidCreateParameters(DidCheqdMethodName, call.request.queryParameters)
            ).let { call.respond(it) }
        }

        post(DidIotaMethodName, {
            summary = "Create a did:iota"
        }) {
            call.getWalletService().createDid(
                DidIotaMethodName, extractDidCreateParameters(DidIotaMethodName, call.request.queryParameters)
            ).let { call.respond(it) }
        }
    }

    private fun extractDidCreateParameters(method: String, parameters: Parameters): Map<String, JsonPrimitive> = mapOf(
        // common
        "alias" to JsonPrimitive(parameters["alias"]?.takeIf { it.isNotEmpty() } ?: "n/a"),
        "keyId" to JsonPrimitive(parameters["keyId"] ?: ""),
    ).plus(
        // specific
        when (method) {
            DidKeyMethodName -> mapOf(
                "useJwkJcsPub" to JsonPrimitive(
                    parameters["useJwkJcsPub"]?.toBoolean() == true
                )
            )

            DidWebMethodName -> mapOf(
                "domain" to JsonPrimitive(parameters["domain"]?.takeIf { it.isNotEmpty() } ?: "localhost:3000"),
                "path" to JsonPrimitive(parameters["path"]),
            )

            DidEbsiMethodName -> mapOf(
                "bearerToken" to JsonPrimitive(parameters["bearerToken"]),
                "version" to JsonPrimitive(parameters["version"]?.toInt() ?: 1),
            )

            DidCheqdMethodName -> mapOf("network" to JsonPrimitive(parameters["network"]?.takeIf { it.isNotEmpty() } ?: "testnet"))
            DidJwkMethodName, DidIotaMethodName -> emptyMap()
            else -> emptyMap()
        }
    )
}
