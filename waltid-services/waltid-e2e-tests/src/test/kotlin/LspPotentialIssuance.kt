import org.cose.java.AlgorithmID
import org.cose.java.OneKey
import cbor.Cbor
import com.nimbusds.jose.jwk.ECKey
import id.walt.commons.interop.LspPotentialInterop
import id.walt.commons.testing.E2ETest
import id.walt.crypto.keys.KeyGenerationRequest
import id.walt.crypto.keys.KeyManager
import id.walt.crypto.keys.KeyType
import id.walt.crypto.utils.Base64Utils.base64UrlDecode
import id.walt.crypto.utils.UuidUtils.randomUUIDString
import id.walt.mdoc.COSECryptoProviderKeyInfo
import id.walt.mdoc.SimpleCOSECryptoProvider
import id.walt.mdoc.cose.COSESign1
import id.walt.mdoc.dataelement.DEType
import id.walt.mdoc.dataelement.MapElement
import id.walt.mdoc.dataelement.MapKey
import id.walt.mdoc.dataelement.toDataElement
import id.walt.mdoc.doc.MDoc
import id.walt.mdoc.doc.MDocTypes
import id.walt.mdoc.doc.MDocVerificationParams
import id.walt.mdoc.doc.VerificationType
import id.walt.mdoc.docrequest.MDocRequestBuilder
import id.walt.mdoc.issuersigned.IssuerSigned
import id.walt.oid4vc.OpenID4VCI
import id.walt.oid4vc.data.*
import id.walt.oid4vc.requests.AuthorizationRequest
import id.walt.oid4vc.requests.CredentialRequest
import id.walt.oid4vc.requests.TokenRequest
import id.walt.oid4vc.responses.CredentialResponse
import id.walt.oid4vc.responses.TokenResponse
import id.walt.sdjwt.JWTVCIssuerMetadata
import id.walt.sdjwt.SDJwtVC
import id.walt.verifier.lspPotential.LspPotentialVerificationInterop
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.kotlincrypto.hash.sha2.SHA256
import java.io.FileReader
import java.security.KeyPairGenerator
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.*

class LspPotentialIssuance(private val e2e: E2ETest, val client: HttpClient) {

    @OptIn(ExperimentalEncodingApi::class, ExperimentalSerializationApi::class)
    suspend fun testTrack1() = e2e.test("test track 1") {
        // ### steps 1-6
        val offerResp = client.get("/lsp-potential/lspPotentialCredentialOfferT1")
        println("Offer resp: $offerResp")
        assert(offerResp.status == HttpStatusCode.OK)
        val offerUri = offerResp.bodyAsText()
        println("Offer uri: $offerUri")
        val parsedOffer = OpenID4VCI.parseAndResolveCredentialOfferRequestUrl(offerUri)
        println("parsedOffer: $parsedOffer")
        // ### get issuer metadata, steps 7-10
        val providerMetadataUri = OpenID4VCI.getCIProviderMetadataUrl(parsedOffer.credentialIssuer)
        val oauthMetadataUri = OpenID4VCI.getOAuthProviderMetadataUrl(parsedOffer.credentialIssuer)
        val providerMetadata = client.get(providerMetadataUri).bodyAsText().let { OpenIDProviderMetadata.fromJSONString(it) } as OpenIDProviderMetadata.Draft13
        val oauthMetadata = client.get(oauthMetadataUri).body<OpenIDProviderMetadata.Draft13>()
        assertNotNull(providerMetadata.credentialConfigurationsSupported)
        assertNotNull(providerMetadata.credentialEndpoint)
        assertNotNull(oauthMetadata.authorizationEndpoint)
        assertNotNull(oauthMetadata.tokenEndpoint)
        // resolve offered credentials
        val offeredCredentials = OpenID4VCI.resolveOfferedCredentials(parsedOffer, providerMetadata)
        println("offered credentials: $offeredCredentials")

        assert(offeredCredentials.isNotEmpty()) { "Offered credentials is empty" }

        val offeredCredential = offeredCredentials.first()
        assertEquals(CredentialFormat.mso_mdoc, offeredCredential.format)
        assertEquals(MDocTypes.ISO_MDL, offeredCredential.docType)

        // ### step 11: confirm issuance (nothing to do)

        // ### step 12-15: authorization
        val codeVerifier = randomUUIDString()

        val codeChallenge =
            codeVerifier.let { Base64.UrlSafe.encode(SHA256().digest(it.toByteArray(Charsets.UTF_8))).trimEnd('=') }

        val authReq = AuthorizationRequest(
            responseType = setOf(ResponseType.Code),
            clientId = "test-wallet",
            redirectUri = "https://test-wallet.org",
            scope = setOf("openid"),
            issuerState = parsedOffer.grants[GrantType.authorization_code.value]?.issuerState,
            authorizationDetails = offeredCredentials.map {
                AuthorizationDetails.fromOfferedCredential(
                    it,
                    providerMetadata.credentialIssuer
                )
            },
            codeChallenge = codeChallenge,
            codeChallengeMethod = "S256"
        )
        val location = if (oauthMetadata.requirePushedAuthorizationRequests != true) {
            val authResp = client.get(oauthMetadata.authorizationEndpoint!!) {
                authReq.toHttpParameters().forEach {
                    parameter(it.key, it.value.first())
                }
            }
            assertEquals(HttpStatusCode.Found, authResp.status)
            Url(authResp.headers[HttpHeaders.Location]!!)

        } else {
            // TODO: check PAR support, also check issuer of panasonic (https://mdlpilot.japaneast.cloudapp.azure.com:8017), which uses PAR and doesn't seem to work
            val parResp = client.submitForm(
                oauthMetadata.pushedAuthorizationRequestEndpoint!!, parametersOf(
                    authReq.toHttpParameters()
                )
            )
            val authResp = client.get(oauthMetadata.authorizationEndpoint!!) {
                parameter("request_uri", Json.parseToJsonElement(parResp.bodyAsText()).jsonObject["request_uri"]!!.jsonPrimitive.content)
            }
            assertEquals(HttpStatusCode.Found, authResp.status)
            Url(authResp.headers[HttpHeaders.Location]!!)
        }
        assertContains(location.parameters.names(), "code")
        // ### step 16-18: access token retrieval
        val tokenResp = client.submitForm(
            oauthMetadata.tokenEndpoint!!,
            parametersOf(
                TokenRequest.AuthorizationCode(
                    clientId = authReq.clientId,
                    codeVerifier = codeVerifier,
                    redirectUri = authReq.redirectUri,
                    code = location.parameters["code"]!!,
                ).toHttpParameters()
            )
        ).let { TokenResponse.fromJSON(it.body<JsonObject>()) }
        assertNotNull(tokenResp.accessToken)
        assertTrue(tokenResp.isSuccess)

        val deviceKeyId = "device-key"
        val deviceKeyPair: ECKey =
            runBlocking { ECKey.parseFromPEMEncodedObjects(FileReader("./src/test/resources/lsp/ec_device_key.pem").readText()).toECKey() }
        val certificateChain: String = FileReader("./src/test/resources/lsp/cert-device-potential.pem").readText()
        // ### steps 19-22: credential issuance

        repeat(3) { it ->
            // TODO: move COSE signing functionality to crypto lib?
            val coseKey = OneKey(deviceKeyPair.toECPublicKey(), null).AsCBOR().EncodeToBytes()
            val credReq = CredentialRequest.forOfferedCredential(
                offeredCredential, ProofOfPossession.CWTProofBuilder(
                    issuerUrl = parsedOffer.credentialIssuer, clientId = authReq.clientId, nonce = tokenResp.cNonce,
                    coseKey = when (it) { // test with COSE_Key header
                        0 -> coseKey
                        else -> null
                    },
                    x5Cert = when (it) { // test with single x509 certificate in x5chain header
                        1 -> certificateChain.encodeToByteArray()
                        else -> null
                    },
                    x5Chain = when (it) { // test with x509 certificate list in x5chain header
                        2 -> listOf(certificateChain.encodeToByteArray())
                        else -> null
                    }
                ).build(
                    SimpleCOSECryptoProvider(
                        listOf(
                            COSECryptoProviderKeyInfo(
                                deviceKeyId, AlgorithmID.ECDSA_256,
                                deviceKeyPair.toECPublicKey(), deviceKeyPair.toECPrivateKey()
                            )
                        )
                    ), deviceKeyId
                )
            )

            val cwt = Cbor.decodeFromByteArray(COSESign1.serializer(), credReq.proof!!.cwt!!.base64UrlDecode())
            assertNotNull(cwt.payload)
            val cwtPayload = Cbor.decodeFromByteArray<MapElement>(cwt.payload!!)
            assertEquals(DEType.textString, cwtPayload.value.get(MapKey(ProofOfPossession.CWTProofBuilder.LABEL_ISS))?.type)
            val cwtProtectedHeader = Cbor.decodeFromByteArray<MapElement>(cwt.protectedHeader)
            assertEquals(
                cwtProtectedHeader.value[MapKey(ProofOfPossession.CWTProofBuilder.HEADER_LABEL_ALG)]!!.internalValue,
                -7L
            )
            assertEquals(
                cwtProtectedHeader.value[MapKey(ProofOfPossession.CWTProofBuilder.HEADER_LABEL_CONTENT_TYPE)]!!.internalValue,
                "openid4vci-proof+cwt"
            )

            val credResp = client.post(providerMetadata.credentialEndpoint!!) {
                contentType(ContentType.Application.Json)
                bearerAuth(tokenResp.accessToken!!)
                setBody(credReq.toJSON())
            }.body<JsonObject>().let { CredentialResponse.fromJSON(it) }
            assertTrue(credResp.isSuccess)
            assertContains(credResp.customParameters!!.keys, "credential_encoding")
            assertEquals("issuer-signed", credResp.customParameters!!["credential_encoding"]!!.jsonPrimitive.content)
            assertNotNull(credResp.credential)
            val mdoc = MDoc(
                credReq.docType!!.toDataElement(), IssuerSigned.fromMapElement(
                    Cbor.decodeFromByteArray(credResp.credential!!.jsonPrimitive.content.base64UrlDecode())
                ), null
            )
            assertEquals(credReq.docType, mdoc.docType.value)
            assertNotNull(mdoc.issuerSigned)
            assertTrue(
                mdoc.verifySignature(
                    SimpleCOSECryptoProvider(
                        listOf(
                            LspPotentialVerificationInterop.POTENTIAL_ISSUER_CRYPTO_PROVIDER_INFO
                        )
                    ), LspPotentialInterop.POTENTIAL_ISSUER_KEY_ID
                )
            )
            assertNotNull(mdoc.issuerSigned.nameSpaces)
            assertNotEquals(0, mdoc.issuerSigned.nameSpaces!!.size)
            assertNotNull(mdoc.issuerSigned.issuerAuth?.x5Chain)
            assertTrue(
                mdoc.verify(
                    MDocVerificationParams(
                        VerificationType.forIssuance, LspPotentialInterop.POTENTIAL_ISSUER_KEY_ID, deviceKeyId,
                        mDocRequest = MDocRequestBuilder(offeredCredential.docType!!).build()
                    ), SimpleCOSECryptoProvider(listOf(LspPotentialVerificationInterop.POTENTIAL_ISSUER_CRYPTO_PROVIDER_INFO))
                )
            )
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    suspend fun testTrack2() = e2e.test("test track 2") {
        // ### steps 1-6
        val offerResp = client.get("/lsp-potential/lspPotentialCredentialOfferT2")
        assertEquals(HttpStatusCode.OK, offerResp.status)

        val offerUri = offerResp.bodyAsText()

        // -------- WALLET ----------
        // as WALLET: receive credential offer, either being called via deeplink or by scanning QR code
        // parse credential URI
        val parsedOffer = OpenID4VCI.parseAndResolveCredentialOfferRequestUrl(offerUri)
        //assertContains(parsedOffer.credentialConfigurationIds, "potential.light.profile")

        // ### get issuer metadata, steps 7-10
        val providerMetadataUri = OpenID4VCI.getCIProviderMetadataUrl(parsedOffer.credentialIssuer)
        val jwtIssuerMetadataUri = OpenID4VCI.getJWTVCIssuerProviderMetadataUrl(parsedOffer.credentialIssuer)
        val oAuthMetadataUri = OpenID4VCI.getOAuthProviderMetadataUrl(parsedOffer.credentialIssuer)
        val providerMetadata = client.get(providerMetadataUri).bodyAsText().let { OpenIDProviderMetadata.fromJSONString(it) } as OpenIDProviderMetadata.Draft13
        val oauthMetadata = client.get(oAuthMetadataUri).body<OpenIDProviderMetadata.Draft13>()
        val jwtIssuerMetadata = client.get(jwtIssuerMetadataUri).body<JWTVCIssuerMetadata>()
        assertNotNull(providerMetadata.credentialConfigurationsSupported)
        assertNotNull(providerMetadata.credentialEndpoint)
        assertNotNull(jwtIssuerMetadata.issuer)
        assertNotNull(jwtIssuerMetadata.jwksUri)
        assertContains(providerMetadata.codeChallengeMethodsSupported.orEmpty(), "S256")

        val jwks = client.get(jwtIssuerMetadata.jwksUri!!).body<JsonObject>()
        assertContains(jwks.keys, "keys")

        // resolve offered credentials
        val offeredCredentials = OpenID4VCI.resolveOfferedCredentials(parsedOffer, providerMetadata)
        println("Offered credentials: $offeredCredentials")
        val offeredCredential = offeredCredentials.first()
        assertEquals(CredentialFormat.sd_jwt_vc, offeredCredential.format)
        assertEquals("${e2e.getBaseURL()}/identity_credential", offeredCredential.vct)

        // ### step 11: confirm issuance (nothing to do)

        // ### step 12-15: authorization
        val codeVerifier = randomUUIDString()

        val codeChallenge =
            codeVerifier.let { Base64.UrlSafe.encode(SHA256().digest(it.toByteArray(Charsets.UTF_8))).trimEnd('=') }

        val authReq = AuthorizationRequest(
            responseType = setOf(ResponseType.Code),
            clientId = "test-wallet",
            redirectUri = "https://test-wallet.org",
            scope = setOf("openid"),
            issuerState = parsedOffer.grants[GrantType.authorization_code.value]?.issuerState,
            authorizationDetails = offeredCredentials.map {
                AuthorizationDetails.fromOfferedCredential(
                    it,
                    providerMetadata.credentialIssuer
                )
            },
            codeChallenge = codeChallenge,
            codeChallengeMethod = "S256"
        )
        val location = if (oauthMetadata.requirePushedAuthorizationRequests != true) {
            val authResp = client.get(oauthMetadata.authorizationEndpoint!!) {
                authReq.toHttpParameters().forEach {
                    parameter(it.key, it.value.first())
                }
            }
            assertEquals(HttpStatusCode.Found, authResp.status)
            Url(authResp.headers[HttpHeaders.Location]!!)

        } else {
            // TODO: check PAR support, also check issuer of panasonic (https://mdlpilot.japaneast.cloudapp.azure.com:8017), which uses PAR and doesn't seem to work
            val parResp = client.submitForm(
                oauthMetadata.pushedAuthorizationRequestEndpoint!!, parametersOf(
                    authReq.toHttpParameters()
                )
            )
            val authResp = client.get(oauthMetadata.authorizationEndpoint!!) {
                parameter("request_uri", Json.parseToJsonElement(parResp.bodyAsText()).jsonObject["request_uri"]!!.jsonPrimitive.content)
            }
            assertEquals(HttpStatusCode.Found, authResp.status)
            Url(authResp.headers[HttpHeaders.Location]!!)
        }
        assertContains(location.parameters.names(), "code")
        // ### step 16-18: access token retrieval
        val tokenResp = client.submitForm(
            oauthMetadata.tokenEndpoint!!,
            parametersOf(
                TokenRequest.AuthorizationCode(
                    clientId = authReq.clientId,
                    codeVerifier = codeVerifier,
                    redirectUri = authReq.redirectUri,
                    code = location.parameters["code"]!!,
                ).toHttpParameters()
            )
        ).let { TokenResponse.fromJSON(it.body<JsonObject>()) }
        assertNotNull(tokenResp.accessToken)
        assertTrue(tokenResp.isSuccess)

        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(256)
        val deviceKeyPair = KeyManager.createKey(KeyGenerationRequest(keyType = KeyType.secp256r1))
        // ### steps 19-22: credential issuance

        // TODO: move COSE signing functionality to crypto lib?
        val credReq = CredentialRequest.forOfferedCredential(
            offeredCredential, ProofOfPossession.JWTProofBuilder(
                issuerUrl = parsedOffer.credentialIssuer, clientId = authReq.clientId, nonce = tokenResp.cNonce, keyId = null,
                keyJwk = deviceKeyPair.getPublicKey().exportJWKObject()
            ).build(deviceKeyPair)
        )

        val credResp = client.post(providerMetadata.credentialEndpoint!!) {
            contentType(ContentType.Application.Json)
            bearerAuth(tokenResp.accessToken!!)
            setBody(credReq.toJSON())
        }.body<JsonObject>().let { CredentialResponse.fromJSON(it) }
        println("Credential response: $credResp")

        assertTrue(credResp.isSuccess)
        assertNotNull(credResp.credential)
        val sdJwtVc = SDJwtVC.parse(credResp.credential!!.jsonPrimitive.content)
        println("sdJwtVc: $sdJwtVc")

        assertNotNull(sdJwtVc.cnfObject)
        assertEquals("vc+sd-jwt", sdJwtVc.type)
        // family_name is defined as non-selective disclosable in issuance request
        assertContains(sdJwtVc.undisclosedPayload.keys, "family_name")
        // birthdate is defined as selective disclosable in issuance request
        assertFalse(sdJwtVc.undisclosedPayload.keys.contains("birthdate"))
        assertContains(sdJwtVc.disclosureObjects.map { it.key }, "birthdate")
        assertContains(sdJwtVc.fullPayload.keys, "birthdate")
    }
}
