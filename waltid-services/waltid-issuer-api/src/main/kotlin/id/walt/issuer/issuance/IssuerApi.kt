package id.walt.issuer.issuance

import id.walt.crypto.keys.KeyManager
import id.walt.did.dids.DidService
import id.walt.issuer.issuance.OidcApi.buildCredentialOfferUri
import id.walt.issuer.issuance.OidcApi.buildOfferUri
import id.walt.issuer.issuance.OidcApi.getFormatByCredentialConfigurationId
import id.walt.issuer.issuance.openapi.issuerapi.JwtDocs.getJwtBatchDocs
import id.walt.issuer.issuance.openapi.issuerapi.JwtDocs.getJwtDocs
import id.walt.issuer.issuance.openapi.issuerapi.MdocDocs.getMdocsDocs
import id.walt.issuer.issuance.openapi.issuerapi.SdJwtDocs.getSdJwtBatchDocs
import id.walt.issuer.issuance.openapi.issuerapi.SdJwtDocs.getSdJwtDocs
import id.walt.issuer.issuance.openapi.issuerapi.IssuanceRequestErrors
import id.walt.issuer.issuance.openapi.issuerapi.RawJwtDocs
import id.walt.oid4vc.data.CredentialFormat
import id.walt.oid4vc.requests.CredentialOfferRequest
import id.walt.w3c.vc.vcs.W3CVC
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smiley4.ktoropenapi.post
import io.github.smiley4.ktoropenapi.route
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger {}
fun createCredentialOfferUri(
    issuanceRequests: List<IssuanceRequest>,
    credentialFormat: CredentialFormat,
    callbackUrl: String? = null,
    expiresIn: Duration = 5.minutes,
    sessionTtl: Duration? = null,
): String {
    val overwrittenIssuanceRequests = issuanceRequests.map {
        it.copy(
            credentialFormat = credentialFormat,
            vct = if (credentialFormat == CredentialFormat.sd_jwt_vc)
                OidcApi.metadata.getVctByCredentialConfigurationId(
                    it.credentialConfigurationId
                ) ?: throw IllegalArgumentException("VCT not found") else null
        )
    }

    val issuanceSession = OidcApi.initializeCredentialOffer(
        issuanceRequests = overwrittenIssuanceRequests,
        expiresIn = sessionTtl ?: expiresIn,
        callbackUrl = callbackUrl,
        standardVersion = overwrittenIssuanceRequests.first().standardVersion!!
    )

    logger.debug { "issuanceSession: $issuanceSession" }

    val offerRequest = CredentialOfferRequest(
        credentialOffer = null,
        credentialOfferUri = buildCredentialOfferUri(
            overwrittenIssuanceRequests.first().standardVersion!!,
            issuanceSession.id
        )
    )

    logger.debug { "offerRequest: $offerRequest" }

    val offerUri = buildOfferUri(overwrittenIssuanceRequests.first().standardVersion!!, offerRequest)

    logger.debug { "Offer URI: $offerUri" }

    return offerUri
}

fun Application.issuerApi() {
    routing {
        route("", {
            tags = listOf("Credential Issuance")
        }) {

            fun RoutingContext.getCallbackUriHeader() = call.request.header("statusCallbackUri")

            fun RoutingContext.getSessionTtl() = call.request.header("sessionTtl")?.toLongOrNull()?.seconds

            route("raw") {
                route("jwt") {
                    post(
                        path = "sign",
                        builder = RawJwtDocs.getRawJwtDocs()
                    ) {
                        val body = call.receive<JsonObject>()
                        validateRawSignatureRequest(body)
                        val signedCredential = executeCredentialSigning(body)
                        call.respond(HttpStatusCode.OK, signedCredential)
                    }
                }
            }

            route("openid4vc") {
                route("jwt") {
                    post("issue", getJwtDocs()) {
                        val jwtIssuanceRequest = call.receive<IssuanceRequest>()
                        val offerUri = createCredentialOfferUri(
                            listOf(jwtIssuanceRequest),
                            getFormatByCredentialConfigurationId(jwtIssuanceRequest.credentialConfigurationId)
                                ?: throw IllegalArgumentException("Invalid Credential Configuration Id"),
                            getCallbackUriHeader(),
                            sessionTtl = getSessionTtl()
                        )
                        call.respond(HttpStatusCode.OK, offerUri)
                    }

                    post("issueBatch", getJwtBatchDocs()) {
                        val issuanceRequests = call.receive<List<IssuanceRequest>>()
                        val offerUri = createCredentialOfferUri(
                            issuanceRequests,
                            getFormatByCredentialConfigurationId(issuanceRequests.first().credentialConfigurationId)
                                ?: throw IllegalArgumentException("Invalid Credential Configuration Id"),
                            getCallbackUriHeader(),
                            sessionTtl = getSessionTtl()
                        )
                        logger.debug { "Offer URI: $offerUri" }
                        call.respond(HttpStatusCode.OK, offerUri)
                    }
                }

                route("sdjwt") {
                    post("issue", getSdJwtDocs()) {
                        val sdJwtIssuanceRequest = call.receive<IssuanceRequest>()
                        val offerUri = createCredentialOfferUri(
                            listOf(sdJwtIssuanceRequest),
                            getFormatByCredentialConfigurationId(sdJwtIssuanceRequest.credentialConfigurationId)
                                ?: throw IllegalArgumentException("Invalid Credential Configuration Id"),
                            getCallbackUriHeader(),
                            sessionTtl = getSessionTtl()
                        )

                        call.respond(
                            HttpStatusCode.OK, offerUri
                        )
                    }

                    post("issueBatch", getSdJwtBatchDocs()) {
                        val sdJwtIssuanceRequests = call.receive<List<IssuanceRequest>>()
                        val offerUri =
                            createCredentialOfferUri(
                                sdJwtIssuanceRequests,
                                getFormatByCredentialConfigurationId(sdJwtIssuanceRequests.first().credentialConfigurationId)
                                    ?: throw IllegalArgumentException("Invalid Credential Configuration Id"),
                                getCallbackUriHeader(),
                                sessionTtl = getSessionTtl()
                            )

                        logger.debug { "Offer URI: $offerUri" }

                        call.respond(
                            HttpStatusCode.OK, offerUri
                        )
                    }
                }

                route("mdoc") {
                    post("issue", getMdocsDocs()) {
                        val mdocIssuanceRequest = call.receive<IssuanceRequest>()
                        val offerUri = createCredentialOfferUri(
                            listOf(mdocIssuanceRequest),
                            getFormatByCredentialConfigurationId(mdocIssuanceRequest.credentialConfigurationId)
                                ?: throw IllegalArgumentException("Invalid Credential Configuration Id"),
                            getCallbackUriHeader(),
                            sessionTtl = getSessionTtl()
                        )
                        call.respond(
                            HttpStatusCode.OK, offerUri
                        )
                    }
                }
            }
        }
    }
}

fun validateRawSignatureRequest(body: JsonObject) {
    requireNotNull(body["issuerKey"]?.jsonObject) { IssuanceRequestErrors.MISSING_ISSUER_KEY }
    requireNotNull(body["subjectDid"]?.jsonPrimitive?.content) { IssuanceRequestErrors.MISSING_SUBJECT_DID }
    requireNotNull(body["credentialData"]?.jsonObject) { IssuanceRequestErrors.MISSING_CREDENTIAL_DATA }
}

private suspend fun <T, k : Exception> executeWrapping(
    runner: suspend () -> T, exception: KClass<k>, message: (() -> String)? = null
): T {
    runCatching {
        runner()
    }.fold(
        onSuccess = { return it },
        onFailure = {
            if (it::class == exception) {
                throw BadRequestException(message?.invoke() ?: it.message ?: "Bad request")
            } else {
                throw it
            }
        }
    )
}

private suspend fun <T> requireValue(runner: suspend () -> T, message: (() -> String)? = null): T = executeWrapping(
    runner, IllegalStateException::class, message
)

private suspend fun <T> checkValue(runner: suspend () -> T, message: (() -> String)? = null): T = executeWrapping(
    runner, IllegalStateException::class, message
)

private suspend fun executeCredentialSigning(body: JsonObject) = run {
    val issuerKey =
        requireValue({ KeyManager.resolveSerializedKey(body["issuerKey"]!!.jsonObject) }) { "Invalid issuerKey Format" }
    val issuerDid = body["subjectDid"]?.jsonPrimitive?.content ?: DidService.registerByKey("key", issuerKey).did
    val vc =
        requireValue({ W3CVC.fromJson(body["credentialData"]!!.jsonObject.toString()) }) { "Invalid credential format" }
    val subjectDid = body["subjectDid"]!!.jsonPrimitive.content

    checkValue(
        {
            vc.signJws(
                issuerKey = issuerKey,
                issuerId = issuerDid,
                subjectDid = subjectDid
            )
        }
    ) {
        "Failed to sign the credential"
    }
}
