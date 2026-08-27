@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.mdoc.proximity

import id.walt.cose.CoseKey
import id.walt.cose.coseCompliantCbor
import id.walt.cose.toCoseKey
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.mdoc.crypto.MdocCryptoHelper
import id.walt.mdoc.encoding.ByteStringWrapper
import id.walt.mdoc.objects.SessionTranscript
import id.walt.mdoc.objects.deviceretrieval.DeviceRequest
import id.walt.mdoc.objects.deviceretrieval.DeviceResponse
import id.walt.mdoc.objects.deviceretrieval.ElementReference
import id.walt.mdoc.objects.engagement.DeviceEngagement
import id.walt.mdoc.objects.engagement.DeviceRetrievalMethod
import id.walt.mdoc.objects.engagement.NfcRetrievalOptions
import id.walt.mdoc.objects.session.SessionData
import id.walt.mdoc.objects.session.SessionEstablishment
import id.walt.mdoc.objects.session.SessionStatusCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.seconds

class HolderProtocolEngineTest {
    private val runtime = CryptoRuntime(defaultSoftwareKeyProviders())

    @Test
    fun `fake reader completes repeated signature and MAC response exchanges`() = realDispatcherTest {
        val deviceKey = agreementKey("engine-device")
        val readerKey = agreementKey("engine-reader")
        val signatureHolderKey = runtime.generateMdocTestKey(
            "engine-signature-holder",
            setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
        )
        val macHolderKey = runtime.generateMdocTestKey(
            "engine-mac-holder",
            setOf(KeyUsage.KEY_AGREEMENT),
        )
        val signatureSourceDocument = runtime.issueMdocTestDocument(signatureHolderKey)
        val macSourceDocument = runtime.issueMdocTestDocument(macHolderKey)
        val loopback = FakeProximityLoopback.create()
        val method = DeviceRetrievalMethod(1u, 1u, NfcRetrievalOptions(1_024u, 1_024u))
        val engagementContext = EngagementContext("iso-full", 1_048_576, EngagementType.QR)
        val capabilities = MdocHolderProtocolCapabilities(
            handoverSessionEstablishment = false,
            readerAuthAll = false,
            extendedRequests = true,
        )
        val readerSession = readerSession(deviceKey, readerKey, method, engagementContext, capabilities)
        val firstEstablishment = ImmutableBytes.of(
            coseCompliantCbor.encodeToByteArray(
                SessionEstablishment(
                    ByteStringWrapper(readerSession.readerCose, readerSession.readerCoseBytes),
                    readerSession.cipher.encrypt(encodeRequest(signatureSourceDocument.docType)),
                )
            )
        )
        val secondRequest = ImmutableBytes.of(
            coseCompliantCbor.encodeToByteArray(
                SessionData(data = readerSession.cipher.encrypt(encodeRequest(macSourceDocument.docType)))
            )
        )
        val termination = ImmutableBytes.of(
            coseCompliantCbor.encodeToByteArray(
                SessionData(status = SessionStatusCode.SESSION_TERMINATION.code)
            )
        )
        var resolved = 0
        val engine = MdocHolderProtocolEngine(
            eDeviceKey = deviceKey,
            transportProviders = listOf(FakeTransportProvider(method, loopback.holder)),
            requestProcessor = object : MdocHolderRequestProcessor {
                override suspend fun preview(context: MdocHolderRequestContext) = preview(context.request)
                override suspend fun resolve(
                    context: MdocHolderRequestContext,
                    preview: MdocRequestPreview,
                ): MdocResponseResolution {
                    val (source, holderKey, authentication) = if (resolved++ == 0) {
                        Triple(
                            signatureSourceDocument,
                            signatureHolderKey,
                            MdocAuthenticationMethod.Signature(),
                        )
                    } else {
                        Triple(
                            macSourceDocument,
                            macHolderKey,
                            MdocAuthenticationMethod.Mac(readerSession.readerCose),
                        )
                    }
                    val response = MdocResponseBuilder().buildResponse(
                        presentations = listOf(
                            MdocDocumentPresentation(
                                source = source,
                                holderKey = holderKey,
                                selectedIssuerElements = setOf(ElementReference("org.example", "given_name")),
                                authentication = authentication,
                            )
                        ),
                        transcript = context.transcript,
                    )
                    return MdocResponseResolution(
                        ImmutableBytes.of(coseCompliantCbor.encodeToByteArray(response)),
                        continueSession = true,
                        submissionBindingDigest = preview.submissionBindingDigest,
                    )
                }
            },
            consentHandler = MdocConsentHandler { MdocConsentDecision.Approve(it.bindingToken) },
            engagementContext = engagementContext,
            capabilities = capabilities,
        )
        val reader = async {
            val qrPayload = requireNotNull(
                engine.state.filterIsInstance<MdocHolderSessionState.Connecting>().first().qrPayload
            )
            val engagementBytes = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
                .decode(qrPayload.removePrefix("mdoc:"))
            assertContentEquals(readerSession.engagementBytes, engagementBytes)
            val engagement = coseCompliantCbor.decodeFromByteArray<DeviceEngagement>(engagementBytes)
            assertEquals(emptyList(), engagement.originInfos)
            assertEquals(true, engagement.capabilities?.extendedRequests)
            assertFalse(engagement.capabilities!!.readerAuthAll)

            loopback.reader.send(firstEstablishment)
            loopback.reader.send(secondRequest)
            loopback.reader.send(termination)
            val signatureResponse = assertResponse(loopback.reader, readerSession.cipher, engine)
            val macResponse = assertResponse(loopback.reader, readerSession.cipher, engine)
            assertNotNull(signatureResponse.documents!!.single().deviceSigned!!.deviceAuth.deviceSignature)
            assertNotNull(macResponse.documents!!.single().deviceSigned!!.deviceAuth.deviceMac)
            readerSession.cipher.close()
        }

        val result = engine.run()
        reader.await()

        assertEquals(2, assertIs<MdocHolderSessionResult.Completed>(result).exchanges)
        assertEquals(2, resolved)
        assertFailsWith<IllegalStateException> { engine.run() }
    }

    @Test
    fun `stale consent and changed submission bindings fail closed before response`() = realDispatcherTest {
        val stale = runRejectedSession(
            consent = { MdocConsentDecision.Approve(ImmutableBytes.of(ByteArray(32))) },
        )
        assertEquals("stale_consent", stale.error.code)
        assertEquals(0, stale.resolveCalls)

        val changed = runRejectedSession(
            resolutionBinding = ImmutableBytes.of(ByteArray(32) { 0x7f }),
        )
        assertEquals("changed_submission", changed.error.code)
        assertEquals(1, changed.resolveCalls)
    }

    @Test
    fun `plaintext request limit is independent from the larger session-message limit`() = realDispatcherTest {
        val rejected = runRejectedSession(
            limits = MdocProximityLimits(maximumRequestBytes = 32),
        )
        assertEquals("request_too_large", rejected.error.code)
        assertEquals(0, rejected.resolveCalls)
    }

    @Test
    fun `a denied consent decision cannot produce response data`() = realDispatcherTest {
        val denied = runRejectedSession(
            consent = { MdocConsentDecision.Deny(it.bindingToken) },
        )

        assertEquals(1, assertIs<MdocHolderSessionResult.Declined>(denied.result).exchange)
        assertEquals(0, denied.resolveCalls)
    }

    @Test
    fun `consent timeout and transport profile size limits fail with stable errors`() = realDispatcherTest {
        val timedOut = runRejectedSession(
            consent = { awaitCancellation() },
            timeouts = MdocProximityTimeouts(consent = 1.seconds),
        )
        assertEquals("consent_timeout", timedOut.error.code)
        assertEquals(0, timedOut.resolveCalls)

        val tooLarge = runRejectedSession(maximumTransportMessageBytes = 32)
        assertEquals("transport_message_limit", tooLarge.error.code)
        assertEquals(0, tooLarge.resolveCalls)
    }

    @Test
    fun `total session timeout is not misreported as the active phase timeout`() = realDispatcherTest {
        val timedOut = runRejectedSession(
            consent = { awaitCancellation() },
            timeouts = MdocProximityTimeouts(
                consent = 5.seconds,
                totalSession = 1.seconds,
            ),
        )

        assertEquals("session_timeout", timedOut.error.code)
        assertEquals(0, timedOut.resolveCalls)
    }

    /** WebCrypto promises must use real scheduling instead of test-scheduler virtual time. */
    private fun realDispatcherTest(block: suspend CoroutineScope.() -> Unit) = runTest {
        withContext(Dispatchers.Default, block)
    }

    private suspend fun assertResponse(
        connection: ProximityConnection,
        cipher: MdocSessionCipher,
        engine: MdocHolderProtocolEngine,
    ): DeviceResponse {
        val bytes = withTimeoutOrNull(5.seconds) { connection.receive() }
            ?: error("No response from holder; state=${engine.state.value}")
        val responseMessage = coseCompliantCbor.decodeFromByteArray<SessionData>(bytes.copy())
        val response = coseCompliantCbor.decodeFromByteArray<DeviceResponse>(cipher.decrypt(responseMessage.data!!))
        assertEquals(0u, response.status)
        return response
    }

    private data class RejectedSession(val result: MdocHolderSessionResult, val resolveCalls: Int) {
        val error: ProximityError get() = assertIs<MdocHolderSessionResult.Failed>(result).error
    }

    private suspend fun CoroutineScope.runRejectedSession(
        consent: suspend (MdocConsentPrompt) -> MdocConsentDecision = { MdocConsentDecision.Approve(it.bindingToken) },
        resolutionBinding: ImmutableBytes? = null,
        limits: MdocProximityLimits = MdocProximityLimits(),
        timeouts: MdocProximityTimeouts = MdocProximityTimeouts(),
        maximumTransportMessageBytes: Int = 8 * 1024 * 1024,
    ): RejectedSession {
        val deviceKey = agreementKey("rejected-device-${deviceKeyCounter++}")
        val readerKey = agreementKey("rejected-reader-${deviceKeyCounter++}")
        val loopback = FakeProximityLoopback.create()
        val method = DeviceRetrievalMethod(1u, 1u, NfcRetrievalOptions(1_024u, 1_024u))
        val engagementContext = EngagementContext("iso-full", maximumTransportMessageBytes, EngagementType.QR)
        val capabilities = MdocHolderProtocolCapabilities(false, false, true)
        val readerSession = readerSession(deviceKey, readerKey, method, engagementContext, capabilities)
        val establishment = ImmutableBytes.of(
            coseCompliantCbor.encodeToByteArray(
                SessionEstablishment(
                    ByteStringWrapper(readerSession.readerCose, readerSession.readerCoseBytes),
                    readerSession.cipher.encrypt(encodeRequest("org.example.rejected")),
                )
            )
        )
        var resolveCalls = 0
        val engine = MdocHolderProtocolEngine(
            eDeviceKey = deviceKey,
            transportProviders = listOf(FakeTransportProvider(method, loopback.holder)),
            requestProcessor = object : MdocHolderRequestProcessor {
                override suspend fun preview(context: MdocHolderRequestContext) = preview(context.request)
                override suspend fun resolve(
                    context: MdocHolderRequestContext,
                    preview: MdocRequestPreview,
                ): MdocResponseResolution {
                    resolveCalls++
                    return MdocResponseResolution(
                        ImmutableBytes.of(coseCompliantCbor.encodeToByteArray(DeviceResponse("1.0", status = 10u))),
                        continueSession = false,
                        submissionBindingDigest = resolutionBinding ?: preview.submissionBindingDigest,
                    )
                }
            },
            consentHandler = MdocConsentHandler { consent(it) },
            engagementContext = engagementContext,
            capabilities = capabilities,
            limits = limits,
            timeouts = timeouts,
        )
        val reader = async {
            val connecting = engine.state.filterIsInstance<MdocHolderSessionState.Connecting>().first()
            val engagementBytes = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
                .decode(requireNotNull(connecting.qrPayload).removePrefix("mdoc:"))
            assertContentEquals(readerSession.engagementBytes, engagementBytes)
            loopback.reader.send(establishment)
            readerSession.cipher.close()
        }

        val result = engine.run()
        reader.await()
        return RejectedSession(result, resolveCalls)
    }

    private fun encodeRequest(docType: String): ByteArray = coseCompliantCbor.encodeToByteArray(
        DeviceRequest(docType, mapOf("org.example" to listOf("name")))
    )

    private fun preview(request: DeviceRequest): MdocRequestPreview {
        val items = request.docRequests.single().itemsRequest.value
        return MdocRequestPreview(
            listOf(
                PreviewDocument(
                    items.docType,
                    listOf("credential"),
                    items.namespaces.flatMap { (namespace, values) ->
                        values.entries.map { PreviewElement(namespace, it.key, it.value) }
                    },
                )
            ),
            submissionBindingDigest = ImmutableBytes.of(org.kotlincrypto.hash.sha2.SHA256().digest("preview".encodeToByteArray())),
        )
    }

    private data class ReaderSession(
        val engagementBytes: ByteArray,
        val readerCose: CoseKey,
        val readerCoseBytes: ByteArray,
        val cipher: MdocSessionCipher,
    )

    private suspend fun readerSession(
        deviceKey: id.walt.crypto2.keys.Key,
        readerKey: id.walt.crypto2.keys.Key,
        method: DeviceRetrievalMethod,
        context: EngagementContext,
        capabilities: MdocHolderProtocolCapabilities,
    ): ReaderSession {
        val engagement = MdocDeviceEngagementFactory().create(
            eDeviceKey = deviceKey,
            methods = listOf(method),
            context = context,
            capabilities = capabilities,
        )
        val readerCose =
            (readerKey.capabilities.publicKeyExporter!!.exportPublicKey() as EncodedKey.Jwk).toCoseKey()
        val readerCoseBytes = coseCompliantCbor.encodeToByteArray(CoseKey.serializer(), readerCose)
        val transcript = SessionTranscript.forQr(engagement.exactBytes.copy(), readerCoseBytes)
        return ReaderSession(
            engagementBytes = engagement.exactBytes.copy(),
            readerCose = readerCose,
            readerCoseBytes = readerCoseBytes,
            cipher = MdocSessionCipher.establishForReader(
                readerKey,
                engagement.deviceEngagement.security.eDeviceKey.value,
                MdocCryptoHelper.buildSessionTranscriptBytes(transcript),
            ),
        )
    }

    private suspend fun agreementKey(id: String) =
        runtime.generateMdocTestKey(id, setOf(KeyUsage.KEY_AGREEMENT))

    private companion object { var deviceKeyCounter = 0 }
}
