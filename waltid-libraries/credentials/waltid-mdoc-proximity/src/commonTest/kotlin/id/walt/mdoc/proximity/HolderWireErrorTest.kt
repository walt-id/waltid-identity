@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class, ExperimentalUnsignedTypes::class)

package id.walt.mdoc.proximity

import id.walt.cose.CoseKey
import id.walt.cose.coseCompliantCbor
import id.walt.cose.toCoseKey
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.mdoc.crypto.MdocCryptoHelper
import id.walt.mdoc.objects.SessionTranscript
import id.walt.mdoc.objects.deviceretrieval.DeviceResponse
import id.walt.mdoc.objects.engagement.DeviceRetrievalMethod
import id.walt.mdoc.objects.session.SessionData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.cbor.*
import kotlinx.serialization.decodeFromByteArray
import kotlin.test.*

class HolderWireErrorTest {
    private val runtime = CryptoRuntime(defaultSoftwareKeyProviders())

    @Test
    fun invalidReaderKeysAndCiphertextsReturnSessionEncryptionError() = realDispatcherTest {
        for (case in listOf("01", "02", "03", "04", "05", "06", "07", "08", "09")) {
            val fixture = session()
            val key = fixture.readerKey.toMutableMap()
            val ciphertext = fixture.readerCipher.encrypt(validRequest())
            var damaged = ciphertext
            when (case) {
                "01" -> { key[CborInteger(-2)] = CborByteString(ByteArray(32)); key[CborInteger(-3)] = CborByteString(ByteArray(32)) }
                "02" -> key[CborInteger(-1)] = CborInteger(2)
                "03" -> damaged = ciphertext.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
                "04" -> damaged = ciphertext.copyOf(ciphertext.size - 4)
                "05" -> damaged = byteArrayOf(0) + ciphertext
                "06" -> key.remove(CborInteger(1))
                "07" -> key[CborInteger(1)] = CborInteger(1)
                "08" -> key.remove(CborInteger(-1))
                "09" -> key[CborInteger(-3)] = CborBoolean(true)
            }
            val result = fixture.exchange(establishment(encode(CborMap(key)), damaged))
            val id = "mDL_SM_SEnc_SEst_UF_$case"
            assertIs<MdocHolderSessionResult.Failed>(result.result, id)
            assertEquals(0, result.previewCalls, id)
            assertEquals(0, result.resolveCalls, id)
            val message = result.messages.single()
            assertEquals(10u, message.status, id)
            assertNull(message.data, id)
            fixture.readerCipher.close()
        }
    }

    @Test
    fun unwrappedReaderKeyStillReturnsSessionCborError() = realDispatcherTest {
        val fixture = session()
        val result = fixture.exchange(encode(CborMap(mapOf(
            CborString("eReaderKey") to fixture.readerKey,
            CborString("data") to CborByteString(fixture.readerCipher.encrypt(validRequest())),
        ))))
        assertEquals(11u, result.messages.single().status, "mDL_SM_SEnc_SEst_UF_10")
        assertNull(result.messages.single().data)
        fixture.readerCipher.close()
    }

    @Test
    fun malformedDeviceRequestsReturnEncryptedDeviceResponseErrors() = realDispatcherTest {
        val valid = validRequest()
        val fields = (coseCompliantCbor.decodeFromByteArray<CborElement>(valid) as CborMap).toMap()
        val duplicateItems = byteArrayOf(0xa3.toByte()) + encode(CborString("docType")) + encode(CborString(DOC_TYPE)) +
            encode(CborString("nameSpaces")) + encode(namespaces("given_name")) +
            encode(CborString("nameSpaces")) + encode(namespaces("family_name"))
        val cases = linkedMapOf(
            "01" to valid.copyOf().also { it[0] = 0x84.toByte() },
            "02" to valid.copyOf().also { it[0] = 0xa1.toByte() },
            "03" to encode(CborMap(fields - CborString("version"))),
            "04" to encode(CborMap(fields + (CborString("version") to CborString("11.0")))),
            "05" to encode(CborMap((fields - CborString("docRequests")) + (CborString("docRequest") to fields.getValue(CborString("docRequests"))))),
            "06" to encode(CborMap(fields + (CborString("docRequests") to CborArray(emptyList())))),
            "08" to requestWithItems(encode(CborMap(mapOf(CborString("docType") to CborString(DOC_TYPE), CborString("nameSpaces") to CborMap(emptyMap()))))),
            "09" to requestWithItems(duplicateItems),
            "11" to requestWithItems(encode(CborMap(mapOf(CborString("docType") to CborString(DOC_TYPE), CborString("nameSpaces") to CborMap(mapOf(CborString(NAMESPACE) to CborMap(emptyMap()))))))),
        )
        for ((case, request) in cases) {
            val fixture = session()
            val result = fixture.exchange(establishment(encode(fixture.readerKey), fixture.readerCipher.encrypt(request)))
            val id = "mDL_MS_DR_UF_$case"
            assertIs<MdocHolderSessionResult.Failed>(result.result, id)
            assertEquals(0, result.previewCalls, id)
            assertEquals(0, result.resolveCalls, id)
            val response = fixture.response(result, id)
            assertEquals(10u, response.status, id)
            assertNull(response.documents, id)
            assertNull(response.documentErrors, id)
            fixture.readerCipher.close()
        }
    }

    @Test
    fun denyingAllElementsReturnsAnEncryptedEmptyResponseWithoutResolvingKeys() = realDispatcherTest {
        val fixture = session()
        val result = fixture.exchange(establishment(encode(fixture.readerKey), fixture.readerCipher.encrypt(validRequest())))
        assertIs<MdocHolderSessionResult.Declined>(result.result)
        assertEquals(1, result.previewCalls)
        assertEquals(0, result.resolveCalls)
        val response = fixture.response(result, "mDL_MS_DR_UF_13")
        assertEquals(0u, response.status)
        assertNull(response.documents)
        assertNull(response.documentErrors)
        fixture.readerCipher.close()
    }

    @Test
    fun noDataPreparationReturnsAnEncryptedEmptyResponseWithoutConsent() = realDispatcherTest {
        val fixture = session()
        val result = fixture.exchange(establishment(encode(fixture.readerKey), fixture.readerCipher.encrypt(validRequest())), noData = true)
        assertIs<MdocHolderSessionResult.Completed>(result.result)
        assertEquals(0, result.previewCalls)
        assertEquals(0, result.resolveCalls)
        assertEquals(0u, fixture.response(result, "no-data preparation").status)
        fixture.readerCipher.close()
    }

    @Test
    fun rejectedPreparationReturnsNoDataAndRetainsTheLocalFailure() = realDispatcherTest {
        val fixture = session()
        val error = ProximityError.Policy("trusted_reader_required", "Reader trust is required")
        val result = fixture.exchange(establishment(encode(fixture.readerKey), fixture.readerCipher.encrypt(validRequest())), rejected = error)
        assertEquals(error, assertIs<MdocHolderSessionResult.Failed>(result.result).error)
        assertEquals(0, result.previewCalls)
        assertEquals(0, result.resolveCalls)
        assertEquals(0u, fixture.response(result, "rejected preparation").status)
        fixture.readerCipher.close()
    }

    @Test
    fun embeddedCborSharesDepthAndItemBudgetsWithTheRequest() {
        val nested = encode(CborByteString(encode(CborByteString(validRequest(), 24u)), 24u))
        assertFailsWith<MdocCborValidationException> { MdocCborGuard.validate(nested, 3, 100, includeEmbeddedCbor = true) }
        assertFailsWith<MdocCborValidationException> { MdocCborGuard.validate(nested, 30, 10, includeEmbeddedCbor = true) }
        MdocCborGuard.validate(nested, 30, 100, includeEmbeddedCbor = true)
    }

    // WebCrypto promises use real time; advancing virtual time would expire a live session.
    private fun realDispatcherTest(block: suspend CoroutineScope.() -> Unit) = runTest {
        withContext(Dispatchers.Default, block)
    }

    private suspend fun session(): Session {
        val deviceKey = runtime.generateMdocTestKey("wire-device", setOf(KeyUsage.KEY_AGREEMENT))
        val readerKey = runtime.generateMdocTestKey("wire-reader", setOf(KeyUsage.KEY_AGREEMENT))
        val method = DeviceRetrievalMethod.Nfc(1024u, 1024u)
        val context = EngagementContext(MdocProximityProfile.ISO_18013_5_ED2_DIS_2026, 1_048_576, MdocEngagementMode.Qr)
        val capabilities = MdocSessionCapabilities.forSession(context.profile, deviceKey, emptySet())
        val engagement = MdocDeviceEngagementFactory().create(deviceKey, listOf(method), context, capabilities)
        val cose = (readerKey.capabilities.publicKeyExporter!!.exportPublicKey() as EncodedKey.Jwk).toCoseKey()
        val coseBytes = coseCompliantCbor.encodeToByteArray(CoseKey.serializer(), cose)
        val transcript = SessionTranscript.forQr(engagement.engagement.encodedCopy(), coseBytes)
        return Session(deviceKey, method, context, capabilities,
            coseCompliantCbor.decodeFromByteArray<CborElement>(coseBytes) as CborMap,
            MdocSessionCipher.establishForReader(readerKey, engagement.engagement.value.security.eDeviceKey.value, MdocCryptoHelper.buildSessionTranscriptBytes(transcript)))
    }

    private data class Exchange(val result: MdocHolderSessionResult, val messages: List<SessionData>, val previewCalls: Int, val resolveCalls: Int)

    private class Session(
        val deviceKey: id.walt.crypto2.keys.Key,
        val method: DeviceRetrievalMethod,
        val context: EngagementContext,
        val capabilities: MdocSessionCapabilities,
        val readerKey: CborMap,
        val readerCipher: MdocSessionCipher,
    ) {
        suspend fun exchange(bytes: ByteArray, noData: Boolean = false, rejected: ProximityError? = null): Exchange {
            val loopback = FakeProximityLoopback.create()
            var previewCalls = 0
            var resolveCalls = 0
            val engine = MdocHolderProtocolEngine(deviceKey, listOf(FakeTransportProvider(method, loopback.holder)),
                object : MdocHolderRequestProcessor {
                    override suspend fun prepare(context: MdocHolderRequestContext): MdocRequestPreparation =
                        if (rejected != null) MdocRequestPreparation.Rejected(rejected)
                        else if (noData) MdocRequestPreparation.NoData else super.prepare(context)
                    override suspend fun preview(context: MdocHolderRequestContext): MdocRequestPreview {
                        previewCalls++
                        return MdocRequestPreview(listOf(PreviewDocument(DOC_TYPE, listOf("credential"),
                            listOf(PreviewElement(NAMESPACE, "given_name", false)))), submissionBindingDigest = ImmutableBytes.of(ByteArray(32)))
                    }
                    override suspend fun resolve(context: MdocHolderRequestContext, preview: MdocRequestPreview): MdocResponseResolution {
                        resolveCalls++
                        error("These requests must not authorize credential disclosure")
                    }
                }, MdocConsentHandler {
                    check(!noData && rejected == null) { "A no-data response must not request consent" }
                    MdocConsentDecision.Deny(it.bindingToken)
                }, context, capabilities)
            loopback.reader.send(ImmutableBytes.of(bytes))
            val result = engine.run()
            val messages = mutableListOf<SessionData>()
            while (true) {
                val message = loopback.reader.receive() ?: break
                messages += coseCompliantCbor.decodeFromByteArray<SessionData>(message.copy())
            }
            return Exchange(result, messages, previewCalls, resolveCalls)
        }

        suspend fun response(exchange: Exchange, id: String): DeviceResponse {
            val message = exchange.messages.single()
            assertEquals(20u, message.status, id)
            val decoded = readerCipher.decrypt(assertNotNull(message.data, id))
            return coseCompliantCbor.decodeFromByteArray<DeviceResponse>(decoded)
        }
    }

    private companion object {
        const val DOC_TYPE = "org.iso.18013.5.1.mDL"
        const val NAMESPACE = "org.iso.18013.5.1"
        fun encode(value: CborElement): ByteArray = coseCompliantCbor.encodeToByteArray(CborElement.serializer(), value)
        fun namespaces(element: String) = CborMap(mapOf(CborString(NAMESPACE) to CborMap(mapOf(CborString(element) to CborBoolean(false)))))
        fun validRequest() = requestWithItems(encode(CborMap(mapOf(CborString("docType") to CborString(DOC_TYPE), CborString("nameSpaces") to namespaces("given_name")))))
        fun requestWithItems(items: ByteArray) = encode(CborMap(mapOf(CborString("version") to CborString("1.0"),
            CborString("docRequests") to CborArray(listOf(CborMap(mapOf(CborString("itemsRequest") to CborByteString(items, 24u))))))))
        fun establishment(key: ByteArray, data: ByteArray) = encode(CborMap(mapOf(CborString("eReaderKey") to CborByteString(key, 24u), CborString("data") to CborByteString(data))))
    }
}
