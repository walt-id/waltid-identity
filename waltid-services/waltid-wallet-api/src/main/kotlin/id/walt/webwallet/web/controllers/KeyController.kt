package id.walt.webwallet.web.controllers

import id.walt.crypto.keys.KeyGenerationRequest
import id.walt.crypto.keys.KeyType
import id.walt.webwallet.web.controllers.auth.getWalletService
import io.github.smiley4.ktorswaggerui.dsl.routing.delete
import io.github.smiley4.ktorswaggerui.dsl.routing.get
import io.github.smiley4.ktorswaggerui.dsl.routing.post
import io.github.smiley4.ktorswaggerui.dsl.routing.route
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.util.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonObject
import org.jetbrains.exposed.sql.transactions.transaction

fun Application.keys() = walletRoute {
    route("keys", {
        tags = listOf("Keys")
    }) {
        get({
            summary = "List Keys"
            response {
                HttpStatusCode.OK to {
                    description = "Array of (key) strings"
                    body<List<String>>()
                }
            }
        }) {
            context.respond(getWalletService().run { transaction { runBlocking { listKeys() } } })
        }

        post("generate", {
            summary = "Generate new key"
            request {
                body<KeyGenerationRequest> {
                    description = "Key configuration (JSON)"

                    example("JWK key generation request") {
                        value = KeyGenerationRequest()
                    }

                    example("TSE key generation request") {
                        value = KeyGenerationRequest(
                            backend = "tse",
                            config = buildJsonObject {
                                put("server", JsonPrimitive("http://0.0.0.0:8200/v1/transit"))
                                put("accessKey", JsonPrimitive("dev-only-token"))
                            }
                        )
                    }

                    example("OCI key generation request") {
                        value = KeyGenerationRequest(
                            backend = "oci",
                            keyType = KeyType.secp256r1,
                            config = buildJsonObject {
                                put(
                                    "vaultId",
                                    JsonPrimitive("ocid1.vault.oc1.eu-frankfurt-1.entbf645aabf2.abtheljshkb6dsuldqf324kitneb63vkz3dfd74dtqvkd5j2l2cxwyvmefeq")
                                )
                                put(
                                    "compartmentId",
                                    JsonPrimitive("ocid1.compartment.oc1..aaaaaaaawirugoz35riiybcxsvf7bmelqsxo3sajaav5w3i2vqowcwqrllxa")
                                )

                            }
                        )
                    }
                    example("OCI API key generation request") {
                        value = KeyGenerationRequest(
                            backend = "oci-rest-api",
                            keyType = KeyType.secp256r1,
                            config = buildJsonObject {
                                put(
                                    "tenancyOcid",
                                    JsonPrimitive("ocid1.tenancy.oc1..aaaaaaaaiijfupfvsqwqwgupzdy5yclfzcccmie4ktp2wlgslftv5j7xpk6q")
                                )
                                put(
                                    "compartmentOcid",
                                    JsonPrimitive("ocid1.tenancy.oc1..aaaaaaaaiijfupfvsqwqwgupzdy5yclfzcccmie4ktp2wlgslftv5j7xpk6q")
                                )
                                put(
                                    "userOcid",
                                    JsonPrimitive("ocid1.user.oc1..aaaaaaaaxjkkfjqxdqk7ldfjrxjmacmbi7sci73rbfiwpioehikavpbtqx5q")
                                )
                                put("fingerprint", JsonPrimitive("bb:d4:4b:0c:c8:3a:49:15:7f:87:55:d5:2b:7e:dd:bc"))
                                put(
                                    "cryptoEndpoint",
                                    JsonPrimitive("entcvrlraabc4-crypto.kms.eu-frankfurt-1.oraclecloud.com")
                                )
                                put(
                                    "managementEndpoint",
                                    JsonPrimitive("entcvrlraabc4-management.kms.eu-frankfurt-1.oraclecloud.com")
                                )
                                put("signingKeyPem", JsonPrimitive("privateKey"))
                            }
                        )
                    }
                    example("AWS key generation request") {
                        value = KeyGenerationRequest(
                            backend = "aws",
                            keyType = KeyType.secp256r1,
                            config = buildJsonObject {
                                putJsonObject("auth") {
                                    put("accessKeyId", JsonPrimitive("accessKey"))
                                    put("secretAccessKey", JsonPrimitive("secretKey"))
                                    put("region", JsonPrimitive("eu-central-1"))
                                }
                            }
                        )
                    }
                    example("Azure key generation request") {
                        value = KeyGenerationRequest(
                            backend = "azure",
                            keyType = KeyType.secp256r1,
                            config = buildJsonObject {
                                putJsonObject("auth") {
                                    put("clientId", JsonPrimitive("clientId"))
                                    put("clientSecret", JsonPrimitive("clientSecret"))
                                    put("tenantId", JsonPrimitive("tenantId"))
                                    put("keyVaultUrl", JsonPrimitive("keyVaultUrl"))
                                }
                            }
                        )
                    }
                }
            }
            response {
                HttpStatusCode.Created to {
                    description = "The generated key"
                    body<String>()
                }
            }
        }) {
            val keyGenerationRequest = context.receive<KeyGenerationRequest>()

            runCatching {
                getWalletService().generateKey(keyGenerationRequest)
            }.onSuccess {
                context.respond(HttpStatusCode.Created, it)
            }.onFailure {
                context.respond(HttpStatusCode.BadRequest, it.localizedMessage)
            }
        }


        post("import", {
            summary = "Import an existing key"
            request {
                body<String> { description = "Key in JWK or PEM format" }
            }
        }) {
            val body = call.receiveText()

            runCatching {
                getWalletService().importKey(body)
            }

                .onSuccess { key ->
                    context.respond(
                        HttpStatusCode.Created,
                        key
                    )
                }.onFailure {
                    throw it
                }

        }
        route("{keyId}", {
            request {
                pathParameter<String>("keyId") {
                    description = "the key id (or alias)"
                    example("Example") { value = "bc6fa6b0593648238c4616800bed7746" }
                }
            }
        }) {

            get("load", {
                summary = "Show a specific key"
                response {
                    HttpStatusCode.OK to {
                        description = "The key document (JSON)"
                        body<JsonObject>()
                    }
                }
            }) {
                context.respond(
                    getWalletService().loadKey(
                        context.parameters["keyId"] ?: throw IllegalArgumentException("No key supplied")
                    )
                )
            }

            get("meta", {
                summary = "Show a specific key meta info"
                response {
                    HttpStatusCode.OK to {
                        description = "The key document (JSON)"
                        body<JsonObject>()
                    }
                }
            }) {
                val keyId = context.parameters["keyId"] ?: error("No key supplied")
                context.respond(getWalletService().getKeyMeta(keyId))
            }

            get("export", {
                summary = "Load a specific key"

                request {
                    queryParameter<String>("format") {
                        description = "Select format to export the key, e.g. 'JWK' / 'PEM'. JWK by default."
                        example("JWK") { value = "JWK" }
                        example("PEM") { value = "PEM" }
                        required = false
                    }
                    queryParameter<Boolean>("loadPrivateKey") {
                        description =
                            "Select if the secret private key should be loaded - take special care in this case! False by default."
                        required = false
                    }
                }
            }) {
                val keyId = context.parameters["keyId"] ?: throw IllegalArgumentException("No key id provided.")

                val format = context.request.queryParameters["format"] ?: "JWK"
                val loadPrivateKey = context.request.queryParameters["loadPrivateKey"].toBoolean()

                context.respond(getWalletService().exportKey(keyId, format, loadPrivateKey))
            }

            delete({
                summary =
                    "Delete a specific key , will delete the key (AWS , OCI , TSE) and its reference in the wallet"
                response {
                    HttpStatusCode.Accepted to { description = "Key deleted" }
                    HttpStatusCode.BadRequest to { description = "Key could not be deleted" }
                }
            }) {
                val keyId = context.parameters.getOrFail("keyId")

                val success = getWalletService().deleteKey(keyId)
                context.respond(if (success) HttpStatusCode.Accepted else HttpStatusCode.BadRequest)
            }

            delete("remove", {
                summary = "Remove a specific key from the wallet"
                response {
                    HttpStatusCode.Accepted to { description = "Key removed" }
                    HttpStatusCode.BadRequest to { description = "Failed to remove the key" }
                }
            }) {
                val keyId = context.parameters.getOrFail("keyId")

                val success = getWalletService().removeKey(keyId)
                context.respond(if (success) HttpStatusCode.Accepted else HttpStatusCode.BadRequest)
            }
        }
    }
}
