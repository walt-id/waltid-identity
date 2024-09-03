package id.walt.issuer.issuance

import id.walt.credentials.vc.vcs.W3CVC
import id.walt.crypto.keys.KeyManager
import id.walt.crypto.keys.KeySerialization
import id.walt.did.dids.DidService
import id.walt.oid4vc.data.AuthenticationMethod
import id.walt.oid4vc.definitions.CROSS_DEVICE_CREDENTIAL_OFFER_URL
import id.walt.oid4vc.requests.CredentialOfferRequest
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smiley4.ktorswaggerui.dsl.routes.OpenApiRequest
import io.github.smiley4.ktorswaggerui.dsl.routing.get
import io.github.smiley4.ktorswaggerui.dsl.routing.post
import io.github.smiley4.ktorswaggerui.dsl.routing.route
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*
import kotlinx.serialization.json.*
import redis.clients.jedis.exceptions.JedisAccessControlException
import redis.clients.jedis.exceptions.JedisConnectionException
import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

private val logger = KotlinLogging.logger {}
suspend fun createCredentialOfferUri(
    issuanceRequests: List<IssuanceRequest>,
    issuanceType: IssuanceType,
    callbackUrl: String? = null,
    expiresIn: Duration = 5.minutes,
): String {
    val overwrittenIssuanceRequests = issuanceRequests.map { it.copy(issuanceType = issuanceType) }

    val credentialOfferBuilder =
        OidcIssuance.issuanceRequestsToCredentialOfferBuilder(overwrittenIssuanceRequests)

    val issuanceSession = OidcApi.initializeCredentialOffer(
        credentialOfferBuilder = credentialOfferBuilder,
        expiresIn,
        allowPreAuthorized = when (overwrittenIssuanceRequests[0].authenticationMethod) {
            AuthenticationMethod.PRE_AUTHORIZED -> true
            else -> false
        }
    )

    OidcApi.setIssuanceDataForIssuanceId(issuanceSession.id, overwrittenIssuanceRequests.map {
        val key = KeyManager.resolveSerializedKey(it.issuerKey)

        CIProvider.IssuanceSessionData(
            id = issuanceSession.id,
            issuerKey = key,
            issuerDid = it.issuerDid,
            request = it,
            callbackUrl = callbackUrl
        )
    })  // TODO: Hack as this is non stateless because of oidc4vc lib API

    logger.debug { "issuanceSession: $issuanceSession" }

    val offerRequest =
        CredentialOfferRequest(null, "${OidcApi.baseUrl}/openid4vc/credentialOffer?id=${issuanceSession.id}")
    logger.debug { "offerRequest: $offerRequest" }

    val offerUri = OidcApi.getCredentialOfferRequestUrl(
        offerRequest,
        CROSS_DEVICE_CREDENTIAL_OFFER_URL + OidcApi.baseUrl.removePrefix("https://").removePrefix("http://") + "/"
    )
    logger.debug { "Offer URI: $offerUri" }
    return offerUri
}

fun Application.issuerApi() {
    routing {
        route("onboard", {
            tags = listOf("Issuer onboarding")
        }) {
            post("issuer", {
                summary = "Onboards a new issuer."
                description = "Creates an issuer keypair and an associated DID based on the provided configuration."

                request {
                    body<OnboardingRequest> {
                        description = "Issuer onboarding request (key & DID) config."
                        example(
                            "did:jwk + JWK key (Ed25519)",
                            IssuanceExamples.issuerOnboardingRequestDefaultEd25519Example
                        )
                        example(
                            "did:jwk + JWK key (secp256r1)",
                            IssuanceExamples.issuerOnboardingRequestDefaultSecp256r1Example
                        )
                        example(
                            "did:jwk + JWK key (secp256k1)",
                            IssuanceExamples.issuerOnboardingRequestDefaultSecp256k1Example
                        )
                        example("did:jwk + JWK key (RSA)", IssuanceExamples.issuerOnboardingRequestDefaultRsaExample)
                        example("did:web + JWK key (Secp256k1)", IssuanceExamples.issuerOnboardingRequestDidWebExample)
                        example(
                            "did:key + TSE key (Hashicorp Vault Transit Engine - Ed25519) + AppRole (Auth)",
                            IssuanceExamples.issuerOnboardingRequestTseExampleAppRole
                        )
                        example(
                            "did:key + TSE key (Hashicorp Vault Transit Engine - Ed25519) + UserPass (Auth)",
                            IssuanceExamples.issuerOnboardingRequestTseExampleUserPass
                        )
                        example(
                            "did:key + TSE key (Hashicorp Vault Transit Engine - Ed25519) + AccessKey (Auth)",
                            IssuanceExamples.issuerOnboardingRequestTseExampleAccessKey
                        )
                        example(
                            "did:jwk + OCI key (Oracle Cloud Infrastructure - Secp256r1)",
                            IssuanceExamples.issuerOnboardingRequestOciExample
                        )
                        example(
                            "did:jwk + OCI REST API key  (Oracle Cloud Infrastructure - Secp256r1)",
                            IssuanceExamples.issuerOnboardingRequestOciRestApiExample
                        )
                        required = true
                    }
                }

                response {
                    "200" to {
                        description = "Issuer onboarding response"
                        body<IssuerOnboardingResponse> {
                            example(
                                "Local JWK key (Secp256r1) + did:jwk",
                                IssuanceExamples.issuerOnboardingResponseDefaultExample,
                            )
                            example(
                                "Local JWK key (Secp256r1) + did:web",
                                IssuanceExamples.issuerOnboardingResponseDidWebExample,
                            )
                            example(
                                "Remote TSE Ed25519 key + did:key",
                                IssuanceExamples.issuerOnboardingResponseTseExample,
                            )
                            example(
                                "Remote OCI Secp256r1 key + did:jwk",
                                IssuanceExamples.issuerOnboardingResponseOciExample,
                            )
                            example(
                                "Remote OCI REST API Secp256r1 key + did:jwk",
                                IssuanceExamples.issuerOnboardingResponseOciRestApiExample,
                            )
                        }
                    }
                    "400" to {
                        description = "Bad request"

                    }
                }
            }) {
                val req = context.receive<OnboardingRequest>()
                val keyConfig = req.key.config?.mapValues { (key, value) ->
                    if (key == "signingKeyPem") {
                        JsonPrimitive(value.jsonPrimitive.content.trimIndent().replace(" ", ""))

                    } else {
                        value
                    }
                }

                val keyGenerationRequest = req.key.copy(config = keyConfig?.let { it1 -> JsonObject(it1) })
                val key = KeyManager.createKey(keyGenerationRequest)

                val did = DidService.registerDefaultDidMethodByKey(
                    req.did.method,
                    key,
                    req.did.config?.mapValues { it.value.jsonPrimitive } ?: emptyMap()).did


                val serializedKey = KeySerialization.serializeKeyToJson(key)


                val issuanceKey = if (req.key.backend == "jwk") {
                    val jsonObject = serializedKey.jsonObject
                    val jwkObject = jsonObject["jwk"] ?: throw IllegalArgumentException(
                        "No JWK key found in serialized key."
                    )
                    val finalJsonObject = jsonObject.toMutableMap().apply {
                        this["jwk"] = jwkObject.jsonObject
                    }
                    JsonObject(finalJsonObject)
                } else {
                    serializedKey
                }
                context.respond(
                    HttpStatusCode.OK, IssuerOnboardingResponse(issuanceKey, did)
                )
            }
        }
        route("", {
            tags = listOf("Credential Issuance")
        }) {
            fun OpenApiRequest.statusCallbackUriHeader() = headerParameter<String>("statusCallbackUri") {
                description = "Callback to push state changes of the issuance process to"
                required = false
            }

            fun PipelineContext<Unit, ApplicationCall>.getCallbackUriHeader() = context.request.header("statusCallbackUri")

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

//                        response {
//                            "200" to {
//                                description = "Signed Credential (with the *proof* attribute added)"
//                                body<String> {
//                                    example(
//                                        "Signed UniversityDegreeCredential example",
//                                        IssuanceExamples.universityDegreeSignResponseCredentialExample
//                                    )
//                                }
//                            }
//                            "400" to {
//                                description = "The request could not be understood or was missing required parameters."
//                                body<String> {
//                                    example("Missing issuerKey in the request body") {
//                                        value = "Missing issuerKey in the request body."
//                                    }
//                                    example("Invalid issuerKey format.") {
//                                        value = "Invalid issuerKey format."
//                                    }
//                                    example("Missing subjectDid in the request body.") {
//                                        value = "Missing subjectDid in the request body."
//                                    }
//                                    example("Missing credentialData in the request body.") {
//                                        value = "Missing credentialData in the request body."
//                                    }
//                                    example("Invalid credentialData format.") {
//                                        value = "Invalid credentialData format."
//                                    }
//                                }
//                            }
//                        }
                    }) {
                        runCatching {
                            val body = context.receive<JsonObject>()
                            validateRawSignatureRequest(body)
                            val signedCredential = executeCredentialSigning(body)
                            context.respond(HttpStatusCode.OK, signedCredential)
                        }.onFailure {
                            throwError(it)
                        }
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
                            body<IssuanceRequest> {
                                description =
                                    "Pass the unsigned credential that you intend to issue as the body of the request."
                                example("OpenBadgeCredential example", IssuanceExamples.openBadgeCredentialIssuanceExample)
                                example("UniversityDegreeCredential example", IssuanceExamples.universityDegreeIssuanceCredentialExample)
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
                                required = true
                            }
                        }

//                        response {
//                            "200" to {
//                                description = "Credential signed (with the *proof* attribute added)"
//                                body<String> {
//                                    example("Issuance URL") {
//                                        value =
//                                            "openid-credential-offer://localhost/?credential_offer=%7B%22credential_issuer%22%3A%22http%3A%2F%2Flocalhost%3A8000%22%2C%22credentials%22%3A%5B%22VerifiableId%22%5D%2C%22grants%22%3A%7B%22authorization_code%22%3A%7B%22issuer_state%22%3A%22501414a4-c461-43f0-84b2-c628730c7c02%22%7D%7D%7D"
//                                    }
//                                }
//                            }
//                            "400" to {
//                                description =
//                                    "Bad request - The request could not be understood or was missing required parameters."
//                                body<String> {
//                                    example("Missing issuerKey in the request body.") {
//                                        value = "Missing issuerKey in the request body."
//                                    }
//                                    example("Invalid issuerKey format.") {
//                                        value = "Invalid issuerKey format."
//                                    }
//                                    example("Missing issuerDid in the request body.") {
//                                        value = "Missing issuerDid in the request body."
//                                    }
//                                    example("Missing credentialConfigurationId in the request body.") {
//                                        value = "Missing credentialConfigurationId in the request body."
//                                    }
//                                    example("Missing credentialData in the request body.") {
//                                        value = "Missing credentialData in the request body."
//                                    }
//                                    example("Invalid credentialData format.") {
//                                        value = "Invalid credentialData format."
//                                    }
//                                }
//                            }
//                        }
                    }) {
                        runCatching {
                            val jwtIssuanceRequest = context.receive<IssuanceRequest>()
                            val offerUri = createCredentialOfferUri(listOf(jwtIssuanceRequest), IssuanceType.w3c, getCallbackUriHeader())
                            context.respond(HttpStatusCode.OK, offerUri)
                        }.onFailure {
                            throwError(it)
                        }

                    }
                    post("issueBatch", {
                        summary = "Signs a list of credentials and starts an OIDC credential exchange flow."
                        description =
                            "This endpoint issues a list of W3C Verifiable Credentials, and returns an issuance URL "

                        request {
                            statusCallbackUriHeader()
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


                        runCatching {
                            val issuanceRequests = context.receive<List<IssuanceRequest>>()
                            val offerUri = createCredentialOfferUri(issuanceRequests, IssuanceType.w3c, getCallbackUriHeader())
                            logger.debug { "Offer URI: $offerUri" }

                            context.respond(
                                HttpStatusCode.OK, offerUri
                            )
                        }.onFailure {
                            throwError(it)
                        }
                    }
                }

                route("sdjwt") {
                    post("issue", {
                        summary = "Signs credential using SD-JWT and starts an OIDC credential exchange flow."
                        description =
                            "This endpoint issues a W3C or SD-JWT-VC Verifiable Credential, and returns an issuance URL "

                        request {
                            statusCallbackUriHeader()
                            body<IssuanceRequest> {
                                description =
                                    "Pass the unsigned credential that you intend to issue in the body of the request."
                                example("W3C SD-JWT example", IssuanceExamples.sdJwtW3CExample)
                                example("SD-JWT-VC example", IssuanceExamples.sdJwtVCExample)
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
                        runCatching {
                            val sdJwtIssuanceRequest = context.receive<IssuanceRequest>()
                            val offerUri = createCredentialOfferUri(listOf(sdJwtIssuanceRequest), IssuanceType.sdjwt, getCallbackUriHeader())
                            context.respond(HttpStatusCode.OK, offerUri)
                        }.onFailure {
                            throwError(it)
                        }
                    }

                    post("issueBatch", {
                        summary = "Signs a list of credentials with SD and starts an OIDC credential exchange flow."
                        description =
                            "This endpoint issues a list of W3C Verifiable Credentials, and returns an issuance URL "

                        request {
                            statusCallbackUriHeader()
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


                        runCatching {
                            val issuanceRequests = context.receive<List<IssuanceRequest>>()
                            val offerUri = createCredentialOfferUri(issuanceRequests, IssuanceType.sdjwt, getCallbackUriHeader())
                            logger.debug { "Offer URI: $offerUri" }

                            context.respond(
                                HttpStatusCode.OK, offerUri
                            )
                        }.onFailure {
                            throwError(it)
                        }
                    }
                }

                route("mdoc") {
                    post("issue", {
                        summary = "Signs a credential based on the IEC/ISO18013-5 mdoc/mDL format."
                        description = "This endpoint issues a mdoc and returns an issuance URL "
                        request {
                            statusCallbackUriHeader()
                            body<IssuanceRequest> {
                                description =
                                    "Pass the unsigned credential that you intend to issue as the body of the request."
                                example("mDL/MDOC example", IssuanceExamples.mDLCredentialIssuanceExample)
                                required = true
                            }
                        }
                    }) {
                        val mdocIssuanceRequest = context.receive<IssuanceRequest>()
                        val offerUri = createCredentialOfferUri(listOf(mdocIssuanceRequest), IssuanceType.mdoc, getCallbackUriHeader())

                        context.respond(
                            HttpStatusCode.OK, offerUri
                        )
                    }
                }

                get("credentialOffer", {
                    summary = "Gets a credential offer based on the session id"
                    request {
                        queryParameter<String>("id") { required = true }
                    }
                }) {
                    val sessionId = call.parameters["id"] ?: throw BadRequestException("Missing parameter \"id\"")
                    val issuanceSession = OidcApi.getSession(sessionId)
                        ?: throw NotFoundException("No active issuance session found by the given id")
                    val credentialOffer = issuanceSession.credentialOffer
                        ?: throw BadRequestException("Session has no credential offer set")

                    OidcApi.sessionCredentialPreMapping[sessionId]?.first()
                        ?.sendCallback("resolved_credential_offer", credentialOffer.toJSON())

                    context.respond(credentialOffer.toJSON())
                }
            }
        }
    }
}

private suspend fun PipelineContext<Unit, ApplicationCall>.throwError(
    it: Throwable
) {
    when (it) {
        is JedisConnectionException -> context.respond(
            HttpStatusCode.InternalServerError,
            "Distributed session management couldn't be initialized : Cannot connect to redis server."
        )

        is JedisAccessControlException -> context.respond(
            HttpStatusCode.InternalServerError,
            "Distributed session management couldn't be initialized : Cannot access redis server, wrong username/password."
        )

        else -> throw it
    }
}

private fun validateRawSignatureRequest(body: JsonObject) {
    requireNotNull(body["issuerKey"]?.jsonObject) { "Missing issuerKey in the request body." }
    requireNotNull(body["subjectDid"]?.jsonPrimitive?.content) { "Missing subjectDid in the request body." }
    requireNotNull(body["credentialData"]?.jsonObject) { "Missing credentialData in the request body." }
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
                issuerDid = issuerDid,
                subjectDid = subjectDid
            )
        }
    ) {
        "Failed to sign the credential"
    }
}
