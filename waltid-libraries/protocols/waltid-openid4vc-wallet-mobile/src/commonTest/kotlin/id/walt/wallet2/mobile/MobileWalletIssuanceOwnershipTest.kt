package id.walt.wallet2.mobile

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.keys.*
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.wallet2.handlers.*
import id.walt.wallet2.stores.inmemory.InMemoryIssuanceSessionStore
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import id.walt.wallet2.stores.inmemory.InMemoryCredentialStore
import id.walt.wallet2.stores.inmemory.InMemoryDidStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.*

class MobileWalletIssuanceOwnershipTest {
    @Test fun invalidSessionAndExcessCopiesDoNotGenerateKeys() = runTest {
        val fixture = fixture()
        assertIs<WalletIssuanceOutcome.Failed>(fixture.wallet.continuePreAuthorizedIssuance("missing", "123", listOf(selection(2))))
        val session = fixture.wallet.startIssuance(request())
        for (copies in listOf(0, 3)) {
            assertIs<WalletIssuanceOutcome.Failed>(fixture.wallet.continuePreAuthorizedIssuance(session.id, "123", listOf(selection(copies))))
        }
        assertEquals(0, fixture.created)
        assertEquals(0, fixture.tokenCalls)
    }

    @Test fun validationFailureCleansOnlyNewKeysForBothGrants() = runTest {
        for (authorization in listOf(false, true)) {
            val fixture = fixture()
            val session = fixture.wallet.startIssuance(request(authorization))
            // The missing second binding fails core resolution after the first configuration's key was prepared.
            val selections = listOf(selection(1), MobileWalletCredentialSelection("other",
                MobileWalletCredentialHolders.Existing(listOf(MobileWalletHolderBinding("missing")))))
            if (authorization) assertFails { fixture.wallet.beginAuthorizationIssuance(session.id, selections) }
            else assertIs<WalletIssuanceOutcome.Failed>(fixture.wallet.continuePreAuthorizedIssuance(session.id, "123", selections))
            assertEquals(1, fixture.created)
            assertEquals(listOf("default"), fixture.keys.listKeys().toList().map { it.keyId })
            assertTrue(fixture.dids.listDids().toList().isEmpty())
            assertEquals(0, fixture.tokenCalls)
        }
    }

    @Test fun rejectedTransactionCodeRetriesReuseAcceptedKeysAcrossRestartAndRejectChangedCounts() = runTest {
        val fixture = fixture(records = InMemoryIssuanceSessionStore())
        val session = fixture.wallet.startIssuance(request())
        repeat(2) {
            assertIs<WalletIssuanceOutcome.Failed>(fixture.wallet.continuePreAuthorizedIssuance(session.id, "123", listOf(selection(2))))
            fixture.wallet = fixture.reopen()
        }
        assertEquals(2, fixture.created)
        assertEquals(2, fixture.tokenCalls)
        assertEquals(3, fixture.keys.listKeys().toList().size)
        assertEquals(2, fixture.dids.listDids().toList().size)
        assertIs<WalletIssuanceOutcome.Failed>(fixture.wallet.continuePreAuthorizedIssuance(session.id, "123", listOf(selection(1))))
        assertEquals(2, fixture.created)
        assertEquals(2, fixture.tokenCalls)
    }

    @Test fun cancellationDuringPreparationCleansKeysBeforeIssuerWork() = runTest {
        val fixture = fixture(afterGenerate = { if (it == 2) currentCoroutineContext().cancel() })
        val session = fixture.wallet.startIssuance(request())
        val operation = launch { fixture.wallet.continuePreAuthorizedIssuance(session.id, "123", listOf(selection(2))) }
        operation.join()
        assertTrue(operation.isCancelled)
        assertEquals(2, fixture.created)
        assertEquals(listOf("default"), fixture.keys.listKeys().toList().map { it.keyId })
        assertTrue(fixture.dids.listDids().toList().isEmpty())
        assertEquals(0, fixture.tokenCalls)
    }

    @Test fun uncertainAcceptanceCheckpointRetainsKeys() = runTest {
        for (authorization in listOf(false, true)) {
            val delegate = InMemoryIssuanceSessionStore()
            var attemptedCheckpoint = false
            val records = object : AtomicWalletIssuanceSessionStore by delegate {
                override suspend fun compareAndSet(expected: WalletIssuanceSessionRecord, replacement: WalletIssuanceSessionRecord?): Boolean {
                    if (replacement?.payload?.contains("generated-") == true) {
                        // The store may commit before losing its acknowledgement. The keys must survive either outcome.
                        delegate.compareAndSet(expected, replacement)
                        attemptedCheckpoint = true
                        error("Checkpoint acknowledgement lost")
                    }
                    return delegate.compareAndSet(expected, replacement)
                }
            }
            val fixture = fixture(records = records)
            val session = fixture.wallet.startIssuance(request(authorization))
            if (authorization) assertFails { fixture.wallet.beginAuthorizationIssuance(session.id, listOf(selection(2))) }
            else assertIs<WalletIssuanceOutcome.Failed>(fixture.wallet.continuePreAuthorizedIssuance(session.id, "123", listOf(selection(2))))
            assertTrue(attemptedCheckpoint)
            assertEquals(2, fixture.created)
            assertEquals(3, fixture.keys.listKeys().toList().size)
            assertEquals(2, fixture.dids.listDids().toList().size)
            assertEquals(0, fixture.tokenCalls)
        }
    }

    private fun selection(count: Int) = MobileWalletCredentialSelection("identity", MobileWalletCredentialHolders.NewKeys(count))

    private fun request(authorization: Boolean = false) = MobileWalletIssuanceRequest(
        offer = MobileWalletCredentialOffer.InlineJson("""{
            "credential_issuer":"https://issuer.example",
            "credential_configuration_ids":["identity","other"],
            "grants":${if (authorization) """{"authorization_code":{}}""" else """{"urn:ietf:params:oauth:grant-type:pre-authorized_code":{"pre-authorized_code":"code","tx_code":{"input_mode":"numeric","length":3}}}"""}
        }"""), keyId = "default",
    )

    private class Fixture(val keys: InMemoryMobileWalletKeyStore, val dids: InMemoryDidStore) {
        lateinit var wallet: MobileWallet
        lateinit var reopen: () -> MobileWallet
        var created = 0
        var tokenCalls = 0
    }

    private suspend fun fixture(records: WalletIssuanceSessionStore? = null, afterGenerate: suspend (Int) -> Unit = {}): Fixture {
        val fixture = Fixture(InMemoryMobileWalletKeyStore(), InMemoryDidStore())
        suspend fun key(id: String) = CryptoRuntime(defaultSoftwareKeyProviders()).generateSoftwareKey(
            GenerateSoftwareKeyRequest(KeyId(id), KeySpec.Ec(EcCurve.P256), setOf(KeyUsage.SIGN, KeyUsage.VERIFY)),
        ).also { fixture.keys.addCrypto2Key(it) }
        key("default")
        val configuration = """{"format":"dc+sd-jwt","vct":"identity","cryptographic_binding_methods_supported":["jwk"],"proof_types_supported":{"jwt":{"proof_signing_alg_values_supported":["ES256"]}}}"""
        val client = HttpClient(MockEngine) {
            engine { addHandler { request ->
                val (body, status) = when (request.url.encodedPath) {
                    "/.well-known/openid-credential-issuer" -> """{"credential_issuer":"https://issuer.example","credential_endpoint":"https://issuer.example/credential","batch_credential_issuance":{"batch_size":2},"credential_configurations_supported":{"identity":$configuration,"other":$configuration}}""" to HttpStatusCode.OK
                    "/.well-known/oauth-authorization-server" -> """{"issuer":"https://issuer.example","token_endpoint":"https://issuer.example/token","authorization_endpoint":"https://issuer.example/authorize","response_types_supported":["code"],"authorization_details_types_supported":["openid_credential"]}""" to HttpStatusCode.OK
                    "/token" -> {
                        fixture.tokenCalls++
                        """{"error":"invalid_grant"}""" to HttpStatusCode.BadRequest
                    }
                    else -> error("Unexpected issuer request: ${request.url}")
                }
                respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
            } }
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        fixture.reopen = { MobileWallet(
            walletId = "ownership", keyStore = fixture.keys, didStore = fixture.dids,
            credentialStore = InMemoryCredentialStore(), issuanceHttpClient = client, issuanceSessionStore = records,
            generateAndPersistHolderKey = { _, _ -> key("generated-${++fixture.created}").also { afterGenerate(fixture.created) } },
        ) }
        fixture.wallet = fixture.reopen()
        return fixture
    }
}
