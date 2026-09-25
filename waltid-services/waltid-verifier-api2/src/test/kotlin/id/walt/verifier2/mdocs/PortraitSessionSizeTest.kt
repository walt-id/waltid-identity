package id.walt.verifier2.mdocs

import id.walt.commons.config.ConfigManager
import id.walt.commons.testing.E2ETest
import id.walt.verifier2.freePort
import id.walt.cose.Cose
import id.walt.cose.CoseCertificate
import id.walt.cose.coseCompliantCbor
import id.walt.cose.toCoseKey
import id.walt.credentials.CredentialParser
import id.walt.credentials.formats.MdocsCredential
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.EcdsaSignatureEncoding
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.certificate.x509.X509CertificateUtil
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.dcql.DcqlMatcher
import id.walt.dcql.RawDcqlCredential
import id.walt.dcql.models.ClaimsQuery
import id.walt.dcql.models.CredentialFormat
import id.walt.dcql.models.CredentialQuery
import id.walt.dcql.models.DcqlQuery
import id.walt.dcql.models.meta.MsoMdocMeta
import id.walt.did.dids.DidService
import id.walt.did.dids.resolver.LocalResolver
import id.walt.mdoc.issuance.MdocIssuer
import id.walt.mdoc.objects.document.Document
import id.walt.verifier.openid.transactiondata.TransactionDataTypeRegistry
import id.walt.verifier2.OSSVerifier2FeatureCatalog
import id.walt.verifier2.OSSVerifier2ServiceConfig
import id.walt.verifier2.Verifier2Service
import id.walt.verifier2.data.CrossDeviceFlowSetup
import id.walt.verifier2.data.GeneralFlowConfig
import id.walt.verifier2.data.Verification2Session
import id.walt.verifier2.data.VerificationSessionSetup
import id.walt.verifier2.handlers.sessioncreation.VerificationSessionCreationResponse
import id.walt.verifier2.verifierModule
import id.waltid.openid4vp.wallet.WalletPresentFunctionality2
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.server.application.Application
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.io.encoding.Base64
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * A verification session carrying a portrait must stay inside the document limit of a persistent store.
 *
 * A load-test arm presenting an mDL with a 250 KB portrait failed every request with HTTP 500:
 * `BsonMaximumSizeExceededException: Payload document size is larger than maximum of 16793600`. Portrait
 * presentations are therefore impossible on any MongoDB or DocumentDB deployment, which is every Enterprise
 * deployment - a harder failure than the OutOfMemoryError this credential shape caused before, because no amount
 * of heap helps.
 *
 * Reproduced here rather than in the cluster because each remote arm costs forty minutes and needs a licence, a
 * DocumentDB instance and twelve nodes to tell us one number. This runs in seconds against the in-memory store
 * and measures what the persistent store would have been asked to write.
 *
 * The measurement is deliberately field by field: the first guess was that CBOR byte strings expand into arrays
 * of one number per byte, and measuring showed they do not - the parser keeps the portrait as base64 text. So the
 * size has to come from how many times the presentation is retained, which is what this test exposes.
 */
class PortraitSessionSizeTest {

    /** MongoDB's hard limit on a single document. DocumentDB inherits it. */
    private val bsonDocumentLimit = 16_793_600

    private val portraitBytes = 250_000

    @Test
    fun `a session holding a portrait presentation stays within the document limit`() {
        val host = "127.0.0.1"
        val port = freePort()
        val holder = runBlocking { portraitHolder() }

        E2ETest(host, port, true).testBlock(
            features = listOf(OSSVerifier2FeatureCatalog),
            preload = {
                ConfigManager.preloadConfig(
                    "verifier-service", OSSVerifier2ServiceConfig(
                        clientId = null,
                        urlPrefix = "http://$host:$port/verification-session",
                        urlHost = "openid4vp://authorize",
                    )
                )
            },
            init = {
                DidService.apply {
                    registerResolver(LocalResolver())
                    updateResolversForMethods()
                }
            },
            module = Application::verifierModule,
        ) {
            val http = testHttpClient()

            val created = testAndReturn("Create verification session") {
                http.post("/verification-session/create") {
                    setBody(portraitSessionSetup())
                }.body<VerificationSessionCreationResponse>()
            }

            testAndReturn("Present the portrait credential") {
                WalletPresentFunctionality2.walletPresentHandling(
                    holderKey = holder.key,
                    holderDid = null,
                    presentationRequestUrl = created.bootstrapAuthorizationRequestUrl!!,
                    selectCredentialsForQuery = { query -> matchFor(query, holder) },
                    holderPoliciesToRun = null,
                    runPolicies = null,
                    transactionDataTypeRegistry = TransactionDataTypeRegistry(emptySet()),
                    mdocHolderKeyResolver = { _, _ -> holder.key },
                )
            }

            test("The stored session fits in a BSON document") {
                val stored = runBlocking {
                    Verifier2Service.defaultSessionRepository.get(created.sessionId)
                }
                val session = assertNotNull(stored, "the session must still be stored").session
                val encoded = Json.encodeToJsonElement(Verification2Session.serializer(), session) as JsonObject

                // Printed rather than only asserted: which field holds the copies is the actionable part, and a
                // bare "too large" would send the next reader back to measuring.
                // 6.7 copies of the encoded portrait were retained when this was written: the raw device response,
                // the parsed presentation, and the policy results, whose `results` map echoes the presented data.
                // One policy was configured here; the enterprise profiles run several, each echoing again, which is
                // how a 250 KB portrait reaches the 16 MB document limit in a real deployment.
                val encodedPortraitChars = portraitBytes * 4 / 3
                println(
                    "PORTRAIT total=${encoded.toString().length} limit=$bsonDocumentLimit portrait=$portraitBytes " +
                            "retainedCopies=${"%.1f".format(encoded.toString().length.toDouble() / encodedPortraitChars)}"
                )
                encoded.entries
                    .map { (key, value) -> key to value.toString().length }
                    .sortedByDescending { it.second }
                    .take(8)
                    .forEach { (key, size) -> println("PORTRAIT field=$key chars=$size") }

                // Which policy result holds the copies, and under which key: that is what has to become a
                // reference, and naming it saves the next reader the measurement.
                session.presentationValidationResults?.forEach { (queryId, byPolicy) ->
                    byPolicy.forEach { (policyName, run) ->
                        run.results.forEach { (key, value) ->
                            println("PORTRAIT policyResult query=$queryId policy=$policyName key=$key chars=${value.toString().length}")
                        }
                    }
                }

                // The regression that matters is not the absolute size but what scales with policy count. Policy
                // results must describe what was checked, not repeat it: one policy echoing the disclosed elements
                // cost a megabyte here, and an Enterprise profile runs several.
                val validationResultChars =
                    encoded["presentation_validation_results"]?.toString()?.length ?: 0
                assertTrue(
                    validationResultChars < encodedPortraitChars / 10,
                    "policy results hold $validationResultChars chars for a $encodedPortraitChars char portrait, " +
                            "so they are carrying the presented values rather than a reference to them",
                )

                // The same rule for the other results field, which was measured holding 3,228,057 bytes of a
                // 10,046,803-byte session against a deployed service - a third full copy of the credential
                // beside the two deliberate audit copies, because a credential policy's payload was kept
                // verbatim (see StoredPolicyResultBounds).
                //
                // Honest limitation: in this fixture the presentation fails on
                // mso_mdoc/transaction-data-hash-check before credential policies run, so policy_results is
                // empty and this assertion is currently vacuous - it guards against regression rather than
                // proving the bounding is wired in. The engine call site is therefore NOT covered by any
                // test; StoredPolicyResultBoundsTest covers the bounding function alone. Closing that gap
                // needs a fixture whose presentation passes validation.
                val policyResultChars = encoded["policy_results"]?.toString()?.length ?: 0
                assertTrue(
                    policyResultChars < encodedPortraitChars / 10,
                    "policy_results holds $policyResultChars chars for a $encodedPortraitChars char portrait, " +
                            "so credential policy payloads are repeating the credential rather than referencing it",
                )

                assertTrue(
                    encoded.toString().length < bsonDocumentLimit,
                    "a session with a ${portraitBytes / 1000} KB portrait serialises to " +
                            "${encoded.toString().length} chars, over the $bsonDocumentLimit document limit, " +
                            "so it cannot be persisted at all",
                )
            }
        }
    }

    /**
     * What a portrait costs in time, beside what it costs in bytes.
     *
     * Reported rather than asserted: a wall-clock number on a shared machine is not a threshold anybody should
     * gate a build on, but the ratio between the two credential shapes is what sizing a deployment needs, and
     * measuring it here takes seconds where a remote arm takes forty minutes.
     */
    @Test
    fun `measure the cost of a portrait against a minimal credential`() {
        val host = "127.0.0.1"
        val port = freePort()
        val minimal = runBlocking { portraitHolder(portraitBytes = 0) }
        val portrait = runBlocking { portraitHolder(portraitBytes = portraitBytes) }

        E2ETest(host, port, true).testBlock(
            features = listOf(OSSVerifier2FeatureCatalog),
            preload = {
                ConfigManager.preloadConfig(
                    "verifier-service", OSSVerifier2ServiceConfig(
                        clientId = null,
                        urlPrefix = "http://$host:$port/verification-session",
                        urlHost = "openid4vp://authorize",
                    )
                )
            },
            init = {
                DidService.apply {
                    registerResolver(LocalResolver())
                    updateResolversForMethods()
                }
            },
            module = Application::verifierModule,
        ) {
            val http = testHttpClient()
            val rounds = 20

            suspend fun presentOnce(holder: PortraitHolder) {
                val created = http.post("/verification-session/create") {
                    setBody(portraitSessionSetup(requestPortrait = holder.hasPortrait))
                }.body<VerificationSessionCreationResponse>()
                WalletPresentFunctionality2.walletPresentHandling(
                    holderKey = holder.key,
                    holderDid = null,
                    presentationRequestUrl = created.bootstrapAuthorizationRequestUrl!!,
                    selectCredentialsForQuery = { query -> matchFor(query, holder) },
                    holderPoliciesToRun = null,
                    runPolicies = null,
                    transactionDataTypeRegistry = TransactionDataTypeRegistry(emptySet()),
                    mdocHolderKeyResolver = { _, _ -> holder.key },
                )
            }

            suspend fun timeFor(holder: PortraitHolder, label: String): Double {
                // One untimed round first, so class loading and JIT land on the warm-up rather than the result.
                repeat(1 + rounds) { index ->
                    val created = http.post("/verification-session/create") {
                        setBody(portraitSessionSetup(requestPortrait = holder.hasPortrait))
                    }.body<VerificationSessionCreationResponse>()
                    val started = kotlin.time.TimeSource.Monotonic.markNow()
                    WalletPresentFunctionality2.walletPresentHandling(
                        holderKey = holder.key,
                        holderDid = null,
                        presentationRequestUrl = created.bootstrapAuthorizationRequestUrl!!,
                        selectCredentialsForQuery = { query -> matchFor(query, holder) },
                        holderPoliciesToRun = null,
                        runPolicies = null,
                        transactionDataTypeRegistry = TransactionDataTypeRegistry(emptySet()),
                        mdocHolderKeyResolver = { _, _ -> holder.key },
                    )
                    if (index > 0) elapsed[label] = (elapsed[label] ?: 0.0) + started.elapsedNow().inWholeMicroseconds
                }
                return (elapsed[label] ?: 0.0) / rounds / 1000.0
            }

            /**
             * Concurrent, because the inverse of a latency is not a throughput.
             *
             * Sequentially this reports about 40 sessions/s at 24.5ms each, which invites the conclusion that the
             * verifier does 40 sessions/s - it does not. A local reference run measured 533 to 715 sessions/s at
             * concurrency 25 to 400, and those are the same system: one in flight is a latency measurement.
             */
            suspend fun throughputFor(
                holder: PortraitHolder,
                concurrency: Int,
                total: Int,
                label: String,
            ): Double = coroutineScope {
                // Warm up before timing anything. 200 sessions at 150/s is 1.3 seconds, which measures class
                // loading and JIT rather than the verifier - the same error as the 8-second remote arms that made
                // a plateau look real this morning, at a smaller scale.
                val warmup = java.util.concurrent.atomic.AtomicInteger(0)
                List(concurrency) {
                    launch {
                        while (warmup.getAndIncrement() < concurrency * 4) presentOnce(holder)
                    }
                }.joinAll()

                val started = kotlin.time.TimeSource.Monotonic.markNow()
                val next = java.util.concurrent.atomic.AtomicInteger(0)
                List(concurrency) {
                    launch {
                        while (next.getAndIncrement() < total) presentOnce(holder)
                    }
                }.joinAll()
                val seconds = started.elapsedNow().inWholeMilliseconds / 1000.0
                println("THROUGHPUT_RUN $label sessions=$total seconds=${"%.1f".format(seconds)}")
                total / seconds
            }

            test("Compare a minimal credential with a portrait one") {
                val minimalMs = timeFor(minimal, "minimal")
                val portraitMs = timeFor(portrait, "portrait")
                println(
                    "LATENCY minimalMs=${"%.1f".format(minimalMs)} portraitMs=${"%.1f".format(portraitMs)} " +
                            "ratio=${"%.2f".format(portraitMs / minimalMs)}"
                )

                // Both credential shapes at the same concurrency, which is the comparison a deployment needs.
                // Sized so each phase runs for tens of seconds rather than one, at the ratio their costs imply.
                val concurrency = 32
                // 6,000 / 600 killed the Gradle daemon on a 24-core, memory-constrained machine: every
                // portrait session retains a 250 KB credential in-process, and the writes backed up to
                // 3.6 s each before the JVM died. A unit test must not be able to take the build down, and
                // these numbers are printed rather than asserted, so a shorter run costs nothing. Real
                // throughput comes from loadtest-harness/local5.sh against a deployed service.
                val minimalRate = throughputFor(minimal, concurrency, total = 600, label = "minimal")
                val portraitRate = throughputFor(portrait, concurrency, total = 60, label = "portrait")
                println(
                    "THROUGHPUT concurrency=$concurrency " +
                            "minimalPerSecond=${"%.1f".format(minimalRate)} " +
                            "portraitPerSecond=${"%.1f".format(portraitRate)} " +
                            "ratio=${"%.2f".format(minimalRate / portraitRate)}"
                )
            }
        }
    }

    private val elapsed = mutableMapOf<String, Double>()

    private data class PortraitHolder(
        val credential: MdocsCredential,
        val key: id.walt.crypto2.keys.Key,
        val hasPortrait: Boolean,
    )

    /** Issues an mDL whose portrait is the size a real one is, with a self-signed document signer. */
    private suspend fun portraitHolder(portraitBytes: Int = this.portraitBytes): PortraitHolder {
        val runtime = CryptoRuntime(defaultSoftwareKeyProviders())
        suspend fun key(id: String) = runtime.generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId(id),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            )
        )

        val holderKey = key("portrait-holder")
        val issuerKey = key("portrait-issuer")
        val certificate = X509CertificateUtil.createSelfSignedCertificate(
            issuerKey,
            SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256, EcdsaSignatureEncoding.DER),
        ) {
            subjectDn = "CN=portrait session size test"
        }
        val claims = buildJsonObject {
            put("family_name", JsonPrimitive("Portrait"))
            put("given_name", JsonPrimitive("Test"))
            put(
                "portrait",
                JsonPrimitive(
                    Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(Random.nextBytes(portraitBytes))
                ),
            )
        }
        val issuerSigned = MdocIssuer.issueUniversal(
            issuerKey = issuerKey,
            signatureAlgorithm = Cose.Algorithm.ES256,
            issuerCertificate = listOf(CoseCertificate(certificate.encodedDer.toByteArray())),
            holderKey = (holderKey.capabilities.publicKeyExporter!!.exportPublicKey() as EncodedKey.Jwk).toCoseKey(),
            docType = DOC_TYPE,
            data = MdocIssuer.MdocUniversalIssuanceData(namespaces = mapOf(NAMESPACE to claims)),
        )
        val raw = coseCompliantCbor.encodeToByteArray(
            Document.serializer(),
            Document(docType = DOC_TYPE, issuerSigned = issuerSigned),
        ).encodeToBase64Url()

        return PortraitHolder(
            CredentialParser.detectAndParse(raw).second as MdocsCredential,
            holderKey,
            hasPortrait = portraitBytes > 0,
        )
    }

    private fun matchFor(query: DcqlQuery, holder: PortraitHolder) = DcqlMatcher.match(
        query,
        listOf(
            RawDcqlCredential(
                id = "0",
                format = holder.credential.format,
                data = holder.credential.credentialData,
                originalCredential = holder.credential,
                disclosures = null,
            )
        ),
    ).getOrThrow()

    private fun portraitSessionSetup(requestPortrait: Boolean = true): VerificationSessionSetup = CrossDeviceFlowSetup(
        core = GeneralFlowConfig(
            dcqlQuery = DcqlQuery(
                credentials = listOf(
                    CredentialQuery(
                        id = "mdl",
                        format = CredentialFormat.MSO_MDOC,
                        meta = MsoMdocMeta(doctypeValue = DOC_TYPE),
                        claims = listOf(
                            ClaimsQuery(
                                path = listOf(NAMESPACE, if (requestPortrait) "portrait" else "family_name")
                                    .map { Json.parseToJsonElement("\"$it\"") }
                            ),
                        ),
                    )
                )
            ),
        )
    )

    private companion object {
        const val DOC_TYPE = "org.iso.18013.5.1.mDL"
        const val NAMESPACE = "org.iso.18013.5.1"
    }
}
