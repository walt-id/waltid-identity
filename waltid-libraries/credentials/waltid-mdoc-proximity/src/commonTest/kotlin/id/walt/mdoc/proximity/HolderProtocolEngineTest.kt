@file:OptIn(
    kotlinx.serialization.ExperimentalSerializationApi::class,
    kotlin.ExperimentalUnsignedTypes::class,
)

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
import id.walt.mdoc.objects.deviceretrieval.DeviceRequestInfo
import id.walt.mdoc.objects.deviceretrieval.DeviceResponse
import id.walt.mdoc.objects.deviceretrieval.DocRequest
import id.walt.mdoc.objects.deviceretrieval.DocRequestInfo
import id.walt.mdoc.objects.deviceretrieval.ElementReference
import id.walt.mdoc.objects.deviceretrieval.ItemsRequest
import id.walt.mdoc.objects.deviceretrieval.UseCase
import id.walt.mdoc.objects.document.DeviceAuth
import id.walt.mdoc.objects.elements.DeviceNameSpaces
import id.walt.mdoc.objects.elements.DeviceSignedItem
import id.walt.mdoc.objects.elements.DeviceSignedItemList
import id.walt.mdoc.objects.engagement.BleCentralMode
import id.walt.mdoc.objects.engagement.BlePeripheralEndpoint
import id.walt.mdoc.objects.engagement.BlePeripheralMode
import id.walt.mdoc.objects.engagement.BlePeripheralServerOptions
import id.walt.mdoc.objects.engagement.DeviceEngagement
import id.walt.mdoc.objects.engagement.DeviceRetrievalMethod
import id.walt.mdoc.objects.mso.KeyAuthorization
import id.walt.mdoc.objects.session.SessionData
import id.walt.mdoc.objects.session.SessionEstablishment
import id.walt.mdoc.objects.session.SessionStatusCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
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
import kotlinx.serialization.cbor.CborArray
import kotlinx.serialization.cbor.CborElement
import kotlinx.serialization.cbor.CborMap
import kotlinx.serialization.cbor.CborString
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class HolderProtocolEngineTest {
    private val runtime = CryptoRuntime(defaultSoftwareKeyProviders())

    @Test
    fun `EDeviceKeyBytes encoder accepts SPKI public keys and retains the complete tagged value`() = runTest {
        val deviceKey = agreementKey("edevice-key-bytes").withSpkiPublicExport()
        val context = EngagementContext(
            MdocProximityProfile.ISO_18013_5_ED2_DIS_2026,
            1_048_576,
            MdocEngagementMode.Qr,
        )
        val capabilities = MdocSessionCapabilities.forSession(context.profile, deviceKey, emptySet())
        val factory = MdocDeviceEngagementFactory()
        val engagement = factory.create(
            eDeviceKey = deviceKey,
            methods = listOf(DeviceRetrievalMethod.Nfc(1_024u, 1_024u)),
            context = context,
            capabilities = capabilities,
        )
        val engagementMap = assertIs<CborMap>(
            coseCompliantCbor.decodeFromByteArray<CborElement>(engagement.engagement.encodedCopy())
        )
        val security = assertIs<CborArray>(engagementMap.getValue(1))
        val exactEDeviceKeyBytes = coseCompliantCbor.encodeToByteArray(
            CborElement.serializer(),
            security[1],
        )

        assertContentEquals(exactEDeviceKeyBytes, factory.encodeEDeviceKeyBytes(deviceKey).copy())
    }

    @Test
    fun `Device Engagement enforces placement-specific retrieval methods`() = runTest {
        val deviceKey = agreementKey("engagement-placement")
        val context = EngagementContext(
            MdocProximityProfile.ISO_18013_5_ED2_DIS_2026,
            1_048_576,
            MdocEngagementMode.Nfc,
        )
        val capabilities = MdocSessionCapabilities.forSession(context.profile, deviceKey, emptySet())
        val factory = MdocDeviceEngagementFactory()
        val readerOwnedBle = DeviceRetrievalMethod.Ble(
            centralMode = BleCentralMode(ByteArray(16)),
            peripheralEndpoint = BlePeripheralEndpoint.Reader(BlePeripheralServerOptions(psm = 0x81u)),
        )

        assertFailsWith<IllegalArgumentException> {
            factory.create(deviceKey, listOf(DeviceRetrievalMethod.NfcV2), context, capabilities)
        }
        val negotiated = factory.create(deviceKey, listOf(readerOwnedBle), context, capabilities)
        assertNull(negotiated.engagement.value.deviceRetrievalMethods)

        val qrContext = context.copy(engagementMode = MdocEngagementMode.Qr)
        val qrCapabilities = MdocSessionCapabilities.forSession(
            qrContext.profile,
            deviceKey,
            emptySet(),
        )
        assertFailsWith<IllegalArgumentException> {
            factory.create(deviceKey, listOf(readerOwnedBle), qrContext, qrCapabilities)
        }
        assertFailsWith<IllegalArgumentException> {
            factory.create(
                deviceKey,
                listOf(DeviceRetrievalMethod.Ble(peripheralMode = BlePeripheralMode(ByteArray(16)))),
                qrContext,
                qrCapabilities,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            factory.create(
                deviceKey,
                listOf(DeviceRetrievalMethod.NfcV2, readerOwnedBle),
                context,
                capabilities,
                MdocDeviceEngagementPlacement.PROVISIONAL_NFC_V2,
            )
        }

        val engagement = factory.create(
            deviceKey,
            listOf(readerOwnedBle),
            context,
            capabilities,
            MdocDeviceEngagementPlacement.PROVISIONAL_NFC_V2,
        )
        assertEquals(listOf(readerOwnedBle), engagement.engagement.value.deviceRetrievalMethods)
    }

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
        val method = DeviceRetrievalMethod.Nfc(1_024u, 1_024u)
        val loopback = FakeProximityLoopback.create(capacity = 0)
        val transport = GatedFakeTransportProvider(method, loopback.holder)
        val engagementContext = EngagementContext(
            MdocProximityProfile.ISO_18013_5_ED2_DIS_2026,
            1_048_576,
            MdocEngagementMode.Qr,
        )
        val capabilities = MdocSessionCapabilities.forSession(
            engagementContext.profile,
            deviceKey,
            setOf(MdocProtocolFeature.EXTENDED_REQUESTS),
        )
        val readerSession = readerSession(deviceKey, readerKey, method, engagementContext, capabilities)
        val firstEstablishment = ImmutableBytes.of(
            coseCompliantCbor.encodeToByteArray(
                SessionEstablishment(
                    ByteStringWrapper(readerSession.readerCose, readerSession.readerCoseBytes),
                    readerSession.cipher.encrypt(
                        encodeRequest(
                            listOf(signatureSourceDocument.docType, signatureSourceDocument.docType),
                            withUseCase = true,
                        )
                    ),
                )
            )
        )
        val secondRequest = ImmutableBytes.of(
            coseCompliantCbor.encodeToByteArray(
                SessionData(data = readerSession.cipher.encrypt(encodeRequest(macSourceDocument.docType)))
            )
        )
        var resolved = 0
        val engine = MdocHolderProtocolEngine(
            eDeviceKey = deviceKey,
            engagementSources = listOf(QrMdocEngagementSource(listOf(transport))),
            requestProcessor = object : MdocHolderRequestProcessor {
                override suspend fun preview(context: MdocHolderRequestContext) = preview(context.request.value)
                override suspend fun resolve(
                    context: MdocHolderRequestContext,
                    preview: MdocRequestPreview,
                ): MdocResponseResolution {
                    assertEquals(readerSession.readerCose, context.readerEphemeralKey.value)
                    assertContentEquals(readerSession.readerCoseBytes, context.readerEphemeralKey.encodedCopy())
                    val firstExchange = resolved++ == 0
                    val (source, holderKey, authentication) = if (firstExchange) {
                        Triple(
                            signatureSourceDocument,
                            signatureHolderKey,
                            MdocAuthenticationMethod.Signature(),
                        )
                    } else {
                        Triple(
                            macSourceDocument,
                            macHolderKey,
                            MdocAuthenticationMethod.Mac(context.readerEphemeralKey.value),
                        )
                    }
                    val presentation =
                            MdocDocumentPresentation(
                                source = source,
                                holderKey = holderKey,
                                selectedIssuerElements = setOf(ElementReference("org.example", "given_name")),
                                authentication = authentication,
                            )
                    val response = MdocResponseBuilder().buildResponse(
                        presentations = List(if (firstExchange) 2 else 1) { presentation },
                        transcript = context.transcript.value,
                    )
                    return MdocResponseResolution.Send(
                        ImmutableBytes.of(coseCompliantCbor.encodeToByteArray(response)),
                        continuation = if (firstExchange) {
                            MdocSessionContinuation.CONTINUE
                        } else {
                            MdocSessionContinuation.TERMINATE
                        },
                        submissionBindingDigest = preview.submissionBindingDigest,
                    )
                }
            },
            consentHandler = MdocConsentHandler { MdocConsentDecision.Approve(it.bindingToken) },
            engagementContext = engagementContext,
            capabilities = capabilities,
        )
        val reader = async {
            val engagement = engine.state.filterIsInstance<MdocHolderSessionState.EngagementReady>().first()
            val qrPayload = requireNotNull(engagement.qrPayload)
            val engagementBytes = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
                .decode(qrPayload.removePrefix("mdoc:"))
            assertContentEquals(readerSession.engagementBytes, engagementBytes)
            val decodedEngagement = coseCompliantCbor.decodeFromByteArray<DeviceEngagement>(engagementBytes)
            assertEquals(emptyList(), decodedEngagement.originInfos)
            assertEquals(true, decodedEngagement.capabilities?.extendedRequests)
            assertFalse(decodedEngagement.capabilities!!.readerAuthAll)

            transport.connect()
            engine.state.filterIsInstance<MdocHolderSessionState.Connecting>().first()
            loopback.reader.send(firstEstablishment)
            val signatureResponse = assertResponse(loopback.reader, readerSession.cipher, engine)
            assertEquals(
                1,
                engine.state.filterIsInstance<MdocHolderSessionState.AwaitingNextRequest>()
                    .first().completedExchanges,
            )
            loopback.reader.send(secondRequest)
            val macResponse = assertResponse(
                loopback.reader,
                readerSession.cipher,
                engine,
                expectedStatus = SessionStatusCode.SESSION_TERMINATION,
            )
            val signatureDocuments = requireNotNull(signatureResponse.documents)
            assertEquals(2, signatureDocuments.size)
            signatureDocuments.forEach {
                assertIs<DeviceAuth.Signature>(it.deviceSigned!!.deviceAuth)
            }
            assertIs<DeviceAuth.Mac>(macResponse.documents!!.single().deviceSigned!!.deviceAuth)
            readerSession.cipher.close()
        }

        val result = engine.run()
        reader.await()

        assertEquals(2, assertIs<MdocHolderSessionResult.Completed>(result).exchanges)
        assertEquals(2, assertIs<MdocHolderSessionState.Completed>(engine.state.value).exchanges)
        assertEquals(2, resolved)
        assertFailsWith<IllegalStateException> { engine.run() }
    }

    @Test
    fun `application extensions display summary and authorized device data stay bound through consent`() =
        realDispatcherTest {
            val deviceKey = agreementKey("application-device")
            val readerKey = agreementKey("application-reader")
            val holderKey = runtime.generateMdocTestKey(
                "application-holder",
                setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            )
            val sourceDocument = runtime.issueMdocTestDocument(
                holderKey,
                KeyAuthorization(
                    dataElements = mapOf(
                        APPLICATION_NAMESPACE to listOf(APPLICATION_ELEMENT),
                    )
                ),
            )
            val loopback = FakeProximityLoopback.create()
            val method = DeviceRetrievalMethod.Nfc(1_024u, 1_024u)
            val engagementContext = EngagementContext(
                MdocProximityProfile.ISO_18013_5_ED2_DIS_2026,
                1_048_576,
                MdocEngagementMode.Qr,
            )
            val capabilities = MdocSessionCapabilities.forSession(
                engagementContext.profile,
                deviceKey,
                setOf(MdocProtocolFeature.EXTENDED_REQUESTS),
            )
            val readerSession = readerSession(deviceKey, readerKey, method, engagementContext, capabilities)
            val encodedRequest = encodeApplicationRequest(sourceDocument.docType)
            val establishment = ImmutableBytes.of(
                coseCompliantCbor.encodeToByteArray(
                    SessionEstablishment(
                        ByteStringWrapper(readerSession.readerCose, readerSession.readerCoseBytes),
                        readerSession.cipher.encrypt(encodedRequest),
                    )
                )
            )
            val profileResultDigest = digest("validated-profile-result")
            val submissionDigest = digest("application-submission")
            val applicationAuthorization = MdocApplicationAuthorization(
                profileId = "org.example.application:v1",
                displayTitle = "Confirm example authorization",
                details = listOf(
                    MdocApplicationAuthorizationDetail("amount", "Amount", "EUR 42.00"),
                    MdocApplicationAuthorizationDetail("recipient", "Recipient", "Example Shop"),
                ),
                resultBindingDigest = profileResultDigest,
            )
            var consentedAuthorization: MdocApplicationAuthorization? = null
            val engine = MdocHolderProtocolEngine(
                eDeviceKey = deviceKey,
                engagementSources = listOf(
                    QrMdocEngagementSource(listOf(FakeTransportProvider(method, loopback.holder)))
                ),
                requestProcessor = object : MdocHolderRequestProcessor {
                    override suspend fun preview(context: MdocHolderRequestContext): MdocRequestPreview {
                        assertContentEquals(encodedRequest, context.request.encodedCopy())
                        val request = context.request.value
                        assertEquals(
                            CborString("checkout"),
                            request.deviceRequestInfo!!.value.extensions[APPLICATION_CONTEXT_EXTENSION],
                        )
                        assertEquals(
                            CborString("authorization-request"),
                            request.docRequests.single().itemsRequest.value.requestInfo!!
                                .extensions[APPLICATION_REQUEST_EXTENSION],
                        )
                        return preview(
                            request,
                            applicationAuthorizations = listOf(applicationAuthorization),
                            submissionBindingDigest = submissionDigest,
                        )
                    }

                    override suspend fun resolve(
                        context: MdocHolderRequestContext,
                        preview: MdocRequestPreview,
                    ): MdocResponseResolution {
                        assertEquals(
                            profileResultDigest,
                            preview.applicationAuthorizations.single().resultBindingDigest,
                        )
                        val response = MdocResponseBuilder().buildResponse(
                            presentations = listOf(
                                MdocDocumentPresentation(
                                    source = sourceDocument,
                                    holderKey = holderKey,
                                    selectedIssuerElements = setOf(ElementReference("org.example", "given_name")),
                                    deviceNameSpaces = DeviceNameSpaces(
                                        mapOf(
                                            APPLICATION_NAMESPACE to DeviceSignedItemList(
                                                listOf(DeviceSignedItem(APPLICATION_ELEMENT, "approved"))
                                            )
                                        )
                                    ),
                                    authentication = MdocAuthenticationMethod.Signature(),
                                )
                            ),
                            transcript = context.transcript.value,
                        )
                        return MdocResponseResolution.Send(
                            exactResponse = ImmutableBytes.of(coseCompliantCbor.encodeToByteArray(response)),
                            continuation = MdocSessionContinuation.TERMINATE,
                            submissionBindingDigest = preview.submissionBindingDigest,
                        )
                    }
                },
                consentHandler = MdocConsentHandler { prompt ->
                    consentedAuthorization = prompt.preview.applicationAuthorizations.single()
                    MdocConsentDecision.Approve(prompt.bindingToken)
                },
                engagementContext = engagementContext,
                capabilities = capabilities,
            )
            val reader = async {
                engine.state.filterIsInstance<MdocHolderSessionState.Connecting>().first()
                loopback.reader.send(establishment)
                val response = assertResponse(
                    loopback.reader,
                    readerSession.cipher,
                    engine,
                    expectedStatus = SessionStatusCode.SESSION_TERMINATION,
                )
                val deviceItem = response.documents!!.single().deviceSigned!!.namespaces.value
                    .entries.getValue(APPLICATION_NAMESPACE).entries.single()
                assertEquals(APPLICATION_ELEMENT, deviceItem.key)
                assertEquals("approved", deviceItem.value)
                readerSession.cipher.close()
            }

            val result = engine.run()
            reader.await()

            assertEquals(1, assertIs<MdocHolderSessionResult.Completed>(result).exchanges)
            val authorization = assertNotNull(consentedAuthorization)
            assertEquals("EUR 42.00", authorization.details.first().value)
            assertEquals(profileResultDigest, authorization.resultBindingDigest)
        }

    @Test
    fun `engagement sources must be present and have distinct modes`() = realDispatcherTest {
        val deviceKey = agreementKey("handover-contract-${deviceKeyCounter++}")
        val method = DeviceRetrievalMethod.Nfc(1_024u, 1_024u)
        val capabilities = MdocSessionCapabilities.forSession(
            MdocProximityProfile.ISO_18013_5_ED2_DIS_2026,
            deviceKey,
            emptySet(),
        )
        val processor = object : MdocHolderRequestProcessor {
            override suspend fun preview(context: MdocHolderRequestContext): MdocRequestPreview =
                error("Not reached")
            override suspend fun resolve(
                context: MdocHolderRequestContext,
                preview: MdocRequestPreview,
            ): MdocResponseResolution = error("Not reached")
        }
        val consent = MdocConsentHandler { error("Not reached") }

        assertFailsWith<IllegalArgumentException> {
            MdocHolderProtocolEngine(
                deviceKey,
                emptyList(),
                processor,
                consent,
                EngagementContext(capabilities.profile, 1_024, MdocEngagementMode.Qr),
                capabilities,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            MdocHolderProtocolEngine(
                deviceKey,
                listOf(
                    QrMdocEngagementSource(
                        listOf(FakeTransportProvider(method, FakeProximityLoopback.create().holder))
                    ),
                    QrMdocEngagementSource(
                        listOf(FakeTransportProvider(method, FakeProximityLoopback.create().holder))
                    ),
                ),
                processor,
                consent,
                EngagementContext(
                    capabilities.profile,
                    1_024,
                    MdocEngagementMode.Nfc,
                ),
                capabilities,
            )
        }
        assertEquals(ProximityTransportKind.FAKE, FakeTransportProvider(method, FakeProximityLoopback.create().holder).kind)
    }

    @Test
    fun `stale consent and changed submission bindings fail closed before response`() = realDispatcherTest {
        val stale = runSingleRequestSession(
            consent = { MdocConsentDecision.Approve(ImmutableBytes.of(ByteArray(32))) },
        )
        assertEquals("stale_consent", stale.error.code)
        assertEquals(0, stale.resolveCalls)

        val changed = runSingleRequestSession(
            resolutionBinding = ImmutableBytes.of(ByteArray(32) { 0x7f }),
        )
        assertEquals("changed_submission", changed.error.code)
        assertEquals(1, changed.resolveCalls)
    }

    @Test
    fun `altered application profile result fails closed before response`() = realDispatcherTest {
        val previewAuthorization = applicationAuthorization("approved-application-result")
        val changed = runSingleRequestSession(
            applicationAuthorization = previewAuthorization,
            resolvedApplicationAuthorization = applicationAuthorization("changed-application-result"),
        )

        assertEquals("changed_submission", changed.error.code)
        assertEquals(1, changed.resolveCalls)
    }

    @Test
    fun `plaintext request limit is independent from the larger session-message limit`() = realDispatcherTest {
        val rejected = runSingleRequestSession(
            limits = MdocProximityLimits(maximumRequestBytes = 32),
        )
        assertEquals("request_too_large", rejected.error.code)
        assertEquals(0, rejected.resolveCalls)
    }

    @Test
    fun `peer disconnects and cumulative byte exhaustion fail before response authorization`() = realDispatcherTest {
        val disconnected = runSingleRequestSession(disconnectBeforeEstablishment = true)
        assertEquals("peer_disconnected", disconnected.error.code)
        assertEquals(0, disconnected.resolveCalls)

        val exhausted = runSingleRequestSession(
            limits = MdocProximityLimits(maximumCumulativeSessionBytes = 1),
        )
        assertEquals("session_byte_limit", exhausted.error.code)
        assertEquals(0, exhausted.resolveCalls)
    }

    @Test
    fun `conventional session rejects an NFCv2 sequence field precisely`() = realDispatcherTest {
        val rejected = runSingleRequestSession(establishmentSequence = 0u)

        assertEquals("unexpected_session_sequence", rejected.error.code)
        assertEquals(0, rejected.resolveCalls)
    }

    @Test
    fun `NFCv2 handover selects direction-local sequence numbers in the holder engine`() = realDispatcherTest {
        val deviceKey = agreementKey("nfc-v2-device")
        val readerKey = agreementKey("nfc-v2-reader")
        val loopback = FakeProximityLoopback.create()
        val context = EngagementContext(
            MdocProximityProfile.ISO_18013_5_ED2_DIS_2026,
            1_048_576,
            MdocEngagementMode.Nfc,
        )
        val capabilities = MdocSessionCapabilities.forSession(context.profile, deviceKey, emptySet())
        val engagement = MdocDeviceEngagementFactory().create(
            eDeviceKey = deviceKey,
            methods = listOf(DeviceRetrievalMethod.NfcV2),
            context = context,
            capabilities = capabilities,
            placement = MdocDeviceEngagementPlacement.PROVISIONAL_NFC_V2,
        )
        val engagementBytes = ImmutableBytes.of(engagement.engagement.encodedCopy())
        val handover = MdocSessionHandover.ProvisionalNfcV2(
            handoverSelect = ImmutableBytes.of(byteArrayOf(0x01)),
            handoverRequest = ImmutableBytes.of(byteArrayOf(0x02)),
        )
        val readerCose =
            (readerKey.capabilities.publicKeyExporter!!.exportPublicKey() as EncodedKey.Jwk).toCoseKey()
        val readerCoseBytes = coseCompliantCbor.encodeToByteArray(CoseKey.serializer(), readerCose)
        val transcript = handover.createTranscript(engagementBytes, ImmutableBytes.of(readerCoseBytes))
        val readerCipher = MdocSessionCipher.establishForReader(
            readerKey,
            engagement.engagement.value.security.eDeviceKey.value,
            MdocCryptoHelper.buildSessionTranscriptBytes(transcript),
        )
        val establishment = ImmutableBytes.of(
            coseCompliantCbor.encodeToByteArray(
                SessionEstablishment(
                    eReaderKey = ByteStringWrapper(readerCose, readerCoseBytes),
                    data = readerCipher.encrypt(encodeRequest("org.example.nfc-v2")),
                    seq = 41u,
                )
            )
        )
        val source = FixedEngagementSource(
            MdocEngagedConnection(
                engagementMode = MdocEngagementMode.Nfc,
                deviceEngagement = engagementBytes,
                sessionHandover = handover,
                connection = loopback.holder,
            )
        )
        val engine = MdocHolderProtocolEngine(
            eDeviceKey = deviceKey,
            engagementSources = listOf(source),
            requestProcessor = object : MdocHolderRequestProcessor {
                override suspend fun preview(context: MdocHolderRequestContext): MdocRequestPreview =
                    preview(context.request.value)

                override suspend fun resolve(
                    context: MdocHolderRequestContext,
                    preview: MdocRequestPreview,
                ): MdocResponseResolution = MdocResponseResolution.Send(
                    exactResponse = ImmutableBytes.of(
                        coseCompliantCbor.encodeToByteArray(DeviceResponse("1.0", status = 0u))
                    ),
                    continuation = MdocSessionContinuation.TERMINATE,
                    submissionBindingDigest = preview.submissionBindingDigest,
                )
            },
            consentHandler = MdocConsentHandler { MdocConsentDecision.Approve(it.bindingToken) },
            engagementContext = context,
            capabilities = capabilities,
        )
        val reader = async {
            engine.state.filterIsInstance<MdocHolderSessionState.Connecting>().first()
            loopback.reader.send(establishment)

            val response = coseCompliantCbor.decodeFromByteArray<SessionData>(
                requireNotNull(withTimeoutOrNull(5.seconds) { loopback.reader.receive() }).copy()
            )
            assertEquals(0u, response.seq)
            assertEquals(
                0u,
                coseCompliantCbor.decodeFromByteArray<DeviceResponse>(
                    readerCipher.decrypt(requireNotNull(response.data))
                ).status,
            )
            assertEquals(SessionStatusCode.SESSION_TERMINATION, response.statusCode)
        }

        val result = engine.run()
        reader.await()
        readerCipher.close()

        assertEquals(1, assertIs<MdocHolderSessionResult.Completed>(result).exchanges)
    }

    @Test
    fun `a denied consent decision cannot produce response data`() = realDispatcherTest {
        val denied = runSingleRequestSession(
            consent = { MdocConsentDecision.Deny(it.bindingToken) },
        )

        assertEquals(1, assertIs<MdocHolderSessionResult.Declined>(denied.result).exchange)
        assertEquals(0, denied.resolveCalls)
    }

    @Test
    fun `consent timeout and transport profile size limits fail with stable errors`() = realDispatcherTest {
        val timedOut = runSingleRequestSession(
            consent = { awaitCancellation() },
            timeouts = MdocProximityTimeouts(consent = 1.seconds),
        )
        assertEquals("consent_timeout", timedOut.error.code)
        assertEquals(0, timedOut.resolveCalls)

        val tooLarge = runSingleRequestSession(maximumTransportMessageBytes = 32)
        assertEquals("transport_message_limit", tooLarge.error.code)
        assertEquals(0, tooLarge.resolveCalls)
    }

    @Test
    fun `total session timeout is not misreported as the active phase timeout`() = realDispatcherTest {
        val timedOut = runSingleRequestSession(
            consent = { awaitCancellation() },
            timeouts = MdocProximityTimeouts(
                consent = 5.seconds,
                totalSession = 1.seconds,
            ),
        )

        assertEquals("session_timeout", timedOut.error.code)
        assertEquals(0, timedOut.resolveCalls)
    }

    @Test
    fun `QR engagement lifetime is not shortened by the transport connection timeout`() = realDispatcherTest {
        val result = runConnectionTimeoutSession(
            mode = MdocEngagementMode.Qr,
            qrPayload = "mdoc:test",
            timeouts = MdocProximityTimeouts(
                qrEngagementLifetime = 200.milliseconds,
                transportConnection = 20.milliseconds,
                totalSession = 1.seconds,
            ),
        )

        assertEquals("engagement_timeout", assertIs<MdocHolderSessionResult.Failed>(result).error.code)
    }

    @Test
    fun `NFC-only engagement retains the transport connection timeout`() = realDispatcherTest {
        val result = runConnectionTimeoutSession(
            mode = MdocEngagementMode.Nfc,
            qrPayload = null,
            timeouts = MdocProximityTimeouts(
                qrEngagementLifetime = 200.milliseconds,
                transportConnection = 20.milliseconds,
                totalSession = 1.seconds,
            ),
        )

        assertEquals("connection_timeout", assertIs<MdocHolderSessionResult.Failed>(result).error.code)
    }

    @Test
    fun `different per-document response limits use the strictest value`() = realDispatcherTest {
        val accepted = runSingleRequestSession(
            encodedRequest = encodeRequestWithResponseLimits(listOf(1_024u, 2_048u)),
        )
        assertEquals(1, assertIs<MdocHolderSessionResult.Completed>(accepted.result).exchanges)
        assertEquals(1, accepted.resolveCalls)

        val rejected = runSingleRequestSession(
            encodedRequest = encodeRequestWithResponseLimits(listOf(8u, 2_048u)),
        )
        assertEquals("reader_response_limit", rejected.error.code)
        assertEquals(1, rejected.resolveCalls)
    }

    /** WebCrypto promises must use real scheduling instead of test-scheduler virtual time. */
    private fun realDispatcherTest(block: suspend CoroutineScope.() -> Unit) = runTest {
        withContext(Dispatchers.Default, block)
    }

    private suspend fun assertResponse(
        connection: ProximityConnection,
        cipher: MdocSessionCipher,
        engine: MdocHolderProtocolEngine,
        expectedStatus: SessionStatusCode? = null,
    ): DeviceResponse {
        val bytes = withTimeoutOrNull(5.seconds) { connection.receive() }
            ?: error("No response from holder; state=${engine.state.value}")
        val responseMessage = coseCompliantCbor.decodeFromByteArray<SessionData>(bytes.copy())
        assertEquals(expectedStatus, responseMessage.statusCode)
        val response = coseCompliantCbor.decodeFromByteArray<DeviceResponse>(cipher.decrypt(responseMessage.data!!))
        assertEquals(0u, response.status)
        return response
    }

    private data class SessionRun(val result: MdocHolderSessionResult, val resolveCalls: Int) {
        val error: ProximityError get() = assertIs<MdocHolderSessionResult.Failed>(result).error
    }

    private suspend fun CoroutineScope.runSingleRequestSession(
        consent: suspend (MdocConsentPrompt) -> MdocConsentDecision = { MdocConsentDecision.Approve(it.bindingToken) },
        applicationAuthorization: MdocApplicationAuthorization? = null,
        resolvedApplicationAuthorization: MdocApplicationAuthorization? = null,
        resolutionBinding: ImmutableBytes? = null,
        limits: MdocProximityLimits = MdocProximityLimits(),
        timeouts: MdocProximityTimeouts = MdocProximityTimeouts(),
        maximumTransportMessageBytes: Int = 8 * 1024 * 1024,
        disconnectBeforeEstablishment: Boolean = false,
        establishmentSequence: UInt? = null,
        encodedRequest: ByteArray = encodeRequest("org.example.rejected"),
    ): SessionRun {
        val deviceKey = agreementKey("rejected-device-${deviceKeyCounter++}")
        val readerKey = agreementKey("rejected-reader-${deviceKeyCounter++}")
        val loopback = FakeProximityLoopback.create()
        val method = DeviceRetrievalMethod.Nfc(1_024u, 1_024u)
        val engagementContext = EngagementContext(
            MdocProximityProfile.ISO_18013_5_ED2_DIS_2026,
            maximumTransportMessageBytes,
            MdocEngagementMode.Qr,
        )
        val capabilities = MdocSessionCapabilities.forSession(
            engagementContext.profile,
            deviceKey,
            setOf(MdocProtocolFeature.EXTENDED_REQUESTS),
        )
        val readerSession = readerSession(deviceKey, readerKey, method, engagementContext, capabilities)
        val establishment = ImmutableBytes.of(
            coseCompliantCbor.encodeToByteArray(
                SessionEstablishment(
                    ByteStringWrapper(readerSession.readerCose, readerSession.readerCoseBytes),
                    readerSession.cipher.encrypt(encodedRequest),
                    seq = establishmentSequence,
                )
            )
        )
        var resolveCalls = 0
        val engine = MdocHolderProtocolEngine(
            eDeviceKey = deviceKey,
            engagementSources = listOf(
                QrMdocEngagementSource(listOf(FakeTransportProvider(method, loopback.holder)))
            ),
            requestProcessor = object : MdocHolderRequestProcessor {
                override suspend fun preview(context: MdocHolderRequestContext) = preview(
                    context.request.value,
                    applicationAuthorizations = listOfNotNull(applicationAuthorization),
                    submissionBindingDigest = applicationAuthorization?.consentBindingDigest() ?: digest("preview"),
                )
                override suspend fun resolve(
                    context: MdocHolderRequestContext,
                    preview: MdocRequestPreview,
                ): MdocResponseResolution {
                    resolveCalls++
                    return MdocResponseResolution.Send(
                        ImmutableBytes.of(coseCompliantCbor.encodeToByteArray(DeviceResponse("1.0", status = 10u))),
                        continuation = MdocSessionContinuation.TERMINATE,
                        submissionBindingDigest = resolutionBinding
                            ?: resolvedApplicationAuthorization?.consentBindingDigest()
                            ?: preview.submissionBindingDigest,
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
            if (disconnectBeforeEstablishment) {
                loopback.reader.close(ProximityCloseReason.PEER_DISCONNECTED)
            } else {
                loopback.reader.send(establishment)
            }
            readerSession.cipher.close()
        }

        val result = engine.run()
        reader.await()
        return SessionRun(result, resolveCalls)
    }

    private suspend fun runConnectionTimeoutSession(
        mode: MdocEngagementMode,
        qrPayload: String?,
        timeouts: MdocProximityTimeouts,
    ): MdocHolderSessionResult {
        val deviceKey = agreementKey("connection-timeout-${deviceKeyCounter++}")
        val context = EngagementContext(
            MdocProximityProfile.ISO_18013_5_ED2_DIS_2026,
            1_048_576,
            mode,
        )
        return MdocHolderProtocolEngine(
            eDeviceKey = deviceKey,
            engagementSources = listOf(NeverConnectingEngagementSource(mode, qrPayload)),
            requestProcessor = object : MdocHolderRequestProcessor {
                override suspend fun preview(context: MdocHolderRequestContext): MdocRequestPreview =
                    error("No request is reachable without a connection")

                override suspend fun resolve(
                    context: MdocHolderRequestContext,
                    preview: MdocRequestPreview,
                ): MdocResponseResolution = error("No request is reachable without a connection")
            },
            consentHandler = MdocConsentHandler { error("Consent is unreachable without a connection") },
            engagementContext = context,
            capabilities = MdocSessionCapabilities.forSession(context.profile, deviceKey, emptySet()),
            timeouts = timeouts,
        ).run()
    }

    private fun encodeRequest(docType: String): ByteArray = encodeRequest(listOf(docType))

    private fun encodeRequest(docTypes: List<String>, withUseCase: Boolean = false): ByteArray {
        val docRequests = docTypes.map { docType ->
            DocRequest.fromValues(docType, mapOf("org.example" to listOf("name")), intentToRetain = false)
        }
        val requestInfo = if (withUseCase) {
            DeviceRequestInfo(
                useCases = listOf(
                    UseCase(
                        mandatory = true,
                        purposeHints = mapOf("org.example.purpose" to 1),
                        documentSets = listOf(docRequests.indices.map(Int::toUInt)),
                    )
                )
            )
        } else null
        return coseCompliantCbor.encodeToByteArray(
            DeviceRequest(
                version = if (requestInfo == null) DeviceRequest.VERSION else DeviceRequest.VERSION_WITH_SIGNING,
                docRequests = docRequests,
                deviceRequestInfo = requestInfo?.let {
                    ByteStringWrapper(
                        it,
                        coseCompliantCbor.encodeToByteArray(DeviceRequestInfo.serializer(), it),
                    )
                },
            )
        )
    }

    private fun encodeRequestWithResponseLimits(responseLimits: List<UInt>): ByteArray {
        require(responseLimits.isNotEmpty())
        val docRequests = responseLimits.mapIndexed { index, maximumResponseSize ->
            val request = DocRequest.fromValues(
                docType = "org.example.response-limit-$index",
                requestedElements = mapOf("org.example" to listOf("name")),
                intentToRetain = false,
            )
            val itemsRequest = request.itemsRequest.value.copy(
                requestInfo = DocRequestInfo(maximumResponseSize = maximumResponseSize),
            )
            request.copy(
                itemsRequest = ByteStringWrapper(
                    itemsRequest,
                    coseCompliantCbor.encodeToByteArray(ItemsRequest.serializer(), itemsRequest),
                ),
            )
        }
        return coseCompliantCbor.encodeToByteArray(
            DeviceRequest(
                version = DeviceRequest.VERSION,
                docRequests = docRequests,
            )
        )
    }

    private fun preview(
        request: DeviceRequest,
        applicationAuthorizations: List<MdocApplicationAuthorization> = emptyList(),
        submissionBindingDigest: ImmutableBytes = digest("preview"),
    ): MdocRequestPreview {
        return MdocRequestPreview(
            request.docRequests.map { docRequest ->
                val items = docRequest.itemsRequest.value
                PreviewDocument(
                    items.docType,
                    listOf("credential"),
                    items.namespaces.flatMap { (namespace, values) ->
                        values.entries.map { PreviewElement(namespace, it.key, it.value) }
                    },
                )
            },
            purposeHints = request.deviceRequestInfo?.value?.useCases.orEmpty()
                .flatMap { it.purposeHints.orEmpty().entries }
                .associate { it.toPair() },
            applicationAuthorizations = applicationAuthorizations,
            submissionBindingDigest = submissionBindingDigest,
        )
    }

    private fun encodeApplicationRequest(docType: String): ByteArray {
        val request = DocRequest.fromValues(
            docType,
            mapOf("org.example" to listOf("given_name")),
            intentToRetain = false,
        )
        val itemsRequest = request.itemsRequest.value.copy(
            requestInfo = DocRequestInfo(
                extensions = mapOf(APPLICATION_REQUEST_EXTENSION to CborString("authorization-request")),
            )
        )
        val docRequest = request.copy(
            itemsRequest = ByteStringWrapper(
                itemsRequest,
                coseCompliantCbor.encodeToByteArray(ItemsRequest.serializer(), itemsRequest),
            )
        )
        val requestInfo = DeviceRequestInfo(
            extensions = mapOf(APPLICATION_CONTEXT_EXTENSION to CborString("checkout")),
        )
        return coseCompliantCbor.encodeToByteArray(
            DeviceRequest(
                version = DeviceRequest.VERSION_WITH_SIGNING,
                docRequests = listOf(docRequest),
                deviceRequestInfo = ByteStringWrapper(
                    requestInfo,
                    coseCompliantCbor.encodeToByteArray(DeviceRequestInfo.serializer(), requestInfo),
                ),
            )
        )
    }

    private fun digest(value: String): ImmutableBytes = ImmutableBytes.of(
        org.kotlincrypto.hash.sha2.SHA256().digest(value.encodeToByteArray())
    )

    private fun applicationAuthorization(result: String) = MdocApplicationAuthorization(
        profileId = "org.example.application:v1",
        displayTitle = "Confirm example authorization",
        details = listOf(
            MdocApplicationAuthorizationDetail("amount", "Amount", "EUR 42.00"),
        ),
        resultBindingDigest = digest(result),
    )

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
        capabilities: MdocSessionCapabilities,
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
        val transcript = SessionTranscript.forQr(engagement.engagement.encodedCopy(), readerCoseBytes)
        return ReaderSession(
            engagementBytes = engagement.engagement.encodedCopy(),
            readerCose = readerCose,
            readerCoseBytes = readerCoseBytes,
            cipher = MdocSessionCipher.establishForReader(
                readerKey,
                engagement.engagement.value.security.eDeviceKey.value,
                MdocCryptoHelper.buildSessionTranscriptBytes(transcript),
            ),
        )
    }

    private suspend fun agreementKey(id: String) =
        runtime.generateMdocTestKey(id, setOf(KeyUsage.KEY_AGREEMENT))

    private class GatedFakeTransportProvider(
        private val method: DeviceRetrievalMethod,
        private val connection: ProximityConnection,
    ) : ProximityTransportProvider {
        private val connected = CompletableDeferred<Unit>()

        override val kind: ProximityTransportKind = ProximityTransportKind.FAKE

        fun connect() {
            connected.complete(Unit)
        }

        override suspend fun capability(context: EngagementContext): ProximityCapability =
            ProximityCapability(true, true, true, sessionSelected = true)

        override suspend fun prepare(
            context: EngagementContext,
            sessionScope: CoroutineScope,
        ): PreparedTransport = object : PreparedTransport {
            override val kind: ProximityTransportKind = ProximityTransportKind.FAKE
            override val connectionMethod: DeviceRetrievalMethod = method

            override suspend fun awaitConnection(): ProximityConnection {
                connected.await()
                return connection
            }

            override suspend fun close(reason: ProximityCloseReason) {
                connection.close(reason)
            }
        }
    }

    private class FixedEngagementSource(
        private val engaged: MdocEngagedConnection,
    ) : MdocEngagementSource {
        override val modes: Set<MdocEngagementMode> = setOf(engaged.engagementMode)

        override suspend fun prepare(
            context: MdocEngagementPreparationContext,
            sessionScope: CoroutineScope,
        ): PreparedMdocEngagement = object : PreparedMdocEngagement {
            override val modes: Set<MdocEngagementMode> = this@FixedEngagementSource.modes
            override val readiness: MdocEngagementReadiness = MdocEngagementReadiness(
                qrPayload = null,
                availableTransports = setOf(engaged.connection.kind),
                unavailableTransports = emptyMap(),
            )

            override suspend fun awaitConnection(): MdocEngagedConnection = engaged

            override suspend fun close(reason: ProximityCloseReason) {
                engaged.connection.close(reason)
            }
        }
    }

    private class NeverConnectingEngagementSource(
        mode: MdocEngagementMode,
        private val qrPayload: String?,
    ) : MdocEngagementSource {
        override val modes: Set<MdocEngagementMode> = setOf(mode)

        override suspend fun prepare(
            context: MdocEngagementPreparationContext,
            sessionScope: CoroutineScope,
        ): PreparedMdocEngagement = object : PreparedMdocEngagement {
            override val modes: Set<MdocEngagementMode> = this@NeverConnectingEngagementSource.modes
            override val readiness: MdocEngagementReadiness = MdocEngagementReadiness(
                qrPayload = this@NeverConnectingEngagementSource.qrPayload,
                availableTransports = setOf(ProximityTransportKind.FAKE),
                unavailableTransports = emptyMap(),
            )

            override suspend fun awaitConnection(): MdocEngagedConnection = awaitCancellation()

            override suspend fun close(reason: ProximityCloseReason) = Unit
        }
    }

    private companion object {
        const val APPLICATION_CONTEXT_EXTENSION = "org.example.applicationContext"
        const val APPLICATION_REQUEST_EXTENSION = "org.example.applicationRequest"
        const val APPLICATION_NAMESPACE = "org.example.application"
        const val APPLICATION_ELEMENT = "authorization_code"
        var deviceKeyCounter = 0
    }
}
