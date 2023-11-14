package id.walt.issuer

import id.walt.credentials.vc.vcs.W3CVC
import id.walt.crypto.keys.*
import id.walt.did.dids.DidService
import id.walt.oid4vc.definitions.CROSS_DEVICE_CREDENTIAL_OFFER_URL
import id.walt.oid4vc.requests.CredentialOfferRequest
import io.github.smiley4.ktorswaggerui.dsl.get
import io.github.smiley4.ktorswaggerui.dsl.post
import io.github.smiley4.ktorswaggerui.dsl.route
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import kotlin.time.Duration.Companion.minutes

@Serializable
data class IssuanceRequest(
    val vc: W3CVC,
    val mapping: JsonObject? = null
) {
    companion object {
        /**
         * Return IssuanceRequest of W3CVC in `vc` and mapping in `mapping` if it has `vc`. Otherwise,
         * return complete JSON as W3CVC and no mapping.
         */
        fun fromJsonObject(jsonObj: JsonObject): IssuanceRequest {
            val maybeHasVc = jsonObj["vc"]?.jsonObject
            return when {
                maybeHasVc != null -> IssuanceRequest(W3CVC(maybeHasVc), jsonObj["mapping"]?.jsonObject)
                else -> IssuanceRequest(W3CVC(jsonObj), null)
            }
        }
    }
}

//language=json
val universityDegreeCredential = """
{
  "vc": {
    "@context": [
      "https://www.w3.org/2018/credentials/v1",
      "https://www.w3.org/2018/credentials/examples/v1"
    ],
    "id": "http://example.gov/credentials/3732",
    "type": [
      "VerifiableCredential",
      "UniversityDegreeCredential"
    ],
    "issuer": {
      "id": "did:web:vc.transmute.world"
    },
    "issuanceDate": "2020-03-10T04:24:12.164Z",
    "credentialSubject": {
      "id": "did:example:ebfeb1f712ebc6f1c276e12ec21",
      "degree": {
        "type": "BachelorDegree",
        "name": "Bachelor of Science and Arts"
      }
    }
  },
  "mapping": {
    "id": "<uuid>",
    "issuer": {"id": "<issuerDid>" },
    "credentialSubject": {"id": "<subjectDid>"},
    "issuanceDate": "<timestamp>",
    "expirationDate": "<timestamp-in:365d>"
  }
}
""".trimIndent()

//language=json
val openBadgeCredentialExampleJsonString = """
{
  "vc": {
    "@context": [
      "https://www.w3.org/2018/credentials/v1",
      "https://purl.imsglobal.org/spec/ob/v3p0/context.json"
    ],
    "id": "urn:uuid:THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION (see below)",
    "type": [
      "VerifiableCredential",
      "OpenBadgeCredential"
    ],
    "name": "JFF x vc-edu PlugFest 3 Interoperability",
    "issuer": {
      "type": [
        "Profile"
      ],
      "id": "did:key:THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION FROM CONTEXT (see below)",
      "name": "Jobs for the Future (JFF)",
      "url": "https://www.jff.org/",
      "image": "https://w3c-ccg.github.io/vc-ed/plugfest-1-2022/images/JFF_LogoLockup.png"
    },
    "issuanceDate": "2023-07-20T07:05:44Z (THIS WILL BE REPLACED BY DYNAMIC DATA FUNCTION (see below))",
    "expirationDate": "WILL BE MAPPED BY DYNAMIC DATA FUNCTION (see below)",
    "credentialSubject": {
      "id": "did:key:123 (THIS WILL BE REPLACED BY DYNAMIC DATA FUNCTION (see below))",
      "type": [
        "AchievementSubject"
      ],
      "achievement": {
        "id": "urn:uuid:ac254bd5-8fad-4bb1-9d29-efd938536926",
        "type": [
          "Achievement"
        ],
        "name": "JFF x vc-edu PlugFest 3 Interoperability",
        "description": "This wallet supports the use of W3C Verifiable Credentials and has demonstrated interoperability during the presentation request workflow during JFF x VC-EDU PlugFest 3.",
        "criteria": {
          "type": "Criteria",
          "narrative": "Wallet solutions providers earned this badge by demonstrating interoperability during the presentation request workflow. This includes successfully receiving a presentation request, allowing the holder to select at least two types of verifiable credentials to create a verifiable presentation, returning the presentation to the requestor, and passing verification of the presentation and the included credentials."
        },
        "image": {
          "id": "https://w3c-ccg.github.io/vc-ed/plugfest-3-2023/images/JFF-VC-EDU-PLUGFEST3-badge-image.png",
          "type": "Image"
        }
      }
    }
  },
  "mapping": {
    "id": "<uuid>",
    "issuer": {"id": "<issuerDid>" },
    "credentialSubject": {"id": "<subjectDid>"},
    "issuanceDate": "<timestamp>",
    "expirationDate": "<timestamp-in:365d>"
  }
}
""".trimIndent()
val openBadgeCredentialExample = Json.parseToJsonElement(openBadgeCredentialExampleJsonString).jsonObject.toMap()


val prettyJson = Json { prettyPrint = true }
val batchExample = prettyJson.encodeToString(
    JsonArray(
        listOf(
            prettyJson.parseToJsonElement(universityDegreeCredential),
            prettyJson.parseToJsonElement(openBadgeCredentialExampleJsonString),
        )
    )
)

val universityDegreeCredentialExample2 = mapOf(
    "@context" to listOf(
        "https://www.w3.org/2018/credentials/v1", "https://www.w3.org/2018/credentials/examples/v1"
    ),
    "id" to "http://example.gov/credentials/3732",
    "type" to listOf(
        "VerifiableCredential", "UniversityDegreeCredential"
    ),
    "issuer" to mapOf(
        "id" to "did:web:vc.transmute.world"
    ),
    "issuanceDate" to "2020-03-10T04:24:12.164Z",
    "credentialSubject" to mapOf(
        "id" to "did:example:ebfeb1f712ebc6f1c276e12ec21", "degree" to mapOf(
            "type" to "BachelorDegree",
            "name" to "Bachelor of Science and Arts"
        )
    ),
)

val universityDegreeCredentialSignedExample = universityDegreeCredentialExample2.plus(
    mapOf(
        "proof" to mapOf(
            "type" to "JsonWebSignature2020",
            "created" to "2020-03-21T17:51:48Z",
            "verificationMethod" to "did:web:vc.transmute.world#_Qq0UL2Fq651Q0Fjd6TvnYE-faHiOpRlPVQcY_-tA4A",
            "proofPurpose" to "assertionMethod",
            "jws" to "eyJiNjQiOmZhbHNlLCJjcml0IjpbImI2NCJdLCJhbGciOiJFZERTQSJ9..OPxskX37SK0FhmYygDk-S4csY_gNhCUgSOAaXFXDTZx86CmI5nU9xkqtLWg-f4cqkigKDdMVdtIqWAvaYx2JBA"
        )
    )
)

fun Application.issuerApi() {
    routing {
        get("/example-key") {
            context.respondText(ContentType.Application.Json) {
                KeySerialization.serializeKey(LocalKey.generate(KeyType.Ed25519))
            }
        }
        get("/example-key-transit", {
            request {
                queryParameter<String>("tse-server") { required = true }
                queryParameter<String>("tse-token") { required = true }
            }
        }) {
            println("example key transit")
            val key = TSEKey.generate(
                KeyType.Ed25519,
                TSEKeyMetadata(call.parameters["tse-server"]!!, call.parameters["tse-token"]!!)
            )

            context.respondText(ContentType.Application.Json) {
                KeySerialization.serializeKey(key)
            }
        }
        get("/example-did", {
            request { headerParameter<String>("key") }
        }) {
            val key = KeySerialization.deserializeKey(context.request.header("key")!!).getOrThrow()
            context.respond(DidService.registerByKey("key", key).did)
        }

        route("", {
            tags = listOf("Credential Issuance")
        }) {

            route("raw") {
                route("jwt") {
                    post("sign", {
                        summary = "Signs credential without using an credential exchange mechanism."
                        description =
                            "This endpoint issues (signs) an Verifiable Credential, but does not utilize an credential exchange " +
                                    "mechanism flow like OIDC or DIDComm to adapt and send the signed credential to an user. This means, that the " +
                                    "caller will have to utilize such an credential exchange mechanism themselves."

                        request {
                            headerParameter<String>("walt-key") {
                                description =
                                    "Supply a core-crypto key representation to use to issue the credential, " +
                                            "e.g. a local key (internal JWK) or a TSE key."
                                example = mapOf(
                                    "type" to "local", "jwk" to "{ ... }"
                                )
                                required = true
                            }
                            headerParameter<String>("walt-issuerDid") {
                                description = "Optionally, supply a DID to use in the proof. If no DID is passed, " +
                                        "a did:key of the supplied key will be used."
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
                            issuerKey = key,
                            issuerDid = issuerDid,
                            subjectDid = subjectDid
                        )

                        context.respond(HttpStatusCode.OK, jws)
                    }
                }
            }

            route("openid4vc") {
                route("jwt") {
                    post("issue", {
                        summary = "Signs credential and starts an OIDC credential exchange flow."
                        description = "This endpoint issues a W3C Verifiable Credential, and returns an issuance URL "

                        request {
                            headerParameter<String>("walt-key") {
                                description =
                                    "Supply a core-crypto key representation to use to issue the credential, " +
                                            "e.g. a local key (internal JWK) or a TSE key."
                                example = mapOf(
                                    "type" to "local", "jwk" to "{ ... }"
                                )
                                required = true
                            }
                            headerParameter<String>("walt-issuerDid") {
                                description = "Optionally, supply a DID to use in the proof. If no DID is passed, " +
                                        "a did:key of the supplied key will be used."
                                example = "did:ebsi:..."
                                required = false
                            }
                            body<IssuanceRequest> {
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
                        val keyJson =
                            context.request.header("walt-key") ?: throw IllegalArgumentException("No key was passed.")
                        val key = KeySerialization.deserializeKey(keyJson)
                            .onFailure { throw IllegalArgumentException("Invalid key was supplied, error occurred is: $it") }
                            .getOrThrow()
                        val issuerDid =
                            context.request.header("walt-issuerDid") ?: DidService.registerByKey("key", key).did

                        val body = context.receive<JsonObject>()
                        val issuanceRequest = IssuanceRequest.fromJsonObject(body)

                        val credentialOfferBuilder =
                            OidcIssuance.issuanceRequestsToCredentialOfferBuilder(issuanceRequest)

                        val issuanceSession = OidcApi.initializeCredentialOffer(
                            credentialOfferBuilder = credentialOfferBuilder,
                            expiresIn = 5.minutes,
                            allowPreAuthorized = true
                        )

                        //val nonce = issuanceSession.cNonce ?: throw IllegalArgumentException("No cNonce set in issuanceSession?")

                        OidcApi.setIssuanceDataForIssuanceId(
                            issuanceSession.id,
                            listOf(CIProvider.IssuanceSessionData(key, issuerDid, issuanceRequest))
                        )
                        println("issuanceSession: $issuanceSession")

                        val offerRequest = CredentialOfferRequest(issuanceSession.credentialOffer!!)
                        println("offerRequest: $offerRequest")

                        val offerUri = OidcApi.getCredentialOfferRequestUrl(
                            offerRequest,
                            CROSS_DEVICE_CREDENTIAL_OFFER_URL + OidcApi.baseUrl.removePrefix("https://")
                                .removePrefix("http://") + "/"
                        )
                        println("Offer URI: $offerUri")

                        context.respond(
                            HttpStatusCode.OK,
                            offerUri
                        )
                    }
                    post("issueBatch", {
                        summary = "Signs a list of credentials and starts an OIDC credential exchange flow."
                        description = "This endpoint issues a list W3C Verifiable Credentials, and returns an issuance URL "

                        request {
                            headerParameter<String>("walt-key") {
                                description = "Supply a core-crypto key representation to use to issue the credential, " +
                                        "e.g. a local key (internal JWK) or a TSE key."
                                example = mapOf(
                                    "type" to "local", "jwk" to "{ ... }"
                                )
                                required = true
                            }
                            headerParameter<String>("walt-issuerDid") {
                                description = "Optionally, supply a DID to use in the proof. If no DID is passed, " +
                                        "a did:key of the supplied key will be used."
                                example = "did:ebsi:..."
                                required = false
                            }
                            body<IssuanceRequest> {
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
                        val keyJson =
                            context.request.header("walt-key") ?: throw IllegalArgumentException("No key was passed.")
                        val key = KeySerialization.deserializeKey(keyJson)
                            .onFailure { throw IllegalArgumentException("Invalid key was supplied, error occurred is: $it") }
                            .getOrThrow()
                        val issuerDid = context.request.header("walt-issuerDid") ?: DidService.registerByKey("key", key).did

                        val body = context.receive<JsonArray>()
                        val issuanceRequests = body.map { IssuanceRequest.fromJsonObject(it.jsonObject) }

                        val credentialOfferBuilder = OidcIssuance.issuanceRequestsToCredentialOfferBuilder(issuanceRequests)

                        val issuanceSession = OidcApi.initializeCredentialOffer(
                            credentialOfferBuilder = credentialOfferBuilder,
                            expiresIn = 5.minutes,
                            allowPreAuthorized = true,
                            //preAuthUserPin = "1234"
                        )


                        OidcApi.setIssuanceDataForIssuanceId(
                            issuanceSession.id,
                            issuanceRequests.map { CIProvider.IssuanceSessionData(key, issuerDid, it) }
                        )
                        println("issuanceSession: $issuanceSession")

                        val offerRequest = CredentialOfferRequest(issuanceSession.credentialOffer!!)
                        println("offerRequest: $offerRequest")

                        val offerUri = OidcApi.getCredentialOfferRequestUrl(
                            offerRequest = offerRequest,
                            walletCredentialOfferEndpoint = CROSS_DEVICE_CREDENTIAL_OFFER_URL + OidcApi.baseUrl.removePrefix(
                                "https://"
                            ).removePrefix("http://") + "/"
                        )
                        println("Offer URI: $offerUri")

                        context.respond(
                            HttpStatusCode.OK,
                            offerUri
                        )
                    }
                }
                route("mdoc") {
                    post("issue", {
                        summary = "Signs a credential based on the IEC/ISO18013-5 mdoc/mDL format."
                        description = "This endpoint issues a mdoc and returns an issuance URL "

                        request {
                            headerParameter<String>("walt-key") {
                                description = "Supply a core-crypto key representation to use to issue the credential, " +
                                        "e.g. a local key (internal JWK) or a TSE key."
                                example = mapOf(
                                    "type" to "local", "jwk" to "{ ... }"
                                )
                                required = false
                            }
                        }
                    }) {
                        context.respond(HttpStatusCode.OK, "mdoc issued")
                    }
                }
            }
        }
        route("credentials", {
            tags = listOf("Credential Status")
        }){
            route("proxy") {
                // register proxy
            }
            route("status") {
                // return status-vc
            }
            route("check") {
                // return status result
            }
            route("revoke") {
                // perform revocation
                // return revocation result
            }
        }
    }
}
