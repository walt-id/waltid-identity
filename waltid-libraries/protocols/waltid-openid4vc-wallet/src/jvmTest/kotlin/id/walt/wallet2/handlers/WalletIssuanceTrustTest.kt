package id.walt.wallet2.handlers

import id.walt.certificate.x509.X509CertificateUtil
import id.walt.cose.Cose
import id.walt.cose.CoseCertificate
import id.walt.cose.coseCompliantCbor
import id.walt.cose.toCoseKey
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.EcdsaSignatureEncoding
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.Key
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.toPublicJwk
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.did.dids.DidService
import id.walt.mdoc.issuance.MdocIssuer
import id.walt.mdoc.objects.document.Document
import id.walt.mdoc.objects.document.IssuerSigned
import id.walt.trust.decision.TrustDecisionEvaluator
import id.walt.trust.decision.TrustDecisionRequest
import id.walt.trust.decision.TrustPurpose
import id.walt.trust.decision.TrustSubject
import id.walt.trust.model.FreshnessState
import id.walt.trust.model.SourceAssurance
import id.walt.trust.model.TrustDecision
import id.walt.trust.model.TrustDecisionCode
import id.walt.trust.model.TrustedEntity
import id.walt.trust.model.TrustedEntityType
import id.walt.wallet2.data.WalletKeyStoreEntry
import id.walt.wallet2.trust.IssuerEntityTypeRule
import id.walt.wallet2.trust.IssuerNotTrustedException
import id.walt.wallet2.trust.WalletIssuanceTrust
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

/** WAL-1851: a wallet with issuance trust stores a received credential only if a registry trusts its signer. */
@OptIn(ExperimentalSerializationApi::class)
class WalletIssuanceTrustTest {

    private enum class Format { SD_JWT_VC, W3C_JWT, MDOC }

    // ---------------------------------------------------------------------------
    // Per format
    // ---------------------------------------------------------------------------

    @Test
    fun `a trusted issuer's credential is stored after the registry is asked about its signing chain`() = runTest {
        for (format in Format.entries) {
            val fixture = batchTestFixture(true)
            val issuer = issuer()
            val evaluator = RecordingEvaluator { trusted(TrustedEntityType.ATTESTATION_PROVIDER) }
            fixture.wallet.attachIssuanceTrust(WalletIssuanceTrust(evaluator))

            val stored = fixture.prepare(format, credential(format, issuer, fixture.keys.first()))

            assertEquals(1, stored.size, "$format")
            val request = evaluator.requests.single()
            assertEquals(TrustPurpose.WALLET_ISSUANCE_ISSUER_CERT, request.purpose)
            assertEquals(TrustSubject.CertificateChain(listOf(pem(issuer.certificate))), request.subject)
            assertEquals(setOf(TrustedEntityType.ATTESTATION_PROVIDER), request.expectedEntityTypes, "$format")
        }
    }

    @Test
    fun `a credential is rejected unless the decision is trusted, fresh and for the expected role`() = runTest {
        val rejected = mapOf(
            "NOT_TRUSTED" to TrustDecision(TrustDecisionCode.NOT_TRUSTED, FreshnessState.FRESH, accepted),
            "TRUSTED but STALE" to trusted(TrustedEntityType.ATTESTATION_PROVIDER).copy(sourceFreshness = FreshnessState.STALE),
            "STALE_SOURCE" to TrustDecision(TrustDecisionCode.STALE_SOURCE, FreshnessState.STALE, accepted),
            "UNKNOWN" to TrustDecision(TrustDecisionCode.UNKNOWN),
            "TRUSTED as another role" to trusted(TrustedEntityType.WALLET_PROVIDER),
            "TRUSTED from a source that is not accepted" to trusted(TrustedEntityType.ATTESTATION_PROVIDER)
                .copy(sourceAssurance = SourceAssurance(accepted = false)),
        )
        for (format in Format.entries) for ((name, decision) in rejected) {
            val fixture = batchTestFixture(true)
            val issuer = issuer()
            val evaluator = RecordingEvaluator { decision }
            fixture.wallet.attachIssuanceTrust(WalletIssuanceTrust(evaluator))

            val error = assertFailsWith<IssuerNotTrustedException>("$format, $name") {
                fixture.prepare(format, credential(format, issuer, fixture.keys.first()))
            }
            assertEquals(1, evaluator.requests.size)
            assertEquals(decision, error.decision)
            if (decision.decision == TrustDecisionCode.UNKNOWN) assertContains(error.message!!, "trust source gave no answer")
        }
    }

    @Test
    fun `a forged signature is rejected before the registry is asked`() = runTest {
        for (format in Format.entries) {
            val fixture = batchTestFixture(true)
            val evaluator = RecordingEvaluator { trusted(TrustedEntityType.ATTESTATION_PROVIDER) }
            fixture.wallet.attachIssuanceTrust(WalletIssuanceTrust(evaluator))
            val issuer = issuer()

            val error = assertFailsWith<IssuerNotTrustedException>("$format") {
                fixture.prepare(format, forged(format, issuer, fixture.keys.first()))
            }
            assertContains(error.message!!, "signature could not be verified")
            assertEquals(0, evaluator.requests.size, "$format")
        }
    }

    @Test
    fun `a credential signed by a key its certificate does not hold is rejected before the registry is asked`() = runTest {
        for (format in Format.entries) {
            val fixture = batchTestFixture(true)
            val evaluator = RecordingEvaluator { trusted(TrustedEntityType.ATTESTATION_PROVIDER) }
            fixture.wallet.attachIssuanceTrust(WalletIssuanceTrust(evaluator))
            val listed = issuer()
            val impostor = Issuer(generateKey("impostor"), listed.certificate)

            assertFailsWith<IssuerNotTrustedException>("$format") {
                fixture.prepare(format, credential(format, impostor, fixture.keys.first()))
            }
            assertEquals(0, evaluator.requests.size, "$format")
        }
    }

    @Test
    fun `an mdoc whose disclosed items do not match its MSO digests is rejected before the registry is asked`() = runTest {
        val fixture = batchTestFixture(true)
        val evaluator = RecordingEvaluator { trusted(TrustedEntityType.ATTESTATION_PROVIDER) }
        fixture.wallet.attachIssuanceTrust(WalletIssuanceTrust(evaluator))
        val issuer = issuer()
        val signed = mdocDocument(issuer, fixture.keys.first(), givenName = "Jane")
        val other = mdocDocument(issuer, fixture.keys.first(), givenName = "Mallory")
        val tampered = signed.copy(
            issuerSigned = IssuerSigned.fromIssuerSignedLists(
                namespaces = requireNotNull(other.issuerSigned.namespaces),
                issuerAuth = signed.issuerSigned.issuerAuth,
            )
        )

        val error = assertFailsWith<IssuerNotTrustedException> { fixture.prepare(Format.MDOC, encode(tampered)) }
        assertContains(error.message!!, "does not match its MSO")
        assertEquals(0, evaluator.requests.size)
    }

    // ---------------------------------------------------------------------------
    // Configuration
    // ---------------------------------------------------------------------------

    @Test
    fun `without issuance trust a credential is stored without asking anyone`() = runTest {
        for (format in Format.entries) {
            val fixture = batchTestFixture(true)
            fixture.wallet.attachIssuanceTrust { null }

            assertEquals(1, fixture.prepare(format, forged(format, issuer(), fixture.keys.first())).size, "$format")
        }
    }

    @Test
    fun `a DID-signed credential has no certificate chain and is rejected without asking the registry`() = runTest {
        DidService.minimalInit()
        val fixture = batchTestFixture(true)
        val evaluator = RecordingEvaluator { trusted(TrustedEntityType.ATTESTATION_PROVIDER) }
        fixture.wallet.attachIssuanceTrust(WalletIssuanceTrust(evaluator))
        val key = generateKey("did-issuer")
        val did = "did:jwk:" + base64Url.encode(publicJwk(key).toString().encodeToByteArray())
        val jwt = CompactJws.sign(
            payload = buildJsonObject {
                put("iss", did)
                put("vct", "https://example.org/vct/a")
            }.toString().encodeToByteArray(),
            key = key,
            algorithm = JwsAlgorithm.ES256,
            protectedHeader = buildJsonObject {
                put("typ", "dc+sd-jwt")
                put("kid", "$did#0")
            },
        )

        val error = assertFailsWith<IssuerNotTrustedException> { fixture.prepare(Format.SD_JWT_VC, "$jwt~") }
        assertContains(error.message!!, "not certified by an X.509 certificate chain")
        assertEquals(0, evaluator.requests.size)
    }

    @Test
    fun `a JWT without x5c is rejected before the key its issuer names is resolved`() = runTest {
        DidService.minimalInit()
        val key = generateKey("unresolvable-issuer")
        // Neither name resolves: resolving either would fail as an unverifiable signature, not as a missing chain.
        for (iss in listOf("did:web:issuer.invalid", "https://issuer.invalid")) {
            val fixture = batchTestFixture(true)
            val evaluator = RecordingEvaluator { trusted(TrustedEntityType.ATTESTATION_PROVIDER) }
            fixture.wallet.attachIssuanceTrust(WalletIssuanceTrust(evaluator))
            val jwt = CompactJws.sign(
                payload = buildJsonObject {
                    put("iss", iss)
                    put("vct", "https://example.org/vct/a")
                }.toString().encodeToByteArray(),
                key = key,
                algorithm = JwsAlgorithm.ES256,
                protectedHeader = buildJsonObject {
                    put("typ", "dc+sd-jwt")
                    put("kid", "$iss#0")
                },
            )

            val error = assertFailsWith<IssuerNotTrustedException>(iss) { fixture.prepare(Format.SD_JWT_VC, "$jwt~") }
            assertContains(error.message!!, "not certified by an X.509 certificate chain", message = iss)
            assertEquals(0, evaluator.requests.size, iss)
        }
    }

    @Test
    fun `an SD-JWT VC whose vct is not a string is rejected as untrusted`() = runTest {
        val fixture = batchTestFixture(true)
        val evaluator = RecordingEvaluator { trusted(TrustedEntityType.ATTESTATION_PROVIDER) }
        fixture.wallet.attachIssuanceTrust(WalletIssuanceTrust(evaluator))
        val issuer = issuer()
        val jwt = CompactJws.sign(
            payload = buildJsonObject {
                put("iss", BATCH_TEST_ISSUER)
                putJsonObject("vct") { put("a", 1) }
            }.toString().encodeToByteArray(),
            key = issuer.key,
            algorithm = JwsAlgorithm.ES256,
            protectedHeader = buildJsonObject {
                put("typ", "dc+sd-jwt")
                putJsonArray("x5c") { add(Base64.encode(issuer.certificate)) }
            },
        )

        assertFailsWith<IssuerNotTrustedException> { fixture.prepare(Format.SD_JWT_VC, "$jwt~") }
        assertEquals(0, evaluator.requests.size)
    }

    @Test
    fun `a credential type no rule names is rejected when no other role is configured`() = runTest {
        for (format in Format.entries) {
            val fixture = batchTestFixture(true)
            val evaluator = RecordingEvaluator { trusted(TrustedEntityType.ATTESTATION_PROVIDER) }
            fixture.wallet.attachIssuanceTrust(WalletIssuanceTrust(evaluator, otherCredentialTypes = emptySet()))

            val error = assertFailsWith<IssuerNotTrustedException>("$format") {
                fixture.prepare(format, credential(format, issuer(), fixture.keys.first()))
            }
            assertContains(error.message!!, "No trusted issuer role is configured")
            assertEquals(0, evaluator.requests.size)
        }
    }

    @Test
    fun `a PID must come from a PID provider, by its signed vct or doctype`() = runTest {
        for ((format, type) in listOf(Format.SD_JWT_VC to "urn:eudi:pid:1", Format.MDOC to "eu.europa.ec.eudi.pid.1")) {
            for (role in listOf(TrustedEntityType.PID_PROVIDER, TrustedEntityType.ATTESTATION_PROVIDER)) {
                val fixture = batchTestFixture(true)
                val evaluator = RecordingEvaluator { trusted(role) }
                fixture.wallet.attachIssuanceTrust(WalletIssuanceTrust(evaluator))
                val raw = credential(format, issuer(), fixture.keys.first(), type = type)

                if (role == TrustedEntityType.PID_PROVIDER) assertEquals(1, fixture.prepare(format, raw).size)
                else assertFailsWith<IssuerNotTrustedException>("$format, $role") { fixture.prepare(format, raw) }
                assertEquals(setOf(TrustedEntityType.PID_PROVIDER), evaluator.requests.single().expectedEntityTypes)
            }
        }
    }

    @Test
    fun `configured rules map a type to their roles and leave others to the default`() = runTest {
        val rule = IssuerEntityTypeRule(vcts = setOf("https://example.org/vct/diploma"), entityTypes = setOf(TrustedEntityType.OTHER))
        val trust = WalletIssuanceTrust({ error("not called") }, credentialTypes = listOf(rule))

        assertEquals(setOf(TrustedEntityType.OTHER), trust.entityTypesFor("https://example.org/vct/diploma", null))
        assertEquals(setOf(TrustedEntityType.ATTESTATION_PROVIDER), trust.entityTypesFor("urn:eudi:pid:1", null))
        assertEquals(setOf(TrustedEntityType.ATTESTATION_PROVIDER), trust.entityTypesFor("https://example.org/vct/DIPLOMA", null))
        assertFailsWith<IllegalArgumentException> { IssuerEntityTypeRule(vcts = setOf("x"), entityTypes = emptySet()) }
        assertFailsWith<IllegalArgumentException> { IssuerEntityTypeRule(entityTypes = setOf(TrustedEntityType.OTHER)) }
    }

    @Test
    fun `an evaluator or configuration that fails rejects the credential`() = runTest {
        val fixture = batchTestFixture(true)
        val raw = credential(Format.SD_JWT_VC, issuer(), fixture.keys.first())

        fixture.wallet.attachIssuanceTrust(WalletIssuanceTrust({ throw IllegalStateException("No trust registry is linked") }))
        assertContains(assertFailsWith<IssuerNotTrustedException> { fixture.prepare(Format.SD_JWT_VC, raw) }.message!!, "No trust registry is linked")

        fixture.wallet.attachIssuanceTrust { throw IllegalStateException("Configuration unavailable") }
        assertContains(assertFailsWith<IssuerNotTrustedException> { fixture.prepare(Format.SD_JWT_VC, raw) }.message!!, "Configuration unavailable")
    }

    // ---------------------------------------------------------------------------
    // Session service: nothing is stored
    // ---------------------------------------------------------------------------

    @Test
    fun `a response from an untrusted issuer stores nothing and fails with ISSUER_NOT_TRUSTED`() = runTest {
        for (trustedIssuer in listOf(true, false)) {
            val fixture = batchTestFixture(true)
            val issuer = issuer()
            val evaluator = RecordingEvaluator {
                if (trustedIssuer) trusted(TrustedEntityType.ATTESTATION_PROVIDER) else TrustDecision(TrustDecisionCode.NOT_TRUSTED, FreshnessState.FRESH, accepted)
            }
            fixture.wallet.attachIssuanceTrust(WalletIssuanceTrust(evaluator))
            val http = batchTestClient(credential = { request -> response(request.batchProofs().map { sdJwt(issuer, it.proofJwk()) }) })
            val service = WalletIssuanceSessionService(fixture.wallet, httpClient = http)

            val review = service.start(WalletIssuanceSessionRequest(offerJson = batchTestOffer()))
            val outcome = service.continuePreAuthorized(review.id, null, listOf(fixture.selection(2)))

            if (trustedIssuer) {
                assertIs<WalletIssuanceOutcome.Stored>(outcome)
                assertEquals(2, fixture.store.listCredentials().toList().size)
            } else {
                assertEquals(WalletIssuanceErrorCode.ISSUER_NOT_TRUSTED, assertIs<WalletIssuanceOutcome.Failed>(outcome).error.code)
                assertTrue(fixture.store.listCredentials().toList().isEmpty())
            }
            // The first rejected credential ends the check of the response.
            assertEquals(if (trustedIssuer) 2 else 1, evaluator.requests.size)
            service.closeSessions()
        }
    }

    @Test
    fun `the receive result tells a refused issuer from a trust source that gave no answer`() = runTest {
        val cases = listOf<Pair<(TrustDecisionRequest) -> TrustDecision, CredentialIssuanceFailureReason>>(
            { _: TrustDecisionRequest -> TrustDecision(TrustDecisionCode.NOT_TRUSTED, FreshnessState.FRESH, accepted) } to CredentialIssuanceFailureReason.ISSUER_NOT_TRUSTED,
            { _: TrustDecisionRequest -> TrustDecision(TrustDecisionCode.UNKNOWN, FreshnessState.FRESH, accepted) } to CredentialIssuanceFailureReason.ISSUER_TRUST_UNAVAILABLE,
            { _: TrustDecisionRequest -> error("Trust registry unreachable") } to CredentialIssuanceFailureReason.ISSUER_TRUST_UNAVAILABLE,
        )
        for ((answer, reason) in cases) {
            val fixture = batchTestFixture(true)
            val issuer = issuer()
            fixture.wallet.attachIssuanceTrust(WalletIssuanceTrust(RecordingEvaluator(answer)))
            val http = batchTestClient(credential = { request -> response(request.batchProofs().map { sdJwt(issuer, it.proofJwk()) }) })

            val result = WalletIssuanceHandler.receiveCredentials(fixture.wallet,
                ReceiveCredentialRequest(offerJson = batchTestOffer(), credentials = listOf(fixture.selection(2))), httpClient = http)

            val failure = assertNotNull(result.failure)
            assertEquals(CredentialIssuanceStage.RESPONSE, failure.stage)
            assertEquals(reason, failure.reason)
            assertTrue(result.credentialIds.isEmpty())
            assertTrue(fixture.store.listCredentials().toList().isEmpty())
        }
    }

    @Test
    fun `one forged credential in a batch rejects the whole response`() = runTest {
        val fixture = batchTestFixture(true)
        val issuer = issuer()
        val evaluator = RecordingEvaluator { trusted(TrustedEntityType.ATTESTATION_PROVIDER) }
        fixture.wallet.attachIssuanceTrust(WalletIssuanceTrust(evaluator))
        val http = batchTestClient(credential = { request ->
            val (first, second) = request.batchProofs().map { it.proofJwk() }
            response(listOf(sdJwt(issuer, first), forgeJwt(sdJwt(issuer, second))))
        })
        val service = WalletIssuanceSessionService(fixture.wallet, httpClient = http)

        val review = service.start(WalletIssuanceSessionRequest(offerJson = batchTestOffer()))
        val outcome = service.continuePreAuthorized(review.id, null, listOf(fixture.selection(2)))

        assertEquals(WalletIssuanceErrorCode.ISSUER_NOT_TRUSTED, assertIs<WalletIssuanceOutcome.Failed>(outcome).error.code)
        assertTrue(fixture.store.listCredentials().toList().isEmpty())
        service.closeSessions()
    }

    @Test
    fun `a deferred credential from an untrusted issuer is checked when it arrives, stores nothing and ends the continuation`() = runTest {
        val fixture = batchTestFixture(true)
        val issuer = issuer()
        val evaluator = RecordingEvaluator { TrustDecision(TrustDecisionCode.NOT_TRUSTED, FreshnessState.FRESH, accepted) }
        fixture.wallet.attachIssuanceTrust(WalletIssuanceTrust(evaluator))
        var proofs = emptyList<String>()
        var current = Clock.System.now()
        val notifications = mutableListOf<JsonObject>()
        val http = batchTestClient(
            metadata = batchTestMetadata(notificationEndpoint = true),
            credentialStatus = HttpStatusCode.Accepted,
            credential = { proofs = it.batchProofs(); """{"transaction_id":"untrusted","interval":1}""" },
            deferred = { response(proofs.map { sdJwt(issuer, it.proofJwk()) }, notificationId = "deferred-notification") },
            notification = { notifications += it },
        )
        val service = WalletIssuanceSessionService(fixture.wallet, httpClient = http, now = { current })

        val review = service.start(WalletIssuanceSessionRequest(offerJson = batchTestOffer()))
        val pending = assertIs<WalletIssuanceOutcome.Deferred>(service.continuePreAuthorized(review.id)).credentials.single()
        assertEquals(0, evaluator.requests.size)
        current += 2.seconds
        val outcome = assertIs<WalletIssuanceOutcome.Failed>(service.resumeDeferred(pending.id))

        assertEquals(WalletIssuanceErrorCode.ISSUER_NOT_TRUSTED, outcome.error.code)
        assertTrue(fixture.store.listCredentials().toList().isEmpty())
        assertEquals(1, evaluator.requests.size)
        // The issuer learns of the refusal, and the spent transaction is no longer offered for polling.
        val notification = notifications.single()
        assertEquals("deferred-notification", notification["notification_id"]?.jsonPrimitive?.content)
        assertEquals("credential_failure", notification["event"]?.jsonPrimitive?.content)
        assertTrue(outcome.deferredCredentials.isEmpty())
        assertTrue(service.listIssuanceContinuations().isEmpty())
        assertEquals(WalletIssuanceErrorCode.INVALID_SESSION,
            assertIs<WalletIssuanceOutcome.Failed>(service.resumeDeferred(pending.id)).error.code)
        service.closeSessions()
    }

    // ---------------------------------------------------------------------------
    // Fixtures
    // ---------------------------------------------------------------------------

    private class Issuer(val key: Key, val certificate: ByteArray)

    private class RecordingEvaluator(val answer: (TrustDecisionRequest) -> TrustDecision) : TrustDecisionEvaluator {
        val requests = mutableListOf<TrustDecisionRequest>()
        override suspend fun evaluate(request: TrustDecisionRequest): TrustDecision {
            requests += request
            return answer(request)
        }
    }

    private val accepted = SourceAssurance(accepted = true)

    private fun trusted(role: TrustedEntityType) = TrustDecision(
        decision = TrustDecisionCode.TRUSTED,
        sourceFreshness = FreshnessState.FRESH,
        sourceAssurance = accepted,
        matchedEntity = TrustedEntity(entityId = "issuer", sourceId = "lote", entityType = role, legalName = "Test issuer"),
    )

    private val runtime = CryptoRuntime(defaultSoftwareKeyProviders())
    private val base64Url = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)

    private suspend fun generateKey(id: String): Key = runtime.generateSoftwareKey(
        GenerateSoftwareKeyRequest(KeyId(id), KeySpec.Ec(EcCurve.P256), setOf(KeyUsage.SIGN, KeyUsage.VERIFY))
    )

    private suspend fun issuer(): Issuer {
        val key = generateKey("issuer")
        val certificate = X509CertificateUtil.createSelfSignedCertificate(
            key, SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256, EcdsaSignatureEncoding.DER),
        ) { subjectDn = "CN=Test issuer" }
        return Issuer(key, certificate.encodedDer.toByteArray())
    }

    private suspend fun publicJwk(key: Key): JsonObject = Json.parseToJsonElement(
        key.capabilities.publicKeyExporter!!.exportPublicKey().toPublicJwk(key.spec).data.toByteArray().decodeToString()
    ).jsonObject

    private suspend fun WalletKeyStoreEntry.publicJwk(): JsonObject = publicJwk(requireNotNull(crypto2Key))

    private fun String.proofJwk(): JsonObject = CompactJws.decodeUnverified(this).protectedHeader.getValue("jwk").jsonObject

    private fun pem(der: ByteArray): String = buildString {
        appendLine("-----BEGIN CERTIFICATE-----")
        Base64.encode(der).chunked(64).forEach { appendLine(it) }
        appendLine("-----END CERTIFICATE-----")
    }

    private fun response(credentials: List<String>, notificationId: String? = null): String = buildJsonObject {
        put("credentials", JsonArray(credentials.map { buildJsonObject { put("credential", it) } }))
        notificationId?.let { put("notification_id", it) }
    }.toString()

    /** Prepares [raw] as the wallet does before storing it: holder-bound for an mdoc, proofless otherwise. */
    private suspend fun BatchTestFixture.prepare(format: Format, raw: String) = wallet.prepareIssuedCredentials(
        rawCredentials = listOf(raw),
        bindings = if (format == Format.MDOC) listOf(ResolvedCredentialHolderBinding(keys.first(), null)) else emptyList(),
        label = null,
        metadata = null,
        proofRequired = format == Format.MDOC,
    )

    private suspend fun credential(format: Format, issuer: Issuer, holder: WalletKeyStoreEntry, type: String? = null): String =
        when (format) {
            Format.SD_JWT_VC -> sdJwt(issuer, holderJwk = null, vct = type ?: "https://example.org/vct/a")
            Format.W3C_JWT -> w3cJwt(issuer)
            Format.MDOC -> encode(mdocDocument(issuer, holder, docType = type ?: "org.example.mdoc"))
        }

    private suspend fun forged(format: Format, issuer: Issuer, holder: WalletKeyStoreEntry): String = when (format) {
        Format.SD_JWT_VC, Format.W3C_JWT -> forgeJwt(credential(format, issuer, holder))
        Format.MDOC -> {
            val document = mdocDocument(issuer, holder)
            val issuerAuth = document.issuerSigned.issuerAuth
            val signature = issuerAuth.signature.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }
            encode(document.copy(issuerSigned = IssuerSigned.fromIssuerSignedLists(
                namespaces = requireNotNull(document.issuerSigned.namespaces),
                issuerAuth = issuerAuth.copy(signature = signature),
            )))
        }
    }

    private fun forgeJwt(raw: String): String {
        val jwt = raw.substringBefore('~')
        val (header, payload, signature) = jwt.split('.')
        val forged = base64Url.decode(signature).also { it[0] = (it[0].toInt() xor 1).toByte() }
        return "$header.$payload.${base64Url.encode(forged)}" + raw.removePrefix(jwt)
    }

    private suspend fun sdJwt(issuer: Issuer, holderJwk: JsonObject?, vct: String = "identity"): String = CompactJws.sign(
        payload = buildJsonObject {
            put("iss", BATCH_TEST_ISSUER)
            put("vct", vct)
            put("iat", 1700000000)
            put("given_name", "Ada")
            if (holderJwk != null) putJsonObject("cnf") { put("jwk", holderJwk) }
        }.toString().encodeToByteArray(),
        key = issuer.key,
        algorithm = JwsAlgorithm.ES256,
        protectedHeader = buildJsonObject {
            put("typ", "dc+sd-jwt")
            putJsonArray("x5c") { add(Base64.encode(issuer.certificate)) }
        },
    ) + "~"

    private suspend fun w3cJwt(issuer: Issuer): String = CompactJws.sign(
        payload = buildJsonObject {
            put("iss", BATCH_TEST_ISSUER)
            putJsonObject("vc") {
                putJsonArray("@context") { add("https://www.w3.org/2018/credentials/v1") }
                putJsonArray("type") { add("VerifiableCredential"); add("UniversityDegree") }
                putJsonObject("credentialSubject") { put("degree", "BSc") }
            }
        }.toString().encodeToByteArray(),
        key = issuer.key,
        algorithm = JwsAlgorithm.ES256,
        protectedHeader = buildJsonObject {
            put("typ", "JWT")
            putJsonArray("x5c") { add(Base64.encode(issuer.certificate)) }
        },
    )

    private suspend fun mdocDocument(
        issuer: Issuer,
        holder: WalletKeyStoreEntry,
        docType: String = "org.example.mdoc",
        givenName: String = "Jane",
    ): Document {
        val namespace = docType.substringBeforeLast('.').ifEmpty { docType }
        val issuerSigned = MdocIssuer.issueUniversal(
            issuerKey = issuer.key,
            signatureAlgorithm = Cose.Algorithm.ES256,
            issuerCertificate = listOf(CoseCertificate(issuer.certificate)),
            holderKey = requireNotNull(holder.crypto2Key).let {
                it.capabilities.publicKeyExporter!!.exportPublicKey().toPublicJwk(it.spec).toCoseKey()
            },
            docType = docType,
            data = MdocIssuer.MdocUniversalIssuanceData(
                namespaces = mapOf(namespace to buildJsonObject { put("given_name", givenName) })
            ),
        )
        return Document(docType = docType, issuerSigned = issuerSigned)
    }

    private fun encode(document: Document): String = base64Url.encode(coseCompliantCbor.encodeToByteArray(document))
}
