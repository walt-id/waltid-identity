@file:OptIn(ExperimentalUuidApi::class)

import id.walt.commons.testing.E2ETest
import id.walt.crypto.keys.KeyGenerationRequest
import id.walt.crypto.keys.KeyType
import id.walt.crypto.utils.JsonUtils.toJsonElement
import id.walt.test.integration.environment.api.wallet.KeysApi
import id.walt.test.integration.expectSuccess
import id.walt.webwallet.db.models.WalletCredential
import id.walt.webwallet.web.controllers.exchange.UsePresentationRequest
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.util.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

//TODO: needs to be ported to JUnit test
class EBSIVectorInteropTest(
    private val e2e: E2ETest,
    private val httpClient: HttpClient,
    private val wallet: Uuid,
) {

    private val keysApi: KeysApi = KeysApi(e2e, httpClient)
    private var keyId: String
    private var did: String

    init {

        runBlocking {
            keyId = keysApi.generate(
                wallet,
                KeyGenerationRequest(
                    backend = "jwk",
                    keyType = KeyType.secp256r1
                )
            )

            did = httpClient.post("/wallet-api/wallet/$wallet/dids/create/key?useJwkJcsPub=true&keyId=$keyId")
                .expectSuccess().bodyAsText()
        }
    }

    private suspend fun claimAndPresentCredentialIzertis() = e2e.test(
        name = "EBSI Vector credential claim and present test case: Our wallet API with the credential issuer and verifier of Izertis",
    ) {

        val offerUri =
            "openid-credential-offer://?credential_offer_uri=https%3A%2F%2Fidentfy.izer.tech%2Foffers%2F17116185-6ae0-4785-9f2e-29c772f9af16%3Frequested_vc_types%3DPreAuthIssuance%26pre-authorized_code%3Dabc"
        val issuerName = "Izertis"
        val presentationRequestUrl =
            "openid://?request_uri=https%3A%2F%2Fidentfy.izer.tech%2F837d5d44-be2e-4fc5-a13c-520571dd8fcd%2Fpresentation-offer"

        lateinit var credentials: List<WalletCredential>

        claimCredential(
            offerUri = offerUri,
            issuerName = issuerName
        ) {
            credentials = it
        }

        val credential = credentials.first()

        presentCredential(
            presentationRequestUrl = presentationRequestUrl,
            credentialId = credential.id,
            verifierName = issuerName,
            walletDid = did
        )

    }

    private suspend fun claimCredential(
        offerUri: String,
        issuerName: String = "",
        pin: String? = null,
        walletDid: String = did,
        output: ((List<WalletCredential>) -> Unit)? = null
    ) = e2e.test(
        name = "EBSI Vector credential claim test case: Our wallet API with the credential issuer of $issuerName",
    ) {
        httpClient.post("/wallet-api/wallet/$wallet/exchange/useOfferRequest") {
            url {
                parameters.append("did", walletDid)
                pin?.let {
                    parameters.append("pinOrTxCode", pin)
                }
            }
            setBody(offerUri)
        }.expectSuccess().apply {
            val credentials = body<List<WalletCredential>>()
            output?.invoke(credentials)
        }
    }

    private suspend fun presentCredential(
        presentationRequestUrl: String,
        credentialId: String,
        verifierName: String = "",
        walletDid: String = did,
    ) = e2e.test(
        name = "EBSI Vector present credential test case: Our wallet API with the verifier of $verifierName",
    ) {
        lateinit var resolvedPresentationOfferString: String
        lateinit var presentationDefinition: String

        httpClient.post("/wallet-api/wallet/$wallet/exchange/resolvePresentationRequest") {
            contentType(ContentType.Text.Plain)
            setBody(presentationRequestUrl)
        }.expectSuccess().apply {
            resolvedPresentationOfferString = body<String>()
            presentationDefinition =
                Url(resolvedPresentationOfferString).parameters.getOrFail("presentation_definition")
        }

        httpClient.post("/wallet-api/wallet/$wallet/exchange/matchCredentialsForPresentationDefinition") {
            setBody(presentationDefinition)
        }.expectSuccess()

        val request = UsePresentationRequest(walletDid, resolvedPresentationOfferString, listOf(credentialId))
        httpClient.post("/wallet-api/wallet/$wallet/exchange/usePresentationRequest") {
            setBody(request)
        }.expectSuccess()

    }


    private suspend fun claimCredentialFromValidatedId() {
        val validatedIdIssuerBaseUrl = "https://labs-openid-interop.vididentity.net/api/issuance-pre-auth"

        val pin = "1234"

        val issuanceRequestPayload = """
        {
          "credentialTypeId": "33095f2f-6f80-4168-8301-abc815848aef",
          "issuerDid": "did:ebsi:zpD3Qp8h4psvdgnTGMX6hfE",
          "credentialSubject": {
            "name": "Bianca",
            "age": 30,
            "surname": "Castafiori"
          },
          "userPin": $pin
        }
    """.trimIndent()

        lateinit var offerUri: String

        httpClient.post(validatedIdIssuerBaseUrl) {
            setBody(issuanceRequestPayload)
        }.expectSuccess().body<JsonObject>().let {
            offerUri = it["rawCredentialOffer"]!!.jsonPrimitive.content
        }

        claimCredential(
            offerUri = offerUri,
            issuerName = "ValidatedID",
            pin = pin,
        )
    }

    private suspend fun claimCredentialFromValidatedIdDraft13() {

        val validatedIdIssuerBaseUrl = "https://labs-openid-interop.vididentity.net/api/issuance-pre-auth"

        val pin = "1234"

        val issuanceRequestPayload = """
            {
              "credentialTypeId": "33095f2f-6f80-4168-8301-abc815848aef",
              "issuerDid": "did:ebsi:zpD3Qp8h4psvdgnTGMX6hfE",
              "credentialSubject": {
                "name": "Bianca",
                "age": 30,
                "surname": "Castafiori"
              },
              "oid4vciVersion": "Draft13",
              "userPin": $pin
            }
        """.trimIndent()

        lateinit var offerUri: String

        httpClient.post(validatedIdIssuerBaseUrl) {
            setBody(issuanceRequestPayload)
        }.expectSuccess().body<JsonObject>().let {
            offerUri = it["rawCredentialOffer"]!!.jsonPrimitive.content
        }

        claimCredential(
            offerUri = offerUri,
            issuerName = "ValidatedID Draft13",
            pin = pin,
        )
    }

    private suspend fun claimCredentialFromTriveria() {

        val issuerWalletBaseUrl =
            "https://wallet.triveria.dev/api/wallets/17fd4d08-e87d-11ef-bdff-0a58a9feac02/eudi/credentialIssuanceRequest"

        lateinit var offerUri: String
        lateinit var pin: String

        httpClient.get(issuerWalletBaseUrl) {
            url {
                parameters.append("issuerId", "ebsi_ct_diplomademo")
                parameters.append("clientId", did)
                parameters.append("preauthOffer", "true")
                parameters.append("externalRequest", "true")
            }
        }.expectSuccess().body<JsonObject>().let {
            offerUri = it["offer"]!!.jsonPrimitive.content
            pin = it["pin"]!!.jsonPrimitive.content
        }

        claimCredential(
            offerUri = offerUri,
            issuerName = "Triveria",
            pin = pin,
        )
    }

    private suspend fun claimCredentialFromGoldman() {

        val useCaseKeyId = "vKpe0rC5k3EkFRIS4pDQWO56oK20KfLDpwU3Zd0C0Bg"
        val privateKey = """
            {"kty":"EC","d":"zKsPggT-dWrymXgvZTg19khBKo0Mj9rF7eDjCPuJOrk","crv":"P-256","kid":"vKpe0rC5k3EkFRIS4pDQWO56oK20KfLDpwU3Zd0C0Bg","x":"lVrg3EKCD2AFPeNz_RjEl12KZ4CweiPqpU7lBgfGN2Q","y":"roQpMQs_915oxyRZt2JlFYa-OP24cPCPUGkGNichi8k"}
        """.trimIndent()

        keysApi.importKey(
            wallet = wallet,
            privateKey
        )

        val useCaseDid =
            httpClient.post("/wallet-api/wallet/$wallet/dids/create/key?useJwkJcsPub=true&keyId=$useCaseKeyId")
                .expectSuccess().bodyAsText()

        val testIssuerBaseUrl =
            "https://testissuer.acgoldman.com:3018/v3/issuer/start-flow"

        val offerUri = httpClient.get(testIssuerBaseUrl) {
            url {
                parameters.append("reqtype", "ReqVC")
                parameters.append("redirect", "false")
                parameters.append("device", "DESKTOP")
                parameters.append("authtype", "PRE")
                parameters.append("issuancemode", "intime")
                parameters.append("walletDID", useCaseDid)
            }
        }.expectSuccess().bodyAsText().let {
            Json.decodeFromString<JsonObject>(Url(it).parameters["credential_offer_uri"]!!)["credential_offer_uri"]!!.jsonPrimitive.content.let {
                Url(url {
                    protocol = URLProtocol("openid-credential-offer", -1)
                    parameters.append("credential_offer_uri", it)

                }).toURI().toString().replace("localhost", "")
            }

        }

        claimCredential(
            offerUri = offerUri,
            issuerName = "Goldman",
            pin = "8152",
            walletDid = useCaseDid,
        )

    }

    private suspend fun claimCredentialFromGataca() {

        val issuerBaseUrl =
            "https://certify.dev.gataca.io/api/v3/tenants/gataca/oidc-issuance/testbed_PreAuthInTime/preauthorized"

        val tempClient = e2e.testHttpClient()

        val accessToken = tempClient.post("https://nucleus.dev.gataca.io/admin/v1/api_keys/login") {
            headers {
                append(
                    "Authorization",
                    "Basic ODljMjc2ODQtY2ExYi00YmI2LWFlYTktODVhNWQyYjYyZjJmOkE3RjlNQlcyck1mY2J2dGozNlNFMUozMWI3clJmY3FFcWN3cERiVGpqUTdR"
                )
            }
            setBody("{}")
        }.expectSuccess().headers["token"]!!

        val pin = "7164"

        val offerUri = tempClient.post(issuerBaseUrl) {
            setBody(buildJsonObject {
                put("pin_code", pin.toJsonElement())
                put("subject", did.toJsonElement())
            })
            headers {
                append(HttpHeaders.Authorization, "jwt $accessToken")
            }
        }.expectSuccess().body<JsonObject>().let {
            it["credential_offer_uri"]!!.jsonPrimitive.content
        }

        claimCredential(
            offerUri = offerUri,
            issuerName = "Gataca",
            pin = pin,
        )
    }

    private suspend fun claimCredentialFromDanubeTech() {

        val issuerBaseUrl =
            "https://oid4vci.uniissuer.io/1.0/v10/authorize/pre-authorize"

        val offerUri = httpClient.post(issuerBaseUrl) {
            setBody(
                Json.decodeFromString<JsonObject>(
                    """
                {
                  "claims": {
                    "type": "AchievementSubject",
                    "achievement": {
                      "type": "Achievement",
                      "name": "Universal Issuer issued Open Badge v3 credential",
                      "description": "Wallet can store and display Badge v3 credential",
                      "criteria": {
                        "type": "Criteria",
                        "narrative": "The first cohort of the JFF Plugfest 3 in Oct/Nov of 2023 collaborated to push interoperability of VCs in education forward."
                      },
                      "image": {
                        "id": "https://w3c-ccg.github.io/vc-ed/plugfest-2-2022/images/JFF-VC-EDU-PLUGFEST2-badge-image.png",
                        "type": "Image"
                      }
                    }
                  },
                  "schema": "OpenBadgeCredential_jwt_vc_json",
                  "format": "jwt_vc_json"
                }
            """.trimIndent()
                )
            )
        }.expectSuccess().bodyAsText()

        claimCredential(
            offerUri = offerUri,
            issuerName = "DanubeTech",
        )
    }

    private suspend fun claimCredentialFromCorpoSign() {

        val issuerBaseUrl =
            "https://ebsi-conformance.corposign.net/api/v1/conformance/initiate-credential-offer"

        val pin = "1234"

        val tempClient = e2e.testHttpClient()

        val offerUri = tempClient.submitForm(
            url = issuerBaseUrl,
            formParameters = Parameters.build {
                append("credential_type", "CTWalletSamePreAuthorisedInTime")
                append("client_id", did)
            }
        ).expectSuccess().body<JsonPrimitive>().content

        claimCredential(
            offerUri = offerUri,
            issuerName = "CorpoSign",
            pin = pin,
        )
    }

    private suspend fun claimCredentialFromBlockchainLabUM() {

        val issuerBaseUrl =
            "https://bclabum.informatika.uni-mb.si/interop-testing-frontend/api/credential-offer"

        val tempClient = e2e.testHttpClient()

        val offerUri = tempClient.post(issuerBaseUrl) {
            setBody(
                Json.decodeFromString<JsonObject>(
                    """
                {
                  "credentialType": "InTimeIssuance",
                  "client_id": "$did"
                }
            """.trimIndent()
                )
            )
        }.expectSuccess().bodyAsText().let {
            Json.decodeFromString<JsonObject>(it)["location"]!!.jsonPrimitive.content
        }

        claimCredential(
            offerUri = offerUri,
            issuerName = "BlockchainLabUM",
        )
    }

    private suspend fun claimCredentialFromCERTH() {

        val issuerBaseUrl =
            "https://api.interoperability-test.ebsi-dev.dlt.iti.gr/api/v1/credential-offer"

        lateinit var pin: String
        lateinit var offerUri: String

        val tempClient = e2e.testHttpClient()

        tempClient.post(issuerBaseUrl) {
            setBody(
                Json.decodeFromString<JsonObject>(
                    """
                {
                  "authorization": "pre-authorized",
                  "flow_type": "in_time",
                  "subject": "$did"
                }
            """.trimIndent()
                )
            )
        }.expectSuccess().body<JsonObject>().let {
            pin = it["pin"]!!.jsonPrimitive.content
            offerUri = it["credential_offer_uri"]!!.jsonPrimitive.content
        }

        claimCredential(
            offerUri = offerUri,
            issuerName = "CERTH/ITI DLT LAB",
            pin = pin,
        )
    }


    suspend fun runTest() {

        claimCredentialFromValidatedId()

        claimCredentialFromValidatedIdDraft13()

        claimCredentialFromTriveria()

        claimCredentialFromDanubeTech()

        claimCredential(
            offerUri = "openid-credential-offer://?credential_offer_uri=https%3A%2F%2Fidentfy.izer.tech%2Foffers%2F17116185-6ae0-4785-9f2e-29c772f9af16%3Frequested_vc_types%3DPreAuthIssuance%26pre-authorized_code%3Dabc",
            issuerName = "Izertis",
        )

        claimAndPresentCredentialIzertis()

//        claimCredentialFromCERTH()

        //Offline but we were compliant when tested
//        claimCredentialFromGoldman()

        //These guys encode an "authorization_code": null key-value pair in the credential offer. If this is removed, it works so partially interoperable
//        claimCredentialFromCorpoSign()

        //These guys are encoding the metadata of supported credentials incorrectly. Not gonna bother.
//        claimCredentialFromGataca()

        //Invalid credential offer, the grants map is incorrect. Not gonna bother.
//        claimCredentialFromBlockchainLabUM()
    }
}
