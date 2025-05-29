package id.walt.issuer.issuance

import id.walt.crypto.keys.KeyManager
import id.walt.did.dids.DidService
import id.walt.issuer.issuance.OidcApi.buildCredentialOfferUri
import id.walt.issuer.issuance.OidcApi.buildOfferUri
import id.walt.issuer.issuance.OidcApi.getFormatByCredentialConfigurationId
import id.walt.oid4vc.data.CredentialFormat
import id.walt.oid4vc.requests.CredentialOfferRequest
import id.walt.w3c.vc.vcs.W3CVC
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smiley4.ktoropenapi.config.RequestConfig
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
            vct = if (credentialFormat == CredentialFormat.sd_jwt_vc) OidcApi.metadata.getVctByCredentialConfigurationId(
                it.credentialConfigurationId
            )
                ?: throw IllegalArgumentException("VCT not found") else null
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

private const val example_title = "Missing credentialData in the request body."

fun Application.issuerApi() {
    routing {
        route("", {
            tags = listOf("Credential Issuance")
        }) {
            fun RequestConfig.statusCallbackUriHeader() = headerParameter<String>("statusCallbackUri") {
                description = "Callback to push state changes of the issuance process to"
                required = false
            }

            fun RequestConfig.sessionTtlHeader() = headerParameter<Long>("sessionTtl") {
                description = "Custom session time-to-live in seconds"
                required = false
            }

            fun RoutingContext.getCallbackUriHeader() = call.request.header("statusCallbackUri")

            fun RoutingContext.getSessionTtl() = call.request.header("sessionTtl")?.toLongOrNull()?.seconds

            route("raw") {
                route("jwt") {
                    post("sign", {
                        summary = "Signs credential without using an credential exchange mechanism."
                        description =
                            "This endpoint issues (signs) an Verifiable Credential, but does not utilize an credential exchange " + "mechanism flow like OIDC or DIDComm to adapt and send the signed credential to an user. This means, that the " + "caller will have to utilize such an credential exchange mechanism themselves."

                        request {
                            body<JsonObject> {
                                description =
                                    "Pass the unsigned credential that you intend to sign as the body of the request."
                                example(
                                    "UniversityDegreeCredential example",
                                    IssuanceExamples.universityDegreeSignRequestCredentialExample
                                )
                                required = true
                            }
                        }

                        response {
                            "200" to {
                                description = "Signed Credential (with the *proof* attribute added)"
                                body<String> {
                                    example(
                                        "Signed UniversityDegreeCredential example",
                                        IssuanceExamples.universityDegreeSignResponseCredentialExample
                                    )
                                }
                            }
                            "400" to {
                                description = "The request could not be understood or was missing required parameters."
                                body<String> {
                                    example("Missing issuerKey in the request body") {
                                        value = "Missing issuerKey in the request body."
                                    }
                                    example("Invalid issuerKey format.") {
                                        value = "Invalid issuerKey format."
                                    }
                                    example("Missing subjectDid in the request body.") {
                                        value = "Missing subjectDid in the request body."
                                    }
                                    example(example_title) {
                                        value = example_title
                                    }
                                    example("Invalid credentialData format.") {
                                        value = "Invalid credentialData format."
                                    }
                                }
                            }
                        }
                    }) {
                        val body = call.receive<JsonObject>()
                        validateRawSignatureRequest(body)
                        val signedCredential = executeCredentialSigning(body)
                        call.respond(HttpStatusCode.OK, signedCredential)
                    }
                }
            }
            route("openid4vc") {

                route("jwt") {
                    post("issue", {
                        summary = "Signs credential with JWT and starts an OIDC credential exchange flow."
                        description = "This endpoint issues a W3C Verifiable Credential, and returns an issuance URL "

                        request {

                            statusCallbackUriHeader()
                            sessionTtlHeader()
                            body<IssuanceRequest> {
                                description =
                                    "Pass the unsigned credential that you intend to issue as the body of the request."
                                example(
                                    "OpenBadgeCredential example",
                                    IssuanceExamples.openBadgeCredentialIssuanceExample
                                )
                                example(
                                    "UniversityDegreeCredential example",
                                    IssuanceExamples.universityDegreeIssuanceCredentialExample
                                )
                                example(
                                    "OpenBadgeCredential example with Authorization Code Flow and Id Token",
                                    IssuanceExamples.openBadgeCredentialIssuanceExampleWithIdToken
                                )
                                example(
                                    "OpenBadgeCredential example with Authorization Code Flow and Vp Token",
                                    IssuanceExamples.openBadgeCredentialIssuanceExampleWithVpToken
                                )
                                example(
                                    "OpenBadgeCredential example with Authorization Code Flow and Username/Password Token",
                                    IssuanceExamples.openBadgeCredentialIssuanceExampleWithUsernamePassword
                                )
                                example(
                                    "EBSI-VECTOR interoperability test - InTimeIssuance Draft11",
                                    IssuanceExamples.ebsiCTExampleAuthInTimeDraft11
                                )
                                example(
                                    "EBSI-VECTOR interoperability test - DeferredIssuance Draft11",
                                    IssuanceExamples.ebsiCTExampleAuthDeferredDraft11
                                )
                                example(
                                    "EBSI-VECTOR interoperability test - PreAuthIssuance Draft11",
                                    IssuanceExamples.ebsiCTExamplePreAuthDraft11
                                )
                                example(
                                    "EBSI-VECTOR interoperability test - InTimeIssuance Draft13",
                                    IssuanceExamples.ebsiCTExampleAuthInTimeDraft13
                                )
                                example(
                                    "EBSI-VECTOR interoperability test - DeferredIssuance Draft13",
                                    IssuanceExamples.ebsiCTExampleAuthDeferredDraft13
                                )
                                example(
                                    "EBSI-VECTOR interoperability test - PreAuthIssuance Draft13",
                                    IssuanceExamples.ebsiCTExamplePreAuthDraft13
                                )
                                required = true
                            }
                        }

                        response {
                            "200" to {
                                description = "Credential signed (with the *proof* attribute added)"
                                body<String> {
                                    example("Issuance URL") {
                                        value =
                                            "openid-credential-offer://localhost/?credential_offer=%7B%22credential_issuer%22%3A%22http%3A%2F%2Flocalhost%3A8000%22%2C%22credentials%22%3A%5B%22VerifiableId%22%5D%2C%22grants%22%3A%7B%22authorization_code%22%3A%7B%22issuer_state%22%3A%22501414a4-c461-43f0-84b2-c628730c7c02%22%7D%7D%7D"
                                    }
                                }
                            }
                            "400" to {
                                description =
                                    "Bad request - The request could not be understood or was missing required parameters."
                                body<String> {
                                    example("Missing issuerKey in the request body.") {
                                        value = "Missing issuerKey in the request body."
                                    }
                                    example("Invalid issuerKey format.") {
                                        value = "Invalid issuerKey format."
                                    }
                                    example("Missing issuerDid in the request body.") {
                                        value = "Missing issuerDid in the request body."
                                    }
                                    example("Missing credentialConfigurationId in the request body.") {
                                        value = "Missing credentialConfigurationId in the request body."
                                    }
                                    example(example_title) {
                                        value = example_title
                                    }
                                    example("Invalid credentialData format.") {
                                        value = "Invalid credentialData format."
                                    }
                                }
                            }
                        }
                    }) {
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
                    post("issueBatch", {
                        summary = "Signs a list of credentials and starts an OIDC credential exchange flow."
                        description =
                            "This endpoint issues a list of W3C Verifiable Credentials, and returns an issuance URL "

                        request {
                            statusCallbackUriHeader()
                            sessionTtlHeader()
                            body<List<IssuanceRequest>> {
                                description =
                                    "Pass the unsigned credential that you intend to issue as the body of the request."
                                example("Batch example", IssuanceExamples.batchExampleJwt)
                                required = true
                            }
                        }

                        response {
                            "200" to {
                                description = "Credential signed (with the *proof* attribute added)"
                                body<String> {
                                    example("Issuance URL") {
                                        value =
                                            "openid-credential-offer://localhost/?credential_offer=%7B%22credential_issuer%22%3A%22http%3A%2F%2Flocalhost%3A8000%22%2C%22credentials%22%3A%5B%22VerifiableId%22%5D%2C%22grants%22%3A%7B%22authorization_code%22%3A%7B%22issuer_state%22%3A%22501414a4-c461-43f0-84b2-c628730c7c02%22%7D%7D%7D"
                                    }
                                }
                            }
                        }
                    }) {
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
                    post("issue", {
                        summary = "Signs credential using SD-JWT and starts an OIDC credential exchange flow."
                        description =
                            "This endpoint issues a W3C or SD-JWT-VC Verifiable Credential, and returns an issuance URL "

                        request {
                            statusCallbackUriHeader()
                            sessionTtlHeader()
                            body<IssuanceRequest> {
                                description =
                                    "Pass the unsigned credential that you intend to issue in the body of the request."
                                example("W3C SD-JWT example", IssuanceExamples.sdJwtW3CExample)
                                example("W3C SD-JWT PDA1 example", IssuanceExamples.sdJwtW3CPDA1Example)
                                example("SD-JWT-VC example", IssuanceExamples.sdJwtVCExample)
                                example(
                                    "SD-JWT-VC example featuring selectively disclosable sub and iat claims",
                                    IssuanceExamples.sdJwtVCExampleWithSDSub
                                )
                                example(
                                    "SD-JWT-VC example with issuer DID",
                                    IssuanceExamples.sdJwtVCWithIssuerDidExample
                                )
                                required = true
                            }
                        }

                        response {
                            "200" to {
                                description = "Credential signed (with the *proof* attribute added)"
                                body<String> {
                                    example("Issuance URL") {
                                        value =
                                            "openid-credential-offer://localhost/?credential_offer=%7B%22credential_issuer%22%3A%22http%3A%2F%2Flocalhost%3A8000%22%2C%22credentials%22%3A%5B%22VerifiableId%22%5D%2C%22grants%22%3A%7B%22authorization_code%22%3A%7B%22issuer_state%22%3A%22501414a4-c461-43f0-84b2-c628730c7c02%22%7D%7D%7D"
                                    }
                                }
                            }
                        }
                    }) {
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

                    post("issueBatch", {
                        summary = "Signs a list of credentials with SD and starts an OIDC credential exchange flow."
                        description =
                            "This endpoint issues a list of W3C Verifiable Credentials, and returns an issuance URL "

                        request {
                            statusCallbackUriHeader()
                            sessionTtlHeader()
                            body<List<IssuanceRequest>> {
                                description =
                                    "Pass the unsigned credential that you intend to issue as the body of the request."
                                example("Batch example", IssuanceExamples.batchExampleSdJwt)
                                required = true
                            }
                        }

                        response {
                            "200" to {
                                description = "Credential signed (with the *proof* attribute added)"
                                body<String> {
                                    example("Issuance URL") {
                                        value =
                                            "openid-credential-offer://localhost/?credential_offer=%7B%22credential_issuer%22%3A%22http%3A%2F%2Flocalhost%3A8000%22%2C%22credentials%22%3A%5B%22VerifiableId%22%5D%2C%22grants%22%3A%7B%22authorization_code%22%3A%7B%22issuer_state%22%3A%22501414a4-c461-43f0-84b2-c628730c7c02%22%7D%7D%7D"
                                    }
                                }
                            }
                        }
                    }) {
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
                    post("issue", {
                        summary = "Signs a credential based on the IEC/ISO18013-5 mdoc/mDL format."
                        description = "This endpoint issues a mdoc and returns an issuance URL "
                        request {
                            statusCallbackUriHeader()
                            sessionTtlHeader()
                            body<IssuanceRequest> {
                                description =
                                    "Pass the unsigned credential that you intend to issue as the body of the request."
                                example(
                                    "mDL/MDOC example with CWT proof",
                                    IssuanceExamples.mDLCredentialIssuanceExample
                                )
                                example(
                                    "mDL/MDOC example with JWT proof",
                                    IssuanceExamples.mDLCredentialIssuanceJwtProofExample
                                )
                                required = true
                            }
                        }
                    }) {
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

private fun validateRawSignatureRequest(body: JsonObject) {
    requireNotNull(body["issuerKey"]?.jsonObject) { "Missing issuerKey in the request body." }
    requireNotNull(body["subjectDid"]?.jsonPrimitive?.content) { "Missing subjectDid in the request body." }
    requireNotNull(body["credentialData"]?.jsonObject) { example_title }
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
