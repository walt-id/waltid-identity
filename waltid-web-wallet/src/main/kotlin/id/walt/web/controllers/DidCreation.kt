package id.walt.web.controllers

import io.github.smiley4.ktorswaggerui.dsl.post
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.JsonPrimitive

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
            getWalletService().createDid(
                DidKeyMethodName, extractDidCreateParameters(DidKeyMethodName, context.request.queryParameters)
            ).let { context.respond(it) }
        }

        post(DidJwkMethodName, {
            summary = "Create a did:jwk"
        }) {
            getWalletService().createDid(
                DidJwkMethodName, extractDidCreateParameters(DidJwkMethodName, context.request.queryParameters)
            ).let { context.respond(it) }
        }

        post(DidWebMethodName, {
            summary = "Create a did:web"
            request {
                queryParameter<String>("domain") {
                    description = "Domain to use to host did:web document at"
                }
                queryParameter<String>("path") {
                    description = "Path to host the did:web document at"
                }
            }
        }) {
            getWalletService().createDid(
                DidWebMethodName, extractDidCreateParameters(DidWebMethodName, context.request.queryParameters)
            ).let { context.respond(it) }
        }

        post(DidEbsiMethodName, {
            summary = "Create a did:ebsi"
            request {
                queryParameter<Int>("version") { description = "Version 2 (NaturalPerson) or 1 (LegalEntity)" }
                queryParameter<String>("bearerToken") { description = "Required for v1 (LegalEntity)" }
            }
        }) {
            getWalletService().createDid(
                DidEbsiMethodName, extractDidCreateParameters(DidEbsiMethodName, context.request.queryParameters)
            ).let { context.respond(it) }
        }

        post(DidCheqdMethodName, {
            summary = "Create a did:cheqd"
            request {
                queryParameter<String>("network") { description = "testnet or mainnet" }
            }
        }) {
            getWalletService().createDid(
                DidCheqdMethodName, extractDidCreateParameters(DidCheqdMethodName, context.request.queryParameters)
            ).let { context.respond(it) }
        }

        post(DidIotaMethodName, {
            summary = "Create a did:iota"
        }) {
            getWalletService().createDid(
                DidIotaMethodName, extractDidCreateParameters(DidIotaMethodName, context.request.queryParameters)
            ).let { context.respond(it) }
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
                    parameters["useJwkJcsPub"]?.toBoolean() ?: false
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
