package id.walt.wallet2.mobile

import id.walt.certificate.x509.X509CertificateUtil
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.EcdsaSignatureEncoding
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.toPublicJwk
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.openid4vci.proofs.ProofType
import id.walt.wallet2.handlers.WalletIssuanceOutcome
import id.walt.wallet2.stores.inmemory.InMemoryCredentialStore
import id.walt.wallet2.stores.inmemory.InMemoryDidStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlin.io.encoding.Base64
import kotlin.time.Clock
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KeyOnlyProviderIssuanceTest {
    private val runtime = CryptoRuntime(defaultSoftwareKeyProviders())

    @Test
    fun `a key-only provider completes anonymous issuance without requesting a wallet instance attestation`() = runTest {
        val holder = key("holder")
        val trace = Trace()
        val wallet = wallet(holder, trace, anonymous = true, keyAttestation = true)
        val session = wallet.startIssuance(MobileWalletIssuanceRequest(
            offer = MobileWalletCredentialOffer.InlineJson(OFFER),
            clientId = "wallet-client",
            keyId = holder.id.value,
        ))
        val outcome = wallet.continuePreAuthorizedIssuance(session.id)
        assertIs<WalletIssuanceOutcome.Stored>(outcome, outcome.toString())
        assertEquals(1, wallet.credentials().size)
        assertTrue(trace.providerBodies.none { "\"wia\"" in it || "\"need\":\"wia\"" in it || "\"mint\":\"wia\"" in it }, trace.providerBodies.toString())
        assertTrue(trace.providerBodies.any { "\"composition\":\"ka\"" in it })
        assertTrue(trace.tokenCalls == 1)
        assertNull(trace.tokenAttestation)
        assertTrue(trace.proofs.single().let { CompactJws.decodeUnverified(it).protectedHeader.containsKey("key_attestation") })
    }

    @Test
    fun `a key-only provider fails a required wallet instance attestation before the token request`() = runTest {
        val holder = key("holder")
        val trace = Trace()
        val wallet = wallet(holder, trace, anonymous = false, keyAttestation = false)
        val session = wallet.startIssuance(MobileWalletIssuanceRequest(
            offer = MobileWalletCredentialOffer.InlineJson(OFFER),
            clientId = "wallet-client",
            keyId = holder.id.value,
        ))
        val outcome = wallet.continuePreAuthorizedIssuance(session.id)
        assertIs<WalletIssuanceOutcome.Failed>(outcome, outcome.toString())
        assertTrue(outcome.storedCredentialIds.isEmpty())
        assertTrue(wallet.credentials().isEmpty())
        assertEquals(1, trace.tokenCalls)
        assertNull(trace.tokenAttestation)
        assertTrue(trace.providerBodies.isEmpty(), trace.providerBodies.toString())
    }

    private suspend fun wallet(
        holder: id.walt.crypto2.keys.Key,
        trace: Trace,
        anonymous: Boolean,
        keyAttestation: Boolean,
    ): MobileWallet {
        val keys = InMemoryMobileWalletKeyStore().apply { addCrypto2Key(holder) }
        val attester = key("attester")
        val certificate = X509CertificateUtil.createSelfSignedCertificate(
            attester,
            SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256, EcdsaSignatureEncoding.DER),
        ) { subjectDn = "CN=Key only provider" }
        val x5c = Base64.encode(certificate.encodedDer.toByteArray())
        val attesterJwk = Json.parseToJsonElement(
            attester.capabilities.publicKeyExporter!!.exportPublicKey().toPublicJwk(attester.spec).data.toByteArray().decodeToString()
        )
        return MobileWallet(
            walletId = "key-only",
            keyStore = keys,
            didStore = InMemoryDidStore(),
            credentialStore = InMemoryCredentialStore(),
            walletProviderConfig = WalletProviderConfig(
                baseUrl = PROVIDER,
                servicePath = "org.demo.wallet-provider",
                bearerToken = "provider-token",
                composition = "ka",
            ),
            issuanceHttpClient = client(trace, anonymous, keyAttestation, attester, attesterJwk, x5c, holder),
        )
    }

    private fun client(
        trace: Trace,
        anonymous: Boolean,
        keyAttestation: Boolean,
        attester: id.walt.crypto2.keys.Key,
        attesterJwk: kotlinx.serialization.json.JsonElement,
        x5c: String,
        holder: id.walt.crypto2.keys.Key,
    ) = HttpClient(MockEngine) {
        engine { addHandler { request ->
            val url = request.url.toString()
            val body = request.bodyText()
            val (status, payload) = when {
                url == "$ISSUER/.well-known/openid-credential-issuer" -> HttpStatusCode.OK to issuerMetadata(keyAttestation)
                url == "$ISSUER/.well-known/oauth-authorization-server" ||
                    url == "$ISSUER/.well-known/openid-configuration" -> HttpStatusCode.OK to authorizationServer(anonymous)
                url == "$ISSUER/token" -> {
                    trace.tokenCalls += 1
                    trace.tokenAttestation = request.headers["OAuth-Client-Attestation"]
                    if (!anonymous && trace.tokenAttestation == null) {
                        HttpStatusCode.Unauthorized to """{"error":"invalid_client"}"""
                    } else {
                        HttpStatusCode.OK to """{"access_token":"access","token_type":"Bearer"}"""
                    }
                }
                url == "$ISSUER/nonce" -> HttpStatusCode.OK to """{"c_nonce":"issuer-nonce"}"""
                url == "$ISSUER/credential" -> {
                    val proof = Json.parseToJsonElement(body).jsonObject.getValue("proofs")
                        .jsonObject.getValue(ProofType.JWT.value).jsonArray.single().jsonPrimitive.content
                    trace.proofs += proof
                    HttpStatusCode.OK to """{"credentials":[{"credential":${Json.encodeToString(credential(holder))}}]}"""
                }
                url.startsWith("$PROVIDER/v2/org.demo.wallet-provider/wallet-provider-api/") -> {
                    trace.providerBodies += body
                    HttpStatusCode.OK to providerResponse(url, body, attester, attesterJwk, x5c)
                }
                else -> error("Unexpected request $url")
            }
            respond(payload, status, headersOf(HttpHeaders.ContentType, "application/json"))
        } }
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    private suspend fun providerResponse(
        url: String,
        body: String,
        attester: id.walt.crypto2.keys.Key,
        attesterJwk: kotlinx.serialization.json.JsonElement,
        x5c: String,
    ): String {
        if (url.endsWith("/units")) return """{"unit_id":"wu-1","state":"operational"}"""
        val request = Json.parseToJsonElement(body).jsonObject
        val attested = request["attested_keys"]!!.jsonArray.single()
        val now = Clock.System.now().epochSeconds
        val jwt = CompactJws.sign(
            payload = buildJsonObject {
                put("iat", now)
                put("exp", now + 300)
                request["nonce"]?.jsonPrimitive?.content?.let { put("nonce", it) }
                put("attested_keys", buildJsonArray { add(attested) })
            }.toString().encodeToByteArray(),
            key = attester,
            algorithm = JwsAlgorithm.ES256,
            protectedHeader = buildJsonObject {
                put("typ", "key-attestation+jwt")
                put("jwk", attesterJwk)
                put("x5c", buildJsonArray { add(JsonPrimitive(x5c)) })
            },
        )
        return """{"unit_id":"wu-1","state":"operational","ka":${Json.encodeToString(jwt)}}"""
    }

    private suspend fun credential(holder: id.walt.crypto2.keys.Key): String {
        val holderJwk = holder.capabilities.publicKeyExporter!!.exportPublicKey().toPublicJwk(holder.spec)
            .data.toByteArray().decodeToString()
        return CompactJws.sign(
            payload = """{"iss":"$ISSUER","sub":"holder","vc":{"@context":["https://www.w3.org/2018/credentials/v1"],"type":["VerifiableCredential","TestCredential"],"credentialSubject":{"id":"holder"},"cnf":{"jwk":$holderJwk}}}"""
                .encodeToByteArray(),
            key = holder,
            algorithm = JwsAlgorithm.ES256,
        )
    }

    private suspend fun key(id: String) = runtime.generateSoftwareKey(
        GenerateSoftwareKeyRequest(KeyId(id), KeySpec.Ec(EcCurve.P256), setOf(KeyUsage.SIGN, KeyUsage.VERIFY))
    )

    private fun issuerMetadata(keyAttestation: Boolean): String {
        val proof = if (!keyAttestation) "" else """,
          "cryptographic_binding_methods_supported":["jwk"],
          "proof_types_supported":{"jwt":{"proof_signing_alg_values_supported":["ES256"],"key_attestations_required":{}}}"""
        return """
            {"credential_issuer":"$ISSUER","credential_endpoint":"$ISSUER/credential","nonce_endpoint":"$ISSUER/nonce",
             "credential_configurations_supported":{"test-credential":{"format":"jwt_vc_json",
               "credential_definition":{"type":["VerifiableCredential","TestCredential"]}$proof}}}
        """.trimIndent()
    }

    private fun authorizationServer(anonymous: Boolean) = """
        {"issuer":"$ISSUER","token_endpoint":"$ISSUER/token","response_types_supported":["code"],
         "authorization_details_types_supported":["openid_credential"],
         "grant_types_supported":["urn:ietf:params:oauth:grant-type:pre-authorized_code"],
         "token_endpoint_auth_methods_supported":["attest_jwt_client_auth"],
         "client_attestation_signing_alg_values_supported":["ES256"],
         "client_attestation_pop_signing_alg_values_supported":["ES256"]
         ${if (anonymous) ""","pre-authorized_grant_anonymous_access_supported":true""" else ""}}
    """.trimIndent()

    private class Trace {
        val providerBodies = mutableListOf<String>()
        val proofs = mutableListOf<String>()
        var tokenCalls = 0
        var tokenAttestation: String? = null
    }

    private companion object {
        const val ISSUER = "https://issuer.example"
        const val PROVIDER = "https://provider.example"
        const val OFFER = """{"credential_issuer":"$ISSUER","credential_configuration_ids":["test-credential"],"grants":{"urn:ietf:params:oauth:grant-type:pre-authorized_code":{"pre-authorized_code":"pre-code"}}}"""
    }
}

private fun HttpRequestData.bodyText(): String = when (val content = body) {
    is TextContent -> content.text
    is OutgoingContent.ByteArrayContent -> content.bytes().decodeToString()
    else -> ""
}
