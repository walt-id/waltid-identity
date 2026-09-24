package id.walt.wallet2.handlers

import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.keys.*
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.wallet2.data.*
import id.walt.wallet2.stores.inmemory.InMemoryCredentialStore
import id.walt.wallet2.stores.inmemory.InMemoryKeyStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.*
import io.ktor.http.content.OutgoingContent
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

class WalletBatchIssuanceTest {
    @Test fun preAuthorizedReceiveNegotiatesFromMetadataAndHonorsIdentifiersEvenAfterScopeAuthorization() = runTest {
        for (detailsSupported in listOf(false, true)) for (returnsIdentifiers in listOf(false, true)) {
            if (detailsSupported && !returnsIdentifiers) continue
            val fixture = fixture(true)
            var tokenCalls = 0
            val targets = mutableListOf<String>()
            val result = WalletIssuanceHandler.receiveCredential(fixture.wallet,
                ReceiveCredentialRequest(offerJson = offer(), credentials = listOf(fixture.selection(2))),
                httpClient = client(authorizationDetailsSupported = detailsSupported, token = { parameters ->
                    tokenCalls++
                    assertAutomaticParameters(parameters, detailsSupported)
                    negotiatedToken(returnsIdentifiers)
                }, credential = { body ->
                    if (returnsIdentifiers) {
                        assertNull(body["credential_configuration_id"])
                        targets += body.getValue("credential_identifier").jsonPrimitive.content
                    } else {
                        assertNull(body["credential_identifier"])
                        targets += body.getValue("credential_configuration_id").jsonPrimitive.content
                    }
                    batchTestResponse(body.proofs())
                }))
            assertEquals(1, tokenCalls)
            assertEquals(if (returnsIdentifiers) listOf("dataset-a", "dataset-b") else listOf("identity"), targets)
            assertEquals(targets.size * 2, result.credentialIds.size)
        }
    }

    @Test fun isolatedTokenAndAuthorizationUrlAutomaticallyNegotiateWithoutCallerSwitches() = runTest {
        for (detailsSupported in listOf(false, true)) {
            val fixture = fixture(true)
            var tokenCalls = 0
            val http = client(authorizationDetailsSupported = detailsSupported, token = { parameters ->
                tokenCalls++
                assertAutomaticParameters(parameters, detailsSupported)
                negotiatedToken(detailsSupported)
            }, credential = { error("Isolated authorization must not issue credentials") })
            val result = WalletIssuanceHandler.requestToken(fixture.wallet,
                RequestTokenRequest(tokenEndpoint = Url("$ISSUER/token"), preAuthorizedCode = "pre-code",
                    credentialIssuer = ISSUER, credentialConfigurationIds = listOf("identity")), httpClient = http)
            assertEquals("access", result.accessToken)
            assertEquals(1, tokenCalls)
            for (withOffer in listOf(false, true)) {
                val authorization = WalletIssuanceHandler.generateAuthorizationUrl(fixture.wallet,
                    GenerateAuthorizationUrlRequest(
                        credentialIssuer = ISSUER.takeUnless { withOffer },
                        offerJson = offer().takeIf { withOffer },
                        credentialConfigurationIds = listOf("identity")), httpClient = http)
                assertAutomaticParameters(Url(authorization.authorizationUrl.toString()).parameters, detailsSupported)
            }
        }
    }

    @Test fun pushedAuthorizationNegotiatesForBothStatelessAndRetainedFlows() = runTest {
        for (detailsSupported in listOf(false, true)) {
            val fixture = fixture(true)
            var pushedRequests = 0
            val http = client(authorizationDetailsSupported = detailsSupported,
                par = { parameters ->
                    pushedRequests++
                    assertAutomaticParameters(parameters, detailsSupported)
                }, credential = { error("Authorization must not issue credentials") })
            WalletIssuanceHandler.generateAuthorizationUrl(fixture.wallet,
                GenerateAuthorizationUrlRequest(credentialIssuer = ISSUER, credentialConfigurationIds = listOf("identity")), httpClient = http)
            val service = WalletIssuanceSessionService(fixture.wallet, httpClient = http)
            val preview = service.start(WalletIssuanceSessionRequest(credentialIssuer = ISSUER, credentialConfigurationIds = listOf("identity")))
            val authorization = service.beginAuthorization(preview.id, listOf(fixture.selection(2)))
            assertTrue(authorization.pushedAuthorizationRequestUsed)
            assertEquals(2, pushedRequests)
        }
    }

    @Test fun missingAuthorizationCapabilitiesFailBeforeRedeemingPreAuthorizedCode() = runTest {
        val fixture = fixture(true)
        var tokenCalls = 0
        assertFailsWith<IllegalArgumentException> {
            WalletIssuanceHandler.receiveCredential(fixture.wallet, ReceiveCredentialRequest(offerJson = offer()),
                httpClient = client(metadata = metadata().replace("\"scope\":\"identity\",", ""),
                    authorizationDetailsSupported = false,
                    token = { tokenCalls++; TOKEN }, credential = { error("Credential request must not be sent") }))
        }
        assertEquals(0, tokenCalls)
    }

    @Test fun retainedSessionsNegotiateBothGrantsAndPreserveBatchSelectionsAcrossRestart() = runTest {
        for (detailsSupported in listOf(false, true)) for (authorized in listOf(false, true)) {
            val fixture = fixture(true)
            val records = MemorySessionStore()
            val http = client(authorizationDetailsSupported = detailsSupported, token = { parameters ->
                if (!authorized) assertAutomaticParameters(parameters, detailsSupported)
                negotiatedToken(detailsSupported)
            }, credential = { batchTestResponse(it.proofs()) })
            val service = WalletIssuanceSessionService(fixture.wallet, httpClient = http, sessionStore = records)
            val preview = service.start(if (authorized) WalletIssuanceSessionRequest(
                credentialIssuer = ISSUER, credentialConfigurationIds = listOf("identity"), redirectUri = Url("openid://callback"))
                else WalletIssuanceSessionRequest(offerJson = offer()))
            val result = if (authorized) {
                val authorization = service.beginAuthorization(preview.id, listOf(fixture.selection(2)))
                assertAutomaticParameters(Url(authorization.url).parameters, detailsSupported)
                val restored = WalletIssuanceSessionService(fixture.wallet, httpClient = http, sessionStore = records)
                restored.continueAuthorization(WalletIssuanceAuthorizationCallback(preview.id,
                    "openid://callback?code=code&state=${authorization.state}"))
            } else {
                service.continuePreAuthorized(preview.id, credentials = listOf(fixture.selection(2)))
            }
            assertEquals(if (detailsSupported) 4 else 2, assertIs<WalletIssuanceOutcome.Stored>(result).credentialIds.size)
        }
    }

    private fun assertAutomaticParameters(parameters: Parameters, detailsSupported: Boolean) {
        if (detailsSupported) {
            assertNull(parameters["scope"])
            val details = Json.parseToJsonElement(assertNotNull(parameters["authorization_details"])).jsonArray
            assertEquals("identity", details.single().jsonObject.getValue("credential_configuration_id").jsonPrimitive.content)
        } else {
            assertEquals("identity", parameters["scope"])
            assertNull(parameters["authorization_details"])
        }
    }

    private fun negotiatedToken(identifiers: Boolean): String = if (identifiers)
        """{"access_token":"access","token_type":"Bearer","authorization_details":[
            {"type":"openid_credential","credential_configuration_id":"identity","credential_identifiers":["dataset-a","dataset-b"]}]}"""
        else """{"access_token":"access","token_type":"Bearer","scope":"identity"}"""

    @Test fun singleIsDefaultAndExplicitBatchesMatchReorderedCrypto1AndCrypto2Keys() = runTest {
        for (crypto2 in listOf(false, true)) for (count in listOf(1, 2, 5)) {
            val fixture = fixture(crypto2)
            val received = mutableListOf<JsonObject>()
            val events = mutableListOf<String>()
            val client = client(credential = { body ->
                received += body
                batchTestResponse(body.proofs().reversed())
            })
            val result = WalletIssuanceHandler.receiveCredential(fixture.wallet,
                ReceiveCredentialRequest(offerJson = offer(),
                    credentials = if (count == 1) null else listOf(fixture.selection(count))),
                httpClient = client,
                beforeCredentialsStored = { events += "before:$it" },
                onCredentialStored = { events += "stored" })
            assertEquals(count, result.credentialIds.size)
            assertEquals(count, received.single().proofs().size)
            assertEquals(listOf("before:$count") + List(count) { "stored" }, events)
            val stored = fixture.store.listCredentials().toList()
            val selected = fixture.keys.take(count).reversed()
            for ((credential, key) in stored.zip(selected)) {
                assertEquals(key.keyId, fixture.wallet.resolveHolderKey(credential, setOf(KeyUsage.SIGN)).keyMaterial.keyId)
            }
        }
    }

    @Test fun acceptsFewerCredentialsWithoutAssigningTheFirstProofsKey() = runTest {
        val fixture = fixture(true)
        val result = WalletIssuanceHandler.receiveCredential(fixture.wallet,
            ReceiveCredentialRequest(offerJson = offer(), credentials = listOf(fixture.selection(5))),
            httpClient = client(credential = { batchTestResponse(listOf(it.proofs().last())) }))
        assertEquals(1, result.credentialIds.size)
        val stored = fixture.store.listCredentials().toList().single()
        assertEquals(fixture.keys.last().keyId, fixture.wallet.resolveHolderKey(stored, setOf(KeyUsage.SIGN)).keyMaterial.keyId)
    }

    @Test fun deferredResultsRetainProofBindingValidationForSingleAndBatchIssuance() = runTest {
        for (count in listOf(1, 2)) {
            val fixture = fixture(true)
            val result = WalletIssuanceHandler.receiveCredential(fixture.wallet,
                ReceiveCredentialRequest(offerJson = offer(), credentials = listOf(fixture.selection(count))),
                httpClient = client(credential = { """{"transaction_id":"deferred-target","interval":5}""" }))
            assertTrue(result.credentialIds.isEmpty())
            val deferred = result.deferredCredentials.single()
            assertTrue(deferred.proofRequired)
            assertEquals(fixture.keys.take(count).map { it.keyId }, deferred.holderBindings.map { it.keyId })
            assertTrue(deferred.holderBindings.all { it.key == null })
        }
    }

    @Test fun repeatedHolderKeysRemainPermitted() = runTest {
        val fixture = fixture(false)
        val result = WalletIssuanceHandler.receiveCredential(fixture.wallet,
            ReceiveCredentialRequest(offerJson = offer(), credentials = listOf(WalletCredentialSelection("identity",
                holderBindings = List(2) { CredentialHolderBinding(fixture.keys.first().keyId) }))),
            httpClient = client(credential = { batchTestResponse(it.proofs()) }))
        assertEquals(2, result.credentialIds.size)
    }

    @Test fun unknownHolderKeyInLastResponseEntryPreventsAllWritesAndCallbacks() = runTest {
        val fixture = fixture(true)
        val foreign = batchTestCredential(JWKKey.generate(KeyType.secp256r1))
        var before = 0
        var stored = 0
        assertFails {
            WalletIssuanceHandler.receiveCredential(fixture.wallet,
                ReceiveCredentialRequest(offerJson = offer(), credentials = listOf(fixture.selection(2))),
                httpClient = client(credential = { request ->
                    val first = Json.parseToJsonElement(batchTestResponse(request.proofs().take(1))).jsonObject["credentials"]!!.jsonArray.single()
                    buildJsonObject { put("credentials", JsonArray(listOf(first, buildJsonObject { put("credential", foreign) }))) }.toString()
                }), beforeCredentialsStored = { before++ }, onCredentialStored = { stored++ })
        }
        assertEquals(0, before)
        assertEquals(0, stored)
        assertTrue(fixture.store.listCredentials().toList().isEmpty())
    }

    @Test fun batchesAboveMetadataLimitFailBeforeRedeemingToken() = runTest {
        val fixture = fixture(true)
        var tokenCalls = 0
        assertFailsWith<IllegalArgumentException> {
            WalletIssuanceHandler.receiveCredential(fixture.wallet,
                ReceiveCredentialRequest(offerJson = offer(), credentials = listOf(fixture.selection(5))),
                httpClient = client(metadata = metadata(2), token = { tokenCalls++; TOKEN },
                    credential = { error("Credential request must not be sent") }))
        }
        assertEquals(0, tokenCalls)
    }

    @Test fun tokenDatasetsProduceSeparateRequestsUsingTheSameToken() = runTest {
        val fixture = fixture(true)
        var tokenCalls = 0
        val targets = mutableListOf<String>()
        val result = WalletIssuanceHandler.receiveCredential(fixture.wallet,
            ReceiveCredentialRequest(offerJson = offer(), credentials = listOf(fixture.selection(2))),
            httpClient = client(token = {
                tokenCalls++
                """{"access_token":"access","token_type":"Bearer","authorization_details":[
                    {"type":"openid_credential","credential_configuration_id":"identity","credential_identifiers":["dataset-a","dataset-b"]}]}"""
            }, credential = { body ->
                assertNull(body["credential_configuration_id"])
                targets += body["credential_identifier"]!!.jsonPrimitive.content
                batchTestResponse(body.proofs())
            }))
        assertEquals(1, tokenCalls)
        assertEquals(listOf("dataset-a", "dataset-b"), targets)
        assertEquals(4, result.credentialIds.size)
    }

    @Test fun authorizedReceiveUsesTokenIdentifiersAndBatchProofs() = runTest {
        val fixture = fixture(true)
        val targets = mutableListOf<String>()
        val result = WalletIssuanceHandler.receiveCredentialAuthCode(fixture.wallet,
            ReceiveAuthorizedCredentialRequest(
                code = "code", credentialIssuer = ISSUER, credentialEndpoint = Url("$ISSUER/credential"),
                credentials = listOf(fixture.selection(2))),
            httpClient = client(token = {
                """{"access_token":"access","token_type":"Bearer","authorization_details":[
                    {"type":"openid_credential","credential_configuration_id":"identity","credential_identifiers":["a","b"]}]}"""
            }, credential = { body ->
                targets += body["credential_identifier"]!!.jsonPrimitive.content
                batchTestResponse(body.proofs())
            }))
        assertEquals(listOf("a", "b"), targets)
        assertEquals(4, result.credentialIds.size)
    }

    @Test fun mobileDeferredBatchSurvivesServiceRecreationWithEveryHolderKey() = runTest {
        val fixture = fixture(true)
        val records = MemorySessionStore()
        var proofs = emptyList<String>()
        val http = client(credentialStatus = HttpStatusCode.Accepted, credential = {
            proofs = it.proofs()
            """{"transaction_id":"deferred-batch","interval":1}"""
        }, deferred = { batchTestResponse(proofs.reversed()) })
        val service = WalletIssuanceSessionService(fixture.wallet, httpClient = http, sessionStore = records)
        val preview = service.start(WalletIssuanceSessionRequest(offerJson = offer()))
        assertEquals(5, preview.offer.batchSize)
        assertTrue(proofs.isEmpty())
        val pending = assertIs<WalletIssuanceOutcome.Deferred>(
            service.continuePreAuthorized(preview.id, credentials = listOf(fixture.selection(5))))
        assertEquals(5, proofs.size)
        val restored = WalletIssuanceSessionService(fixture.wallet, httpClient = http, sessionStore = records)
        val result = assertIs<WalletIssuanceOutcome.Stored>(restored.resumeDeferred(pending.credentials.single().id))
        assertEquals(5, result.credentialIds.size)
        val stored = fixture.store.listCredentials().toList()
        assertEquals(fixture.keys.reversed().map { it.keyId },
            stored.map { fixture.wallet.resolveHolderKey(it, setOf(KeyUsage.SIGN)).keyMaterial.keyId })
        assertTrue(records.records.isEmpty())
    }

    @Test fun walletInitiatedAuthorizationSelectsOnlyAcceptedConfigurationsAndRetainsKeys() = runTest {
        val fixture = fixture(true)
        val records = MemorySessionStore()
        val http = client(credential = { batchTestResponse(it.proofs()) })
        val service = WalletIssuanceSessionService(fixture.wallet, httpClient = http, sessionStore = records)
        val preview = service.start(WalletIssuanceSessionRequest(
            credentialIssuer = ISSUER, credentialConfigurationIds = listOf("identity"),
            redirectUri = Url("openid://callback")))
        val authorization = service.beginAuthorization(preview.id, listOf(fixture.selection(2)))
        val details = Url(authorization.url).parameters["authorization_details"]!!
        assertEquals("identity", Json.parseToJsonElement(details).jsonArray.single().jsonObject["credential_configuration_id"]!!.jsonPrimitive.content)
        val restored = WalletIssuanceSessionService(fixture.wallet, httpClient = http, sessionStore = records)
        val result = restored.continueAuthorization(WalletIssuanceAuthorizationCallback(preview.id,
            "openid://callback?code=code&state=${authorization.state}"))
        assertEquals(2, assertIs<WalletIssuanceOutcome.Stored>(result).credentialIds.size)
    }


    @Test fun legacyDeferredRecordNormalizesToItsOriginalSingleKey() = runTest {
        val fixture = fixture(true)
        val records = MemorySessionStore()
        var proofs = emptyList<String>()
        val http = client(credentialStatus = HttpStatusCode.Accepted, credential = {
            proofs = it.proofs()
            """{"transaction_id":"legacy","interval":1}"""
        }, deferred = { batchTestResponse(proofs) })
        val service = WalletIssuanceSessionService(fixture.wallet, httpClient = http, sessionStore = records)
        val preview = service.start(WalletIssuanceSessionRequest(offerJson = offer()))
        val pending = assertIs<WalletIssuanceOutcome.Deferred>(service.continuePreAuthorized(preview.id))
        // Previous persisted schema had selectedPublicJwk/keyId but no binding collection.
        records.records.replaceAll { _, record ->
            record.copy(payload = JsonObject(Json.parseToJsonElement(record.payload).jsonObject -
                setOf("bindings", "proofRequired")).toString())
        }
        val restored = WalletIssuanceSessionService(fixture.wallet, httpClient = http, sessionStore = records)
        val result = assertIs<WalletIssuanceOutcome.Stored>(restored.resumeDeferred(pending.credentials.single().id))
        assertEquals(1, result.credentialIds.size)
        assertEquals(fixture.keys.first().keyId,
            fixture.wallet.resolveHolderKey(result.credentialIds.single(), setOf(KeyUsage.SIGN)).keyMaterial.keyId)
    }

    @Test fun legacyAuthorizationSessionRestoresDefaultSelection() = runTest {
        val fixture = fixture(true)
        val records = MemorySessionStore()
        val http = client(credential = { batchTestResponse(it.proofs()) })
        val service = WalletIssuanceSessionService(fixture.wallet, httpClient = http, sessionStore = records)
        val authOffer = buildJsonObject {
            put("credential_issuer", ISSUER)
            put("credential_configuration_ids", JsonArray(listOf(JsonPrimitive("identity"))))
            putJsonObject("grants") { putJsonObject("authorization_code") { put("issuer_state", "offered-session") } }
        }
        val preview = service.start(WalletIssuanceSessionRequest(offerJson = authOffer, redirectUri = Url("openid://callback")))
        val authorization = service.beginAuthorization(preview.id)
        records.records.replaceAll { _, record ->
            record.copy(payload = JsonObject(Json.parseToJsonElement(record.payload).jsonObject - "selections").toString())
        }
        val result = WalletIssuanceSessionService(fixture.wallet, httpClient = http, sessionStore = records)
            .continueAuthorization(WalletIssuanceAuthorizationCallback(preview.id,
                "openid://callback?code=code&state=${authorization.state}"))
        assertEquals(1, assertIs<WalletIssuanceOutcome.Stored>(result).credentialIds.size)
    }

    @Test fun nonceRetryRebuildsEveryProofWithSameHolderKeys() = runTest {
        val fixture = fixture(true)
        val collections = mutableListOf<List<String>>()
        var nonceRequests = 0
        val http = HttpClient(MockEngine) {
            engine { addHandler { request ->
                when (request.url.encodedPath) {
                    "/nonce" -> respond("""{"c_nonce":"nonce-${++nonceRequests}"}""",
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
                    "/credential" -> {
                        val body = Json.parseToJsonElement((request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()).jsonObject
                        collections += body.proofs()
                        respond(if (collections.size == 1) """{"error":"invalid_nonce"}""" else batchTestResponse(body.proofs()),
                            if (collections.size == 1) HttpStatusCode.BadRequest else HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
                    }
                    else -> error("Unexpected request")
                }
            } }
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val config = Json.decodeFromString<id.walt.openid4vci.metadata.issuer.CredentialIssuerMetadata>(metadata())
        val selected = fixture.wallet.resolveCredentialSelections(listOf(fixture.selection(5)), listOf("identity"),
            config, fixture.keys.first(), null).single()
        WalletIssuanceHandler.requestCredentialWithNonceRetry(
            FetchCredentialRequest(Url("$ISSUER/credential"), "access", "identity"), "$ISSUER/nonce", http,
            buildProof = { WalletIssuanceHandler.buildProofCollection(selected, config.credentialConfigurationsSupported.getValue("identity"), ISSUER, it) })
        assertEquals(listOf(5, 5), collections.map { it.size })
        for ((index, proofs) in collections.withIndex()) {
            assertTrue(proofs.all { proof ->
                Json.parseToJsonElement(CompactJws.decodeUnverified(proof).payload.decodeToString()).jsonObject["nonce"]!!.jsonPrimitive.content == "nonce-${index + 1}"
            })
        }
        assertEquals(collections.first().map { CompactJws.decodeUnverified(it).protectedHeader["jwk"] },
            collections.last().map { CompactJws.decodeUnverified(it).protectedHeader["jwk"] })
    }

    @Test fun presentationUsesTheStoredBatchKeyInsteadOfWalletDefault() = runTest {
        for (crypto2 in listOf(false, true)) {
            val fixture = fixture(crypto2)
            val result = WalletIssuanceHandler.receiveCredential(fixture.wallet,
                ReceiveCredentialRequest(offerJson = offer(), credentials = listOf(fixture.selection(2))),
                httpClient = client(credential = { batchTestResponse(it.proofs().reversed()) }))
            val credentialId = result.credentialIds.first() // holder #2; wallet default is holder #1
            val request = id.walt.verifier.openid.models.authorization.AuthorizationRequest(
                clientId = "redirect_uri:https://verifier.example/callback",
                redirectUri = "https://verifier.example/callback", nonce = "presentation-nonce",
                responseType = id.walt.verifier.openid.models.openid.OpenID4VPResponseType.VP_TOKEN,
                dcqlQuery = id.walt.dcql.models.DcqlQuery(credentials = listOf(
                    id.walt.dcql.models.CredentialQuery(id = "identity", format = id.walt.dcql.models.CredentialFormat.DC_SD_JWT,
                        meta = id.walt.dcql.models.meta.SdJwtVcMeta(vctValues = listOf("identity")))) ),
            )
            val presentation = WalletPresentationHandler.buildVpToken(fixture.wallet,
                BuildVpTokenRequest(Url("https://verifier.example/request"), selectedCredentialIds = mapOf("identity" to listOf(credentialId))),
                resolveAuthorizationRequest = { id.waltid.openid4vp.wallet.request.ResolvedAuthorizationRequest.Plain(request) })
            val sdJwt = Json.parseToJsonElement(presentation.vpToken).jsonObject["identity"]!!.jsonArray.single().jsonPrimitive.content
            val keyBindingJwt = sdJwt.substringAfterLast('~')
            val holder = fixture.keys[1]
            if (holder.crypto2Key != null) CompactJws.verify(keyBindingJwt, holder.crypto2Key, id.walt.crypto2.jose.JwsAlgorithm.ES256)
            else assertTrue(holder.legacyKey!!.verifyJws(keyBindingJwt).isSuccess)
        }
    }


    @Test fun deferredBatchWithMissingHolderKeyFailsBeforePollingAndRetainsRecord() = runTest {
        val fixture = fixture(true)
        val records = MemorySessionStore()
        var polls = 0
        val http = client(credentialStatus = HttpStatusCode.Accepted,
            credential = { """{"transaction_id":"batch","interval":1}""" },
            deferred = { polls++; error("Must not poll with a missing key") })
        val service = WalletIssuanceSessionService(fixture.wallet, httpClient = http, sessionStore = records)
        val preview = service.start(WalletIssuanceSessionRequest(offerJson = offer()))
        val pending = assertIs<WalletIssuanceOutcome.Deferred>(
            service.continuePreAuthorized(preview.id, credentials = listOf(fixture.selection(2))))
        fixture.wallet.keyStores!!.single().removeKey(fixture.keys[1].keyId)
        val restored = WalletIssuanceSessionService(fixture.wallet, httpClient = http, sessionStore = records)
        assertIs<WalletIssuanceOutcome.Failed>(restored.resumeDeferred(pending.credentials.single().id))
        assertEquals(0, polls)
        assertEquals(1, records.records.size)
    }

    private suspend fun fixture(crypto2: Boolean): Fixture {
        val keys = InMemoryKeyStore()
        val ids = (1..5).map { index ->
            if (crypto2) keys.addCrypto2Key(CryptoRuntime(defaultSoftwareKeyProviders()).generateSoftwareKey(
                GenerateSoftwareKeyRequest(KeyId("holder-$index"), KeySpec.Ec(EcCurve.P256), setOf(KeyUsage.SIGN, KeyUsage.VERIFY))))
            else keys.addKey(JWKKey.generate(KeyType.secp256r1))
        }
        val store = InMemoryCredentialStore()
        val wallet = Wallet("batch-wallet", keyStores = listOf(keys), credentialStores = listOf(store), defaultKeyId = ids.first())
        return Fixture(wallet, store, ids.map { wallet.resolveKeyMaterial(it, setOf(KeyUsage.SIGN))!! })
    }

    private data class Fixture(val wallet: Wallet, val store: InMemoryCredentialStore, val keys: List<WalletKeyStoreEntry>) {
        fun selection(count: Int) = WalletCredentialSelection("identity", holderBindings = keys.take(count).map { CredentialHolderBinding(it.keyId) })
    }

    private class MemorySessionStore : WalletIssuanceSessionStore {
        val records = mutableMapOf<String, WalletIssuanceSessionRecord>()
        override suspend fun get(id: String) = records[id]
        override suspend fun put(record: WalletIssuanceSessionRecord) { records[record.id] = record }
        override suspend fun remove(id: String): Boolean = records.remove(id) != null
        override suspend fun list() = records.values.toList()
    }

    private fun JsonObject.proofs(): List<String> = getValue("proofs").jsonObject.getValue("jwt").jsonArray.map { it.jsonPrimitive.content }
    private fun offer(): JsonObject = Json.parseToJsonElement(
        """{"credential_issuer":"$ISSUER","credential_configuration_ids":["identity"],
            "grants":{"urn:ietf:params:oauth:grant-type:pre-authorized_code":{"pre-authorized_code":"pre-code"}}}""").jsonObject

    private fun metadata(batchSize: Int = 5) = """{"credential_issuer":"$ISSUER","credential_endpoint":"$ISSUER/credential",
        "deferred_credential_endpoint":"$ISSUER/deferred","nonce_endpoint":"$ISSUER/nonce","batch_credential_issuance":{"batch_size":$batchSize},
        "credential_configurations_supported":{"identity":{"format":"dc+sd-jwt","vct":"identity","scope":"identity",
        "cryptographic_binding_methods_supported":["jwk"],"proof_types_supported":{"jwt":{"proof_signing_alg_values_supported":["ES256"]}}}}}"""

    private fun client(
        metadata: String = metadata(),
        authorizationDetailsSupported: Boolean = true,
        token: (Parameters) -> String = { TOKEN },
        par: ((Parameters) -> Unit)? = null,
        credentialStatus: HttpStatusCode = HttpStatusCode.OK,
        credential: suspend (JsonObject) -> String,
        deferred: suspend () -> String = { error("Unexpected deferred request") },
    ) = HttpClient(MockEngine) {
        engine {
            addHandler { request ->
                var status = HttpStatusCode.OK
                val content = when (request.url.toString()) {
                    "$ISSUER/.well-known/openid-credential-issuer" -> metadata
                    "$ISSUER/.well-known/oauth-authorization-server" ->
                        """{"issuer":"$ISSUER","token_endpoint":"$ISSUER/token","authorization_endpoint":"$ISSUER/authorize",
                            ${if (authorizationDetailsSupported) "\"authorization_details_types_supported\":[\"openid_credential\"]," else ""}
                            ${if (par != null) "\"pushed_authorization_request_endpoint\":\"$ISSUER/par\",\"require_pushed_authorization_requests\":true," else ""}
                            "response_types_supported":["code"]}"""
                    "$ISSUER/token" -> token(parseQueryString((request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()))
                    "$ISSUER/par" -> {
                        status = HttpStatusCode.Created
                        requireNotNull(par)(parseQueryString((request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()))
                        """{"request_uri":"urn:ietf:params:oauth:request_uri:test","expires_in":60}"""
                    }
                    "$ISSUER/nonce" -> """{"c_nonce":"nonce"}"""
                    "$ISSUER/credential" -> {
                        assertEquals("Bearer access", request.headers[HttpHeaders.Authorization])
                        status = credentialStatus
                        credential(Json.parseToJsonElement((request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()).jsonObject)
                    }
                    "$ISSUER/deferred" -> deferred()
                    else -> error("Unexpected endpoint ${request.url}")
                }
                respond(content, status, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
            }
        }
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    companion object {
        const val ISSUER = "https://issuer.example"
        const val TOKEN = """{"access_token":"access","token_type":"Bearer"}"""
    }
}
