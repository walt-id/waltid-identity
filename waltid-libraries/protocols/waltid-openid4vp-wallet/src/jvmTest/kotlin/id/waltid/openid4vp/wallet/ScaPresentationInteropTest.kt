package id.waltid.openid4vp.wallet

import com.nimbusds.jose.*
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.crypto.ECDSAVerifier
import com.nimbusds.jose.crypto.ECDHDecrypter
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import id.walt.credentials.CredentialDetectorTypes
import id.walt.credentials.formats.SdJwtCredential
import id.walt.credentials.signatures.sdjwt.SdJwtSelectiveDisclosure
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto2.keys.Signer
import id.walt.dcql.DcqlMatcher
import id.walt.dcql.RawDcqlCredential
import id.walt.dcql.models.CredentialFormat
import id.walt.dcql.models.CredentialQuery
import id.walt.dcql.models.DcqlQuery
import id.walt.dcql.models.meta.SdJwtVcMeta
import id.walt.verifier.openid.models.authorization.ClientMetadata
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.verifier.openid.models.openid.OpenID4VPResponseMode
import id.waltid.openid4vp.wallet.presentation.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import java.io.File
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.util.concurrent.atomic.AtomicReference
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import kotlin.test.*

/** Independent Nimbus signature/decryption checks. All authentication in these tests is simulated. */
class ScaPresentationInteropTest {
    private val factors = ScaAuthenticationMethods.KnowledgeAndPossession(
        ScaAuthenticationMethods.Knowledge.PIN_6_OR_MORE_DIGITS,
        ScaAuthenticationMethods.Possession.KEY_IN_LOCAL_NATIVE_WSCD,
    )
    private val simulatedAuthentication = ScaPresentationAuthorizer { factors }
    private val modes = listOf(
        OpenID4VPResponseMode.DIRECT_POST, OpenID4VPResponseMode.DIRECT_POST_JWT,
        OpenID4VPResponseMode.DC_API, OpenID4VPResponseMode.DC_API_JWT,
    )
    private val claimsByType = mapOf(
        "sca-iban" to mapOf("masked_iban" to "DE**1234", "iban" to "DE02120300000000202051", "bic" to "BYLADEM1001", "currency" to "EUR"),
        "sca-user" to mapOf("masked_psu_id" to "user***"),
        "sca-card-dpc" to mapOf("credential_id" to "synthetic-card", "network" to "synthetic", "card_id" to "card***"),
    )

    @Test
    fun `three payment credentials bind all four response modes and decrypt independently`() = runTest {
        val artifacts = mutableListOf<JsonObject>()
        val identifiers = mutableSetOf<String>()
        for (type in claimsByType.keys) for (mode in modes) {
            val fixture = fixture(type, mode)
            var observed: ScaPresentation? = null
            val vp = present(fixture, ScaPresentationAuthorizer { observed = it; factors })
            val payload = verify(fixture, vp)
            val proofId = payload.getValue("jti").jsonPrimitive.content
            assertEquals(4, UUID.fromString(proofId).version())
            assertTrue(identifiers.add(proofId), "$type/$mode must have a fresh jti")
            assertEquals(Json.encodeToJsonElement(mode), payload["response_mode"])
            assertEquals(factors.toJson(), payload["amr"])
            assertEquals(fixture.audience, payload["aud"]?.jsonPrimitive?.content)
            assertEquals(fixture.request.nonce, payload["nonce"]?.jsonPrimitive?.content)
            assertEquals("sha-256", payload["transaction_data_hashes_alg"]?.jsonPrimitive?.content)
            assertEquals(listOf(hash(fixture.transaction)), payload.getValue("transaction_data_hashes").jsonArray.map { it.jsonPrimitive.content })
            assertEquals(fixture.transaction, assertNotNull(observed).transactionData.single())
            assertEquals(proofId, observed!!.proofId)
            assertEquals("ES256", observed!!.signingAlgorithm)
            assertEquals(payload["sd_hash"]?.jsonPrimitive?.content, observed!!.sdHash)
            assertEquals("stored-$type", observed!!.credentialId)
            assertEquals(fixture.keyId, observed!!.holderKeyId)
            assertEquals(fixture.audience, observed!!.audience)
            assertEquals(fixture.request.nonce, observed!!.nonce)
            assertEquals(mode, observed!!.responseMode)

            val delivered = deliver(fixture, vp)
            assertEquals(Json.parseToJsonElement(vp), delivered["vp_token"])
            if (mode in OpenID4VPResponseMode.DIRECT_POST_RESPONSES) {
                assertEquals("synthetic-state", delivered["state"]?.jsonPrimitive?.content)
            }
            artifacts += buildJsonObject {
                put("type", type); put("authentication", "SIMULATED TEST FIXTURE")
                put("responseMode", Json.encodeToJsonElement(mode)); put("audience", fixture.audience)
                put("nonce", fixture.request.nonce); put("clientId", fixture.request.clientId)
                put("transactionData", fixture.transaction); put("vpToken", Json.parseToJsonElement(vp))
                put("issuerPublicJwk", Json.parseToJsonElement(fixture.issuer.toPublicJWK().toJSONString()))
                put("businessClaims", JsonArray(claimsByType.getValue(type).keys.map(::JsonPrimitive)))
            }
        }
        // Optional local cross-implementation check consumes only synthetic presentations and public keys.
        System.getenv("WALT_TS12_INTEROP_OUTPUT")?.let { output ->
            File(output).apply { parentFile.mkdirs(); writeText(JsonArray(artifacts).toString()) }
        }
    }

    @Test
    fun `SCA without an authorizer or with denied authorization produces no signature`() = runTest {
        val fixture = fixture()
        val missing = assertFailsWith<IllegalArgumentException> { present(fixture, null) }
        assertContains(missing.message.orEmpty(), "authentication evidence")
        assertFailsWith<IllegalStateException> {
            present(fixture, ScaPresentationAuthorizer { error("Authentication denied") })
        }
        assertFailsWith<CancellationException> {
            present(fixture, ScaPresentationAuthorizer { throw CancellationException("User cancelled") })
        }
        assertEquals(0, fixture.signatures)
    }

    @Test
    fun `SCA refuses missing or different credential holder keys before authentication`() = runTest {
        for (binding in listOf("missing", "different")) {
            val fixture = fixture(holderBinding = binding)
            var authorizations = 0
            assertFailsWith<IllegalArgumentException> {
                present(fixture, ScaPresentationAuthorizer { authorizations++; factors })
            }
            assertEquals(0, authorizations)
            assertEquals(0, fixture.signatures)
        }
    }

    @Test
    fun `successful authentication cannot bypass a failed key operation`() = runTest {
        val fixture = fixture().also { it.rejectSigning = true }
        assertFailsWith<IllegalStateException> { present(fixture, simulatedAuthentication) }
        assertEquals(1, fixture.signatures)
    }

    @Test
    fun `signing can establish the declared factors without a separate authentication operation`() = runTest {
        val fixture = fixture()
        val events = mutableListOf<String>()
        val signingEnforcedFactors = ScaAuthenticationMethods.PossessionAndInherence(
            ScaAuthenticationMethods.Possession.OTHER,
            ScaAuthenticationMethods.Inherence.OTHER,
        )
        // Models the ordering contract only, not a real platform policy or authentication result.
        fixture.beforeSigning = { events += "native authentication and signing" }
        val vp = present(fixture, ScaPresentationAuthorizer {
            events += "authorize proof intent"
            assertEquals(0, fixture.signatures)
            signingEnforcedFactors
        })
        events += "proof released"
        assertEquals(signingEnforcedFactors.toJson(), verify(fixture, vp)["amr"])
        assertEquals(listOf("authorize proof intent", "native authentication and signing", "proof released"), events)
        assertEquals(1, fixture.signatures)
    }

    @Test
    fun `cancellation during authorization prevents the signing operation`() = runTest {
        val fixture = fixture()
        var released = false
        val attempt = launch {
            present(fixture, ScaPresentationAuthorizer {
                currentCoroutineContext().cancel()
                factors // A callback may return normally after the operation was cancelled.
            })
            released = true
        }
        attempt.join()
        assertTrue(attempt.isCancelled)
        assertFalse(released)
        assertEquals(0, fixture.signatures)
    }

    @Test
    fun `late native success cannot release a proof after cancellation`() = runTest {
        val fixture = fixture()
        fixture.afterSigning = { currentCoroutineContext().cancel() }
        var released = false
        val attempt = launch {
            present(fixture, simulatedAuthentication)
            released = true
        }
        attempt.join()
        assertTrue(attempt.isCancelled)
        assertFalse(released)
        assertEquals(1, fixture.signatures)
    }

    @Test
    fun `legacy key overload uses the same SCA proof path`() = runTest {
        val fixture = fixture()
        val vp = WalletPresentFunctionality2.buildVpToken(
            authorizationRequest = fixture.request,
            matchedCredentials = fixture.matches,
            holderKey = fixture.legacyKey,
            holderDid = null,
            transactionDataTypeRegistry = id.walt.verifier.openid.transactiondata.TransactionDataTypeRegistry(),
            holderCrypto2Key = null,
            scaAuthorizer = simulatedAuthentication,
        )
        assertEquals(factors.toJson(), verify(fixture, vp)["amr"])
    }

    @Test
    fun `ordinary SD-JWT does not call SCA authorizer or emit SCA claims`() = runTest {
        val fixture = fixture(transactionType = "org.waltid.transaction-data.payment-authorization")
        val payload = verify(fixture, present(fixture, ScaPresentationAuthorizer { error("Must not authenticate generic transaction data") }))
        for (name in listOf("amr", "jti", "response_mode")) assertFalse(name in payload)
        assertFalse("transaction_data_hashes_alg" in payload) // preserve ordinary request's default behavior
    }

    @Test
    fun `authentication is requested again for each proof and bound to its nonce`() = runTest {
        val fixture = fixture()
        val nonces = mutableListOf<String>()
        val authorizer = ScaPresentationAuthorizer { nonces += it.nonce; factors }
        val first = verify(fixture, present(fixture, authorizer))
        fixture.request = fixture.request.copy(nonce = "second-nonce")
        val second = verify(fixture, present(fixture, authorizer))
        assertNotEquals(first["jti"], second["jti"])
        assertEquals(listOf("synthetic-nonce", "second-nonce"), nonces)
    }

    @Test
    fun `SCA transaction for another query never authorizes this credential`() = runTest {
        val fixture = fixture(transactionQueryId = "different_query")
        val payload = verify(fixture, present(fixture, ScaPresentationAuthorizer { error("Wrong credential binding") }))
        for (name in listOf("amr", "jti", "response_mode", "transaction_data_hashes")) assertFalse(name in payload)
    }

    @Test
    fun `missing response mode rejects SCA before signing`() = runTest {
        val fixture = fixture().also { it.request.responseMode = null }
        assertFailsWith<IllegalArgumentException> { present(fixture, simulatedAuthentication) }
        assertEquals(0, fixture.signatures)
    }

    @Test
    fun `all representable method combinations contain distinct categories`() {
        val knowledge = ScaAuthenticationMethods.Knowledge.PATTERN
        val possession = ScaAuthenticationMethods.Possession.OTHER
        val inherence = ScaAuthenticationMethods.Inherence.FACE_DEVICE
        for (methods in listOf(
            ScaAuthenticationMethods.KnowledgeAndPossession(knowledge, possession),
            ScaAuthenticationMethods.KnowledgeAndInherence(knowledge, inherence),
            ScaAuthenticationMethods.PossessionAndInherence(possession, inherence),
            ScaAuthenticationMethods.All(knowledge, possession, inherence),
        )) {
            val json = methods.toJson()
            assertTrue(json.size >= 2)
            assertEquals(json.size, json.map { it.jsonObject.keys.single() }.toSet().size)
        }
    }

    private suspend fun present(f: Fixture, authorizer: ScaPresentationAuthorizer?): String = WalletPresentFunctionality2.buildVpToken(
        authorizationRequest = f.request, matchedCredentials = f.matches, holderKey = f.key, holderDid = null,
        dcApiOrigin = if (f.request.responseMode in OpenID4VPResponseMode.DC_API_RESPONSES) "https://rp.example" else null,
        scaAuthorizer = authorizer,
    )

    /** Exercises the real HTTP form/direct-post encryption and DC API response builders. */
    private suspend fun deliver(f: Fixture, vp: String): JsonObject {
        val response = if (f.request.responseMode in OpenID4VPResponseMode.DC_API_RESPONSES) {
            DcApiWallet.buildResponse(
                ResolvedDcApiRequest(DcApiRequestProtocol.OPENID4VP_V1_SIGNED, "https://rp.example", f.request), vp,
            ).data
        } else {
            val received = AtomicReference<Map<String, String>>()
            val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            server.createContext("/response") { exchange ->
                val form = exchange.requestBody.bufferedReader().readText().split("&").associate { item ->
                    val pair = item.split("=", limit = 2)
                    URLDecoder.decode(pair[0], "UTF-8") to URLDecoder.decode(pair[1], "UTF-8")
                }
                received.set(form)
                exchange.responseHeaders.set("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, 2)
                exchange.responseBody.use { it.write("{}".toByteArray()) }
            }
            server.start()
            try {
                val result = WalletPresentFunctionality2.sendAuthorizationResponse(
                    f.request.copy(responseUri = "http://127.0.0.1:${server.address.port}/response"), vp,
                ).getOrThrow()
                assertEquals(true, result.transmissionSuccess)
                val form = assertNotNull(received.get())
                buildJsonObject {
                    form.forEach { (key, value) -> put(key, if (key == "vp_token") Json.parseToJsonElement(value) else JsonPrimitive(value)) }
                }
            } finally {
                server.stop(0)
            }
        }
        return if (f.request.responseMode in OpenID4VPResponseMode.ENCRYPTED_RESPONSES) {
            val decrypted = JWEObject.parse(response.getValue("response").jsonPrimitive.content)
                .apply { decrypt(ECDHDecrypter(f.recipient)) }
            Json.parseToJsonElement(decrypted.payload.toString()).jsonObject
        } else response
    }

    private fun verify(f: Fixture, vp: String): JsonObject {
        val token = Json.parseToJsonElement(vp).jsonObject.values.single().jsonArray.single().jsonPrimitive.content
        val issuerJwt = JWSObject.parse(token.substringBefore('~'))
        assertTrue(issuerJwt.verify(ECDSAVerifier(f.issuer.toPublicJWK())))
        val kb = JWSObject.parse(token.substringAfterLast('~'))
        assertTrue(kb.verify(ECDSAVerifier(f.holderPublic)))
        assertEquals("kb+jwt", kb.header.type.toString())
        val payload = Json.parseToJsonElement(kb.payload.toString()).jsonObject
        assertEquals(hash(token.substringBeforeLast('~') + "~"), payload["sd_hash"]?.jsonPrimitive?.content)
        return payload
    }

    private suspend fun fixture(
        type: String = "sca-iban", mode: OpenID4VPResponseMode = OpenID4VPResponseMode.DIRECT_POST,
        transactionType: String = TS12_PAYMENT_TYPE, transactionQueryId: String = "payment",
        holderBinding: String = "valid",
    ): Fixture {
        val issuer = ECKeyGenerator(Curve.P_256).generate()
        val holder = JWKKey.generate(KeyType.secp256r1)
        val cryptoKey = assertNotNull(WalletCrypto2KeyAdapter.signingKey(holder))
        val publicKey = holder.getPublicKey().exportJWKObject()
        val vct = "https://webuildconsortium.eu/sca/$type/1.0"
        val disclosures = claimsByType.getValue(type).map { (name, value) ->
            SdJwtSelectiveDisclosure("synthetic-salt-$name", name, JsonPrimitive(value))
        }
        val claims = buildJsonObject {
            put("iss", "https://issuer.example"); put("vct", vct); put("_sd_alg", "sha-256")
            put("iat", 1789990000); put("exp", 4102444800L)
            if (holderBinding != "missing") put("cnf", buildJsonObject {
                put("jwk", if (holderBinding == "different")
                    Json.parseToJsonElement(issuer.toPublicJWK().toJSONString()) else publicKey)
            })
            if (type == "sca-user") put("aud", "x509_san_dns:rp.example")
            put("_sd", JsonArray(disclosures.map { JsonPrimitive(hash(it.encoded)) }))
        }
        val issuerJwt = JWSObject(JWSHeader.Builder(JWSAlgorithm.ES256).type(JOSEObjectType("dc+sd-jwt")).build(), Payload(claims.toString()))
            .apply { sign(ECDSASigner(issuer)) }.serialize()
        val credential = SdJwtCredential(
            dmtype = CredentialDetectorTypes.SDJWTVCSubType.sdjwtvc,
            disclosables = mapOf("$._sd" to disclosures.map { it.encoded }.toSet()), disclosures = disclosures,
            credentialData = claims, originalCredentialData = claims, signature = null, signed = issuerJwt,
        )
        val query = CredentialQuery("payment", CredentialFormat.DC_SD_JWT, meta = SdJwtVcMeta(listOf(vct)))
        val raw = RawDcqlCredential("stored-$type", "dc+sd-jwt", claims, originalCredential = credential)
        val matches = mapOf("payment" to listOf(DcqlMatcher.DcqlMatchResult(raw, disclosures.associateBy { it.name!! }, query)))
        val transaction = encode(buildJsonObject {
            put("type", transactionType)
            put("credential_ids", buildJsonArray { add(transactionQueryId) })
            if (transactionType == TS12_PAYMENT_TYPE) {
                put("transaction_data_hashes_alg", buildJsonArray { add("sha-256") })
            }
            put("payload", buildJsonObject {
                put("transaction_id", "synthetic-payment"); put("currency", "EUR"); put("amount", 12.5)
                put("payee", buildJsonObject { put("name", "Synthetic shop"); put("id", "synthetic-payee") })
            })
        }.toString().encodeToByteArray())
        val recipient = ECKeyGenerator(Curve.P_256).keyID("response-encryption").generate()
        val encryptionJwk = JsonObject(Json.parseToJsonElement(recipient.toPublicJWK().toJSONString()).jsonObject +
            mapOf("alg" to JsonPrimitive("ECDH-ES"), "use" to JsonPrimitive("enc")))
        val request = AuthorizationRequest(clientId = "x509_san_dns:rp.example", nonce = "synthetic-nonce", responseMode = mode, state = "synthetic-state",
            transactionData = listOf(transaction), dcqlQuery = DcqlQuery(listOf(query)),
            clientMetadata = ClientMetadata(jwks = ClientMetadata.Jwks(listOf(encryptionJwk))),
            responseUri = if (mode in OpenID4VPResponseMode.DIRECT_POST_RESPONSES) "https://rp.example/response" else null)
        val fixture = Fixture(issuer, ECKey.parse(publicKey.toString()), cryptoKey, request, matches, transaction, recipient, holder)
        return fixture
    }

    private class Fixture(
        val issuer: ECKey, val holderPublic: ECKey, originalKey: id.walt.crypto2.keys.Key,
        var request: AuthorizationRequest, val matches: Map<String, List<DcqlMatcher.DcqlMatchResult>>, val transaction: String, val recipient: ECKey, val legacyKey: JWKKey,
    ) {
        var signatures = 0
        var rejectSigning = false
        var beforeSigning: suspend () -> Unit = {}
        var afterSigning: suspend () -> Unit = {}
        val key = object : id.walt.crypto2.keys.Key by originalKey {
            override val capabilities = originalKey.capabilities.copy(signer = Signer { data, algorithm ->
                signatures++
                check(!rejectSigning) { "Signing failed" }
                beforeSigning()
                requireNotNull(originalKey.capabilities.signer).sign(data, algorithm).also { afterSigning() }
            })
        }
        val keyId = key.id.value
        val audience get() = if (request.responseMode in OpenID4VPResponseMode.DC_API_RESPONSES) "origin:https://rp.example" else "x509_san_dns:rp.example"
    }

    private fun encode(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    private fun hash(value: String): String = encode(MessageDigest.getInstance("SHA-256").digest(value.toByteArray()))
}
