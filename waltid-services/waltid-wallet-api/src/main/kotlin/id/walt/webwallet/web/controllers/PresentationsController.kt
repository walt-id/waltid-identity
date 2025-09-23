package id.walt.webwallet.web.controllers

import id.walt.oid4vc.data.CredentialFormat
import id.walt.webwallet.db.models.WalletOperationHistory
import id.walt.webwallet.web.controllers.auth.getWalletService
import io.github.smiley4.ktoropenapi.post
import io.github.smiley4.ktoropenapi.route
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.util.*
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
fun Application.presentations() = walletRoute {
    route("presentations", {
        request {}
        response {}
    }) {
        post("enveloped", {
            summary = "Generate an Enveloped Verifiable Presentation (EVP) as JWT"
            description =
                "Creates a VP-JWT from selected credentials and wraps it in an Enveloped Verifiable Presentation object as per VC Data Model 2.0."
        }) {
            val wallet = call.getWalletService()

            val req = call.receive<GenerateEnvelopedPresentationRequest>()

            val did = req.did
                ?: wallet.listDids().firstOrNull { it.default }?.did
                ?: throw IllegalArgumentException("No DID to use supplied and no default DID found")

            val selected = wallet.getCredentialsByIds(req.credentialIds)

            if (selected.isEmpty()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to "No credentials found for provided IDs"))
                return@post
            }

            val verifiableCredentials: List<JsonElement> = selected.map { cred ->
                when (cred.format) {
                    CredentialFormat.jwt_vc,
                    CredentialFormat.jwt_vc_json,
                    CredentialFormat.jwt_vc_json_ld,
                        -> {
                        val jwt = cred.document
                        buildJsonObject {
                            put("@context", JsonPrimitive("https://www.w3.org/ns/credentials/v2"))
                            put("id", JsonPrimitive("data:application/vc+jwt,$jwt"))
                            put("type", JsonPrimitive("EnvelopedVerifiableCredential"))
                        }
                    }

                    else -> throw Error("Unsupported credential format: ${cred.format}")
                }
            }

            val now = (Clock.System.now().epochSeconds)

            val vp = buildJsonObject {
                putJsonArray("@context") {
                    add(JsonPrimitive("https://www.w3.org/ns/credentials/v2"))
                    add(JsonPrimitive("https://www.w3.org/ns/credentials/examples/v2"))
                }
                putJsonArray("type") { add(JsonPrimitive("VerifiablePresentation")) }
                put("verifiableCredential", JsonArray(verifiableCredentials))
            }

            val claims = buildJsonObject {
                put("issuer", JsonPrimitive(did))
                put("validFrom", JsonPrimitive(now))
                put("validUntil", JsonPrimitive(now))
                vp.forEach { (key, value) ->
                    put(key, value)
                }
            }


            val header = buildJsonObject {
                put("alg", JsonPrimitive("RS256"))
                put("cty", JsonPrimitive("vp"))
                put("typ", JsonPrimitive("vp+jwt"))
                put("iss", JsonPrimitive(did))
                put("kid", JsonPrimitive(did))
            }

            val protectedB64 =
                Base64.getUrlEncoder().withoutPadding().encodeToString(header.toString().toByteArray(Charsets.UTF_8))
            val payloadB64 =
                Base64.getUrlEncoder().withoutPadding().encodeToString(claims.toString().toByteArray(Charsets.UTF_8))
            val jwsJson = buildJsonObject {
                put("protected", JsonPrimitive(protectedB64))
                put("payload", JsonPrimitive(payloadB64))
            }

            val jwt = wallet.sign(alias = did, data = jwsJson)
            val envelope = buildJsonObject {
                putJsonArray("type") { add(JsonPrimitive("EnvelopedVerifiablePresentation")) }
                put("verifiablePresentation", JsonPrimitive(jwt))
            }

            wallet.addOperationHistory(
                WalletOperationHistory.new(
                    tenant = wallet.tenant,
                    wallet = wallet,
                    operation = "presentations.enveloped",
                    data = mapOf(
                        "did" to did,
                        "credentials" to req.credentialIds.joinToString(),
                        "audience" to (req.audience ?: ""),
                    )
                )
            )

            call.respond(
                status = HttpStatusCode.OK,
                message = GenerateEnvelopedPresentationResponse(
                    vp_jwt = jwt,
                    envelope = envelope
                )
            )
        }
    }
}

@Serializable
data class GenerateEnvelopedPresentationRequest(
    val did: String? = null,
    val credentialIds: List<String>,
    val audience: String? = null,
    val nonce: String? = null,
    val exp: Long? = null,
)

@Serializable
data class GenerateEnvelopedPresentationResponse(
    val vp_jwt: String,
    val envelope: JsonObject,
)