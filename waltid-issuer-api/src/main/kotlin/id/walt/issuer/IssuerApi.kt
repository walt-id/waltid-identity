package id.walt.issuer

import id.walt.credentials.vc.vcs.W3CVC
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeySerialization
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.keys.tse.TSEKey
import id.walt.crypto.keys.tse.TSEKeyMetadata
import id.walt.did.dids.DidService
import id.walt.did.dids.registrar.dids.DidCreateOptions
import id.walt.issuer.IssuanceExamples.batchExample
import id.walt.issuer.IssuanceExamples.issuerOnboardingRequestDefaultExample
import id.walt.issuer.IssuanceExamples.issuerOnboardingRequestDidWebExample
import id.walt.issuer.IssuanceExamples.issuerOnboardingRequestTseExample
import id.walt.issuer.IssuanceExamples.issuerOnboardingResponseDefaultExample
import id.walt.issuer.IssuanceExamples.issuerOnboardingResponseTseExample
import id.walt.issuer.IssuanceExamples.openBadgeCredentialExampleJsonString
import id.walt.issuer.IssuanceExamples.sdJwtExample
import id.walt.issuer.IssuanceExamples.universityDegreeCredential
import id.walt.issuer.IssuanceExamples.universityDegreeCredentialExample2
import id.walt.issuer.IssuanceExamples.universityDegreeCredentialSignedExample
import id.walt.oid4vc.definitions.CROSS_DEVICE_CREDENTIAL_OFFER_URL
import id.walt.oid4vc.requests.CredentialOfferRequest
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smiley4.ktorswaggerui.dsl.get
import io.github.smiley4.ktorswaggerui.dsl.post
import io.github.smiley4.ktorswaggerui.dsl.route
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.*
import kotlin.time.Duration.Companion.minutes

private val logger = KotlinLogging.logger {}
suspend fun createCredentialOfferUri(issuanceRequests: List<BaseIssuanceRequest>): String {
    val credentialOfferBuilder = OidcIssuance.issuanceRequestsToCredentialOfferBuilder(issuanceRequests)

    val issuanceSession = OidcApi.initializeCredentialOffer(
        credentialOfferBuilder = credentialOfferBuilder, expiresIn = 5.minutes, allowPreAuthorized = true
    )
    OidcApi.setIssuanceDataForIssuanceId(issuanceSession.id, issuanceRequests.map {
        CIProvider.IssuanceSessionData(KeySerialization.deserializeKey(it.issuerKey)
            .onFailure { throw IllegalArgumentException("Invalid key was supplied, error occurred is: $it") }
            .getOrThrow(), it.issuerDid, it)
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
                    body<IssuerOnboardingRequest> {
                        description = "Issuer onboarding request (key & DID) config."
                        example("did:jwk + JWK key (Ed25519)", issuerOnboardingRequestDefaultExample)
                        example(
                            "did:key + TSE (Hashicorp Vault Transit Engine key - RSA)",
                            issuerOnboardingRequestTseExample
                        )
                        example("did:web + JWK key (Secp256k1)", issuerOnboardingRequestDidWebExample)
                        required = true
                    }
                }

                response {
                    "200" to {
                        description = "Issuer onboarding response"
                        body<IssuerOnboardingResponse> {
                            example(
                                "did:web + JWK key (Secp256r1)",
                                issuerOnboardingResponseDefaultExample,
                            )
                            example(
                                "did:key + TSE (Hashicorp Vault Transit Engine key - Ed25519)",
                                issuerOnboardingResponseTseExample,
                            )
                        }
                    }
                }
            }) {
                val req = context.receive<IssuerOnboardingRequest>()

                logger.debug { "Onboarding issuer according config: $req" }

                // Generate key

                val keyType = getParamOrThrow(
                    req.issuerKeyConfig["type"], "Mandatory issuerKeyConfig param 'type' not provided"
                )
                val keyAlgorithm = getParamOrThrow(
                    req.issuerKeyConfig["algorithm"], "Mandatory issuerKeyConfig param 'algorithm' not provided"
                ).let { KeyType.valueOf(it) }

                val (key, jsonKey) = generateJsonKey(keyType, keyAlgorithm, req)

                logger.debug { "Key created: $key" }

                // Generate DID

                val didMethod = getParamOrThrow(
                    req.issuerDidConfig["method"], "Mandatory issuerDidConfig param 'method' not provided"
                )

                val didDoc = DidService.registerByKey(
                    didMethod,
                    key,
                    DidCreateOptions(didMethod, req.issuerDidConfig as JsonElement)
                )

                logger.debug { "DID created: $didDoc" }

                context.respond(
                    HttpStatusCode.OK, IssuerOnboardingResponse(jsonKey, didDoc)
                )
            }
        }
        route("", {
            tags = listOf("Credential Issuance")
        }) {

            route("raw") {
                route("jwt") {
                    post("sign", {
                        summary = "Signs credential without using an credential exchange mechanism."
                        description =
                            "This endpoint issues (signs) an Verifiable Credential, but does not utilize an credential exchange " + "mechanism flow like OIDC or DIDComm to adapt and send the signed credential to an user. This means, that the " + "caller will have to utilize such an credential exchange mechanism themselves."

                        request {
                            headerParameter<String>("walt-key") {
                                description =
                                    "Supply a core-crypto key representation to use to issue the credential, " + "e.g. a local key (internal JWK) or a TSE key."
                                example = mapOf(
                                    "type" to "jwk", "jwk" to "{ ... }"
                                )
                                required = true
                            }
                            headerParameter<String>("walt-issuerDid") {
                                description =
                                    "Optionally, supply a DID to use in the proof. If no DID is passed, " + "a did:key of the supplied key will be used."
                                example = "did:ebsi:..."
                                required = false
                            }
                            headerParameter<String>("walt-subjectDid") {
                                description = "Supply the DID of the subject that will receive the credential"
                                example = "did:key:..."
                                required = true
                            }

                            body<JsonObject> {
                                description =
                                    "Pass the unsigned credential that you intend to sign as the body of the request."
                                example("OpenBadgeCredential example", openBadgeCredentialExampleJsonString)
                                example("UniversityDegreeCredential example", universityDegreeCredentialExample2)
                                required = true
                            }
                        }

                        response {
                            "200" to {
                                description = "Signed Credential (with the *proof* attribute added)"
                                body<JsonObject> {
                                    example(
                                        "Signed UniversityDegreeCredential example",
                                        universityDegreeCredentialSignedExample
                                    )
                                }
                            }
                        }
                    }) {
                        val keyJson =
                            context.request.header("walt-key") ?: throw IllegalArgumentException("No key was passed.")
                        val subjectDid = context.request.header("walt-subjectDid")
                            ?: throw IllegalArgumentException("No subjectDid was passed.")

                        val key = KeySerialization.deserializeKey(keyJson).getOrThrow()

                        val issuerDid =
                            context.request.header("walt-issuerDid") ?: DidService.registerByKey("key", key).did

                        val body = context.receive<Map<String, JsonElement>>()

                        val vc = W3CVC(body)

                        // Sign VC
                        val jws = vc.signJws(
                            issuerKey = key, issuerDid = issuerDid, subjectDid = subjectDid
                        )

                        context.respond(HttpStatusCode.OK, jws)
                    }
                }
            }

            route("openid4vc") {
                route("jwt") {
                    post("issue", {
                        summary = "Signs credential with JWT and starts an OIDC credential exchange flow."
                        description = "This endpoint issues a W3C Verifiable Credential, and returns an issuance URL "

                        request {
                            body<JwtIssuanceRequest> {
                                description =
                                    "Pass the unsigned credential that you intend to issue as the body of the request."
                                example("OpenBadgeCredential example", openBadgeCredentialExampleJsonString)
                                example("UniversityDegreeCredential example", universityDegreeCredential)
                                required = true
                            }
                        }

                        response {
                            "200" to {
                                description = "Credential signed (with the *proof* attribute added)"
                                body<String> {
                                    example(
                                        "Issuance URL URL",
                                        "openid-credential-offer://localhost/?credential_offer=%7B%22credential_issuer%22%3A%22http%3A%2F%2Flocalhost%3A8000%22%2C%22credentials%22%3A%5B%22VerifiableId%22%5D%2C%22grants%22%3A%7B%22authorization_code%22%3A%7B%22issuer_state%22%3A%22501414a4-c461-43f0-84b2-c628730c7c02%22%7D%7D%7D"
                                    )
                                }
                            }
                        }
                    }) {
                        val jwtIssuanceRequest = context.receive<JwtIssuanceRequest>()
                        val offerUri = createCredentialOfferUri(listOf(jwtIssuanceRequest))

                        context.respond(
                            HttpStatusCode.OK, offerUri
                        )
                    }
                    post("issueBatch", {
                        summary = "Signs a list of credentials and starts an OIDC credential exchange flow."
                        description =
                            "This endpoint issues a list W3C Verifiable Credentials, and returns an issuance URL "

                        request {
                            body<List<JwtIssuanceRequest>> {
                                description =
                                    "Pass the unsigned credential that you intend to issue as the body of the request."
                                example("Batch example", batchExample)
                                required = true
                            }
                        }

                        response {
                            "200" to {
                                description = "Credential signed (with the *proof* attribute added)"
                                body<String> {
                                    example(
                                        "Issuance URL URL",
                                        "openid-credential-offer://localhost/?credential_offer=%7B%22credential_issuer%22%3A%22http%3A%2F%2Flocalhost%3A8000%22%2C%22credentials%22%3A%5B%22VerifiableId%22%5D%2C%22grants%22%3A%7B%22authorization_code%22%3A%7B%22issuer_state%22%3A%22501414a4-c461-43f0-84b2-c628730c7c02%22%7D%7D%7D"
                                    )
                                }
                            }
                        }
                    }) {


                        val issuanceRequests = context.receive<List<JwtIssuanceRequest>>()
                        val offerUri = createCredentialOfferUri(issuanceRequests)
                        logger.debug { "Offer URI: $offerUri" }

                        context.respond(
                            HttpStatusCode.OK, offerUri
                        )
                    }
                }
                route("sdjwt") {
                    post("issue", {
                        summary = "Signs credential and starts an OIDC credential exchange flow."
                        description = "This endpoint issues a W3C Verifiable Credential, and returns an issuance URL "

                        request {
                            body<SdJwtIssuanceRequest> {
                                description =
                                    "Pass the unsigned credential that you intend to issue as the body of the request."
                                example("SD-JWT example", sdJwtExample)
                                //example("UniversityDegreeCredential example", universityDegreeCredential)
                                required = true
                            }
                        }

                        response {
                            "200" to {
                                description = "Credential signed (with the *proof* attribute added)"
                                body<String> {
                                    example(
                                        "Issuance URL URL",
                                        "openid-credential-offer://localhost/?credential_offer=%7B%22credential_issuer%22%3A%22http%3A%2F%2Flocalhost%3A8000%22%2C%22credentials%22%3A%5B%22VerifiableId%22%5D%2C%22grants%22%3A%7B%22authorization_code%22%3A%7B%22issuer_state%22%3A%22501414a4-c461-43f0-84b2-c628730c7c02%22%7D%7D%7D"
                                    )
                                }
                            }
                        }
                    }) {
                        val sdJwtIssuanceRequest = context.receive<SdJwtIssuanceRequest>()

                        val offerUri = createCredentialOfferUri(listOf(sdJwtIssuanceRequest))

                        context.respond(
                            HttpStatusCode.OK, offerUri
                        )
                    }
                }
                route("mdoc") {
                    post("issue", {
                        summary = "Signs a credential based on the IEC/ISO18013-5 mdoc/mDL format."
                        description = "This endpoint issues a mdoc and returns an issuance URL "

                        request {
                            headerParameter<String>("walt-key") {
                                description =
                                    "Supply a core-crypto key representation to use to issue the credential, " + "e.g. a local key (internal JWK) or a TSE key."
                                example = mapOf(
                                    "type" to "jwk", "jwk" to "{ ... }"
                                )
                                required = false
                            }
                        }
                    }) {
                        context.respond(HttpStatusCode.OK, "mdoc issued")
                    }
                }
                get("credentialOffer", {
                    summary = "Gets a credential offer based on the session id"
                    request {
                        queryParameter<String>("id") { required = true }
                    }
                }) {
                    val sessionId = call.parameters.get("id") ?: throw BadRequestException("Missing parameter \"id\"")
                    val issuanceSession = OidcApi.getSession(sessionId)
                        ?: throw NotFoundException("No active issuance session found by the given id")
                    val credentialOffer = issuanceSession.credentialOffer
                        ?: throw BadRequestException("Session has no credential offer set")
                    context.respond(credentialOffer.toJSON())
                }
            }
        }
    }
}

private suspend fun generateJsonKey(
    keyType: String, keyAlgorithm: KeyType, req: IssuerOnboardingRequest
): Pair<Key, JsonElement> {
    val key = when (keyType) {
        "jwk" -> JWKKey.generate(keyAlgorithm)
        "tse" -> TSEKey.generate(
            keyAlgorithm, TSEKeyMetadata(
                getParamOrThrow(
                    req.issuerKeyConfig["tseServer"], "Mandatory issuerKeyConfig param 'tseServer' not provided"
                ), getParamOrThrow(
                    req.issuerKeyConfig["tseAccessToken"],
                    "Mandatory issuerKeyConfig param 'tseAccessToken' not provided"
                )
            )
        )

        else -> {
            JWKKey.generate(KeyType.Ed25519)
        }
    }

    // TODO: serialize TSE key the same way as the local key
    val jsonKey = if (keyType == "tse") {
        KeySerialization.serializeKeyToJson(key)
    } else {
        // TODO: serialized the internal jwk to avoid this construct
        val jsonKey = """
                        {
                            "type" : "${keyType}",
                            "jwk" : ${key.exportJWKObject()}
                        }
                    """.trimIndent()
        Json.parseToJsonElement(jsonKey)
    }

    return key to jsonKey
}

private fun getParamOrThrow(element: JsonElement?, errorMessage: String) =
    element?.jsonPrimitive?.contentOrNull ?: throw IllegalArgumentException(errorMessage)
