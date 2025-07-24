@file:OptIn(ExperimentalUuidApi::class)

import id.walt.commons.interop.LspPotentialInterop
import id.walt.commons.testing.E2ETest
import id.walt.w3c.utils.VCFormat
import id.walt.crypto.keys.KeyGenerationRequest
import id.walt.crypto.keys.KeySerialization
import id.walt.crypto.keys.KeyType
import id.walt.issuer.issuance.IssuanceRequest
import id.walt.issuer.feat.lspPotential.LspPotentialIssuanceInterop
import id.walt.oid4vc.data.*
import id.walt.oid4vc.requests.AuthorizationRequest
import id.walt.sdjwt.SDField
import id.walt.sdjwt.SDJwtVC
import id.walt.sdjwt.SDMap
import id.walt.verifier.oidc.RequestedCredential
import id.walt.webwallet.db.models.WalletCredential
import id.walt.webwallet.web.controllers.exchange.UsePresentationRequest
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class LspPotentialWallet(val e2e: E2ETest, val client: HttpClient, val walletId: String) {
    private var issuedMdocId: String = ""
    private var issuedSDJwtVCId: String = ""
    private lateinit var generatedKeyId: String
    private lateinit var generatedDid: String

    init {
        // === create EC256 key and DID:JWK (did is not necessarily required, but currently needed for wallet initialization) ===
        val keysApi = KeysApi(e2e, client)

        runBlocking {
            keysApi.generate(Uuid.parse(walletId), KeyGenerationRequest(keyType = KeyType.secp256r1)) {
                generatedKeyId = it
            }
            DidsApi(e2e, client).create(Uuid.parse(walletId), DidsApi.DidCreateRequest("jwk", keyId = generatedKeyId)) {
                generatedDid = it
            }
        }
    }

    suspend fun testMDocIssuance(issuanceRequestData: String, useForPresentation: Boolean) = e2e.test("test mdoc issuance") {
        // === get credential offer from test issuer API ===
        val issuanceReq = Json.decodeFromString<IssuanceRequest>(issuanceRequestData).copy(
            authenticationMethod = AuthenticationMethod.PRE_AUTHORIZED
        )
        val offerResp = client.post("/openid4vc/mdoc/issue") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToJsonElement(issuanceReq).toString())
        }
        assert(offerResp.status == HttpStatusCode.OK)
        val offerUri = offerResp.bodyAsText()

        // === resolve credential offer ===
        val resolvedOffer11 = client.post("/wallet-api/wallet/$walletId/exchange/resolveCredentialOffer") {
            setBody(offerUri)
        }.expectSuccess().body<JsonObject>()

        println(resolvedOffer11)

        val resolvedOffer = client.post("/wallet-api/wallet/$walletId/exchange/resolveCredentialOffer") {
            setBody(offerUri)
        }.expectSuccess().body<CredentialOffer.Draft13>()

        assertEquals(1, resolvedOffer.credentialConfigurationIds.size)
        assertEquals(issuanceReq.credentialConfigurationId, resolvedOffer.credentialConfigurationIds.first().jsonPrimitive.content)

        // === resolve issuer metadata ===
        val issuerMetadata =
            client.get("${resolvedOffer.credentialIssuer}/.well-known/openid-credential-issuer").expectSuccess()
                .body<OpenIDProviderMetadata.Draft13>()
        assertEquals(issuerMetadata.issuer, resolvedOffer.credentialIssuer)
        assertContains(
            issuerMetadata.credentialConfigurationsSupported!!.keys,
            resolvedOffer.credentialConfigurationIds.first().jsonPrimitive.content,
        )

        // === use credential offer request ===
        val issuedCred = client.post("/wallet-api/wallet/$walletId/exchange/useOfferRequest?did=$generatedDid") {
            setBody(offerUri)
        }.expectSuccess().body<List<WalletCredential>>().first()

        assertEquals(CredentialFormat.mso_mdoc, issuedCred.format)

        // === get issued credential from wallet-api
        val fetchedCredential = client.get("/wallet-api/wallet/$walletId/credentials/${issuedCred.id}")
            .expectSuccess().body<WalletCredential>()
        assertEquals(issuedCred.format, fetchedCredential.format)
        if(useForPresentation)
            runBlocking { issuedMdocId = fetchedCredential.id }
    }

    suspend fun testMdocPresentation() = e2e.test("test mdoc presentation") {
        val createReqResponse = client.post("/openid4vc/verify") {
            header("authorizeBaseUrl", "mdoc-openid4vp://")
            header("responseMode", "direct_post_jwt")
            contentType(ContentType.Application.Json)
            setBody(
                buildJsonObject {
                    put(
                        "request_credentials",
                        JsonArray(
                            listOf(
                                RequestedCredential(
                                    format = VCFormat.mso_mdoc,
                                    docType = "org.iso.18013.5.1.mDL"
                                ).let { Json.encodeToJsonElement(it) })
                        )
                    )
                    put(
                        "trusted_root_cas",
                        JsonArray(listOf(JsonPrimitive(LspPotentialInterop.POTENTIAL_ROOT_CA_CERT)))
                    )
                })
        }
        assertEquals(200, createReqResponse.status.value)
        val presReqUrl = createReqResponse.bodyAsText()

        // === resolve presentation request ===
        val parsedRequest = client.post("/wallet-api/wallet/$walletId/exchange/resolvePresentationRequest") {
            setBody(presReqUrl)
        }.expectSuccess().let { response ->
            response.body<String>()
                .let { AuthorizationRequest.fromHttpParameters(parseQueryString(Url(it).encodedQuery).toMap()) }
        }
        assertNotNull(parsedRequest.presentationDefinition)

        // === find matching credential ===
        val matchingCreds =
            client.post("/wallet-api/wallet/$walletId/exchange/matchCredentialsForPresentationDefinition") {
                setBody(parsedRequest.presentationDefinition!!.toJSON())
            }.expectSuccess().body<List<WalletCredential>>()
        assertNotEquals(0, matchingCreds.size)

        client.post("/wallet-api/wallet/$walletId/exchange/usePresentationRequest") {
            setBody(UsePresentationRequest(generatedDid, presReqUrl, listOf(issuedMdocId)))
        }.expectSuccess()
    }

    suspend fun testSDJwtVCIssuance() = testSDJwtVCIssuance(
        IssuanceRequest(
            Json.parseToJsonElement(KeySerialization.serializeKey(LspPotentialIssuanceInterop.POTENTIAL_ISSUER_JWK_KEY)).jsonObject,
            "identity_credential_vc+sd-jwt",
            credentialData = buildJsonObject {
                put("family_name", "Doe")
                put("given_name", "John")
                put("birthdate", "1940-01-01")
            },
            "identity_credential",
            x5Chain = listOf(LspPotentialInterop.POTENTIAL_ISSUER_CERT),
            trustedRootCAs = listOf(LspPotentialInterop.POTENTIAL_ROOT_CA_CERT),
            selectiveDisclosure = SDMap(
                mapOf(
                    "birthdate" to SDField(sd = true)
                )
            ),
            mapping = Json.parseToJsonElement(
                """
              {
                "id": "<uuid>",
                "iat": "<timestamp-seconds>",
                "nbf": "<timestamp-seconds>",
                "exp": "<timestamp-in-seconds:365d>"
              }
            """.trimIndent()
            ).jsonObject
        )
    )

    suspend fun testSDJwtVCIssuanceByIssuerDid() = testSDJwtVCIssuance(
        IssuanceRequest(
            Json.parseToJsonElement(KeySerialization.serializeKey(LspPotentialIssuanceInterop.POTENTIAL_ISSUER_JWK_KEY)).jsonObject,
            "identity_credential_vc+sd-jwt",
            credentialData = buildJsonObject {
                put("family_name", "Doe")
                put("given_name", "John")
                put("birthdate", "1940-01-01")
            },
            mdocData = null,
            selectiveDisclosure = SDMap(
                mapOf(
                    "birthdate" to SDField(sd = true)
                )
            ),
            mapping = Json.parseToJsonElement(
                """
              {
                "id": "<uuid>",
                "iat": "<timestamp-seconds>",
                "nbf": "<timestamp-seconds>",
                "exp": "<timestamp-in-seconds:365d>"
              }
            """.trimIndent()
            ).jsonObject,
            issuerDid = LspPotentialIssuanceInterop.ISSUER_DID
        )
    )

    suspend fun testSDJwtVCIssuance(issuanceReq: IssuanceRequest) = e2e.test("test sd-jwt-vc issuance") {
        // === get credential offer from test issuer API ===
        val offerResp = client.post("/openid4vc/sdjwt/issue") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToJsonElement(issuanceReq).toString())
        }
        assert(offerResp.status == HttpStatusCode.OK)
        val offerUri = offerResp.bodyAsText()

        // === resolve credential offer ===
        val resolvedOffer = client.post("/wallet-api/wallet/$walletId/exchange/resolveCredentialOffer") {
            setBody(offerUri)
        }.expectSuccess().body<CredentialOffer.Draft13>()
        assertEquals(1, resolvedOffer.credentialConfigurationIds.size)
        assertEquals("identity_credential_vc+sd-jwt", resolvedOffer.credentialConfigurationIds.first().jsonPrimitive.content)

        // === resolve issuer metadata ===
        val issuerMetadata =
            client.get("${resolvedOffer.credentialIssuer}/.well-known/openid-credential-issuer").expectSuccess()
                .body<OpenIDProviderMetadata.Draft13>()
        assertEquals(issuerMetadata.issuer, resolvedOffer.credentialIssuer)
        assertContains(
            issuerMetadata.credentialConfigurationsSupported!!.keys,
            resolvedOffer.credentialConfigurationIds.first().jsonPrimitive.content,
        )

        // === use credential offer request ===
        val issuedCred = client.post("/wallet-api/wallet/$walletId/exchange/useOfferRequest?did=$generatedDid") {
            setBody(offerUri)
        }.expectSuccess().body<List<WalletCredential>>().first()

        assertEquals(CredentialFormat.sd_jwt_vc, issuedCred.format)

        // === get issued credential from wallet-api
        val fetchedCredential = client.get("/wallet-api/wallet/$walletId/credentials/${issuedCred.id}")
            .expectSuccess().body<WalletCredential>()
        assertEquals(issuedCred.format, fetchedCredential.format)
        runBlocking { issuedSDJwtVCId = fetchedCredential.id }
        val sdJwtVC = SDJwtVC.parse("${fetchedCredential.document}~${fetchedCredential.disclosures}")
        assert(sdJwtVC.disclosures.isNotEmpty())
        assert(sdJwtVC.sdMap["birthdate"]!!.sd)
        val id = sdJwtVC.undisclosedPayload["id"]?.jsonPrimitive?.contentOrNull ?: ""
        val iat = sdJwtVC.undisclosedPayload["iat"]?.jsonPrimitive?.longOrNull ?: 0L
        val nbf = sdJwtVC.undisclosedPayload["nbf"]?.jsonPrimitive?.longOrNull ?: 0L
        val exp = sdJwtVC.undisclosedPayload["exp"]?.jsonPrimitive?.longOrNull ?: 0L
        assert(iat > 0)
        assert(iat == nbf)
        assert(exp == iat + 365 * 24 * 60 * 60)
        assert(id.startsWith("urn:uuid:"))

    }

    suspend fun testSDJwtPresentation(openIdProfile: OpenId4VPProfile = OpenId4VPProfile.HAIP) =
        e2e.test("test sd-jwt-vc presentation") {
            val createReqResponse = client.post("/openid4vc/verify") {
                header("authorizeBaseUrl", "openid4vp://")
                header("openId4VPProfile", openIdProfile.name)
                header("responseMode", "direct_post")
                contentType(ContentType.Application.Json)
                setBody(
                    buildJsonObject {
                        put(
                            "request_credentials",
                            JsonArray(
                                listOf(
                                    RequestedCredential(
                                        format = VCFormat.sd_jwt_vc,
                                        vct = "${e2e.getBaseURL()}/identity_credential",
                                    ).let {
                                        Json.encodeToJsonElement(it)
                                    })
                            )
                        )
                    })
            }
            assertEquals(200, createReqResponse.status.value)
            val presReqUrl = createReqResponse.bodyAsText()

            // === resolve presentation request ===
            val parsedRequest = client.post("/wallet-api/wallet/$walletId/exchange/resolvePresentationRequest") {
                setBody(presReqUrl)
            }.expectSuccess().let { response ->
                response.body<String>()
                    .let { AuthorizationRequest.fromHttpParameters(parseQueryString(Url(it).encodedQuery).toMap()) }
            }
            assertNotNull(parsedRequest.presentationDefinition)

            // === find matching credential ===
            val matchingCreds =
                client.post("/wallet-api/wallet/$walletId/exchange/matchCredentialsForPresentationDefinition") {
                    setBody(parsedRequest.presentationDefinition!!.toJSON())
                }.expectSuccess().body<List<WalletCredential>>()
            assertNotEquals(0, matchingCreds.size)

            client.post("/wallet-api/wallet/$walletId/exchange/usePresentationRequest") {
                setBody(UsePresentationRequest(generatedDid, presReqUrl, listOf(issuedSDJwtVCId)))
            }.expectSuccess()
        }

    suspend fun testPresentationDefinitionCredentialMatching() = e2e.test("test presentation definition matching") {
//        val presentationDefinition: String = """
//    {"id":"tovqUq4ddXYC","input_descriptors":[{"id":"IdIsRequired","constraints":{"fields":[{"path":["${'$'}.type"],"filter":{"type":"string","pattern":"BankId"}},{"path":["${'$'}.credentialSubject.type"],"filter":{"type":"string","pattern":".*"}}],"limit_disclosure":"required"}}]}
//        """.trimIndent()
        val presentationDefinition: String = """
    {"id":"tovqUq4ddXYC","input_descriptors":[{"id":"IdIsRequired","constraints":{"fields":[{"path":["${'$'}.vct"],"filter":{"type":"string","pattern":".*/identity_credential"}}],"limit_disclosure":"required"}}]}
        """.trimIndent()
        // === find matching credential ===
        val matchingCreds =
            client.post("/wallet-api/wallet/$walletId/exchange/matchCredentialsForPresentationDefinition") {
                setBody(presentationDefinition)
            }.expectSuccess().body<List<WalletCredential>>()
        assertNotEquals(0, matchingCreds.size)
    }
}
