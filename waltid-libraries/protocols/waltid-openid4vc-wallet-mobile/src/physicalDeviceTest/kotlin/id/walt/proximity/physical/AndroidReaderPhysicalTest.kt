@file:Suppress("DEPRECATION") // Pinned peer exposes explicit authentication verdicts.
package id.walt.proximity.physical

import android.nfc.NfcAdapter
import android.nfc.tech.IsoDep
import id.walt.proximity.test.PhysicalDeviceTest
import id.walt.wallet2.mobile.peer.SelectCorrectedNfcReader
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Test
import org.multipaz.cbor.*
import org.multipaz.context.initializeApplication
import org.multipaz.crypto.Algorithm
import org.multipaz.crypto.Crypto
import org.multipaz.crypto.EcCurve
import org.multipaz.mdoc.connectionmethod.MdocConnectionMethod
import org.multipaz.mdoc.connectionmethod.MdocConnectionMethodBle
import org.multipaz.mdoc.connectionmethod.MdocConnectionMethodNfc
import org.multipaz.mdoc.engagement.EngagementParser
import org.multipaz.mdoc.nfc.mdocReaderNfcHandover
import org.multipaz.mdoc.request.DeviceRequestGenerator
import org.multipaz.mdoc.response.DeviceResponseParser
import org.multipaz.mdoc.role.MdocRole
import org.multipaz.mdoc.sessionencryption.SessionEncryption
import org.multipaz.mdoc.transport.*
import org.multipaz.nfc.*
import org.multipaz.util.Logger
import java.security.cert.*
import kotlin.io.encoding.Base64
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

/** Runs only from the explicit local controller on a second selected Android device. */
@PhysicalDeviceTest
class AndroidReaderPhysicalTest {
    @Test(timeout = 240_000)
    fun independentlyVerifySuccessRejectDisclosureOnDisconnectAndVerifyRecovery() = runBlocking {
        PhysicalRun("reader").use { run ->
            run.launch()
            initializeApplication(run.context.applicationContext)
            val previousLogger = Logger.logPrinter
            Logger.logPrinter = Logger.LogPrinter { _, _, _, _ -> }
            try {
                withTimeout(210.seconds) {
                    for (round in 1..3) exchange(run, round)
                    run.event("reader-passed", "rounds" to "3", "peerRevision" to PhysicalRun.PEER_REVISION)
                }
            } finally { Logger.logPrinter = previousLogger }
        }
    }

    private suspend fun exchange(run: PhysicalRun, round: Int) = coroutineScope {
        val input = run.input("peer-input-$round")
        var isoDep: IsoDep? = null
        var transport: MdocTransport? = null
        try {
            val readerKey = Crypto.createEcPrivateKey(EcCurve.P256)
            val encodedEngagement: ByteArray
            val handover: DataItem
            val methods: List<MdocConnectionMethod>
            var nfcTag: CountingTag? = null
            if (run.configuration.startsWith("nfc")) {
                isoDep = awaitNfc(run)
                nfcTag = CountingTag(NfcIsoTagAndroid(isoDep, Dispatchers.IO) {})
                val result = assertNotNull(mdocReaderNfcHandover(nfcTag, emptyList()))
                encodedEngagement = result.encodedDeviceEngagement.toByteArray()
                handover = result.handover
                methods = result.connectionMethods
            } else {
                val qr = input.getValue("qr").jsonPrimitive.content
                check(qr.startsWith("mdoc:")) { "Missing selected holder engagement" }
                encodedEngagement = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).decode(qr.removePrefix("mdoc:"))
                handover = Simple.NULL
                methods = EngagementParser(encodedEngagement).parse().connectionMethods
            }
            val engagement = EngagementParser(encodedEngagement).parse()
            val transcript = Cbor.encode(buildCborArray {
                add(Tagged(24, Bstr(encodedEngagement)))
                add(Tagged(24, Bstr(Cbor.encode(readerKey.publicKey.toCoseKey().toDataItem()))))
                add(handover)
            })
            val cipher = SessionEncryption(MdocRole.MDOC_READER, readerKey, engagement.eSenderKey, transcript)
            val directNfc = run.configuration == "nfc-direct-disconnect"
            val method = if (directNfc) methods.filterIsInstance<MdocConnectionMethodNfc>().single()
                else methods.filterIsInstance<MdocConnectionMethodBle>().single()
            val l2cap = run.configuration.startsWith("ble-l2cap")
            val reader = if (directNfc) {
                SelectCorrectedNfcReader(MdocRole.MDOC_READER, MdocTransportOptions(), method as MdocConnectionMethodNfc)
                    .also { it.setTag(assertNotNull(nfcTag)) }
            } else MdocTransportFactory.Default.createTransport(method, MdocRole.MDOC_READER,
                MdocTransportOptions(bleUseL2CAP = l2cap))
            transport = reader
            withTimeout(45.seconds) { reader.open(engagement.eSenderKey) }
            val bearer = if (directNfc) "nfc" to "" else actualBleBearer(reader, l2cap)
            run.event("reader-connected-$round", "bearer" to bearer.first, "psm" to bearer.second)
            if (run.configuration == "nfc-ble-continuation") {
                // Real Android field loss after handover; the following request/response must use BLE.
                run.onMain { NfcAdapter.getDefaultAdapter(run.activity).disableReaderMode(run.activity) }
                isoDep?.close()
                isoDep = null
                run.event("reader-nfc-field-disabled-$round")
            }
            val fields = mapOf("given_name" to "Ada", "family_name" to "Lovelace")
            val request = DeviceRequestGenerator(transcript).addDocumentRequest(PhysicalCredentialFixture.DOC_TYPE,
                mapOf(PhysicalCredentialFixture.NAMESPACE to fields.keys.associateWith { false }),
                null, null, Algorithm.UNSET, null).generate()
            reader.sendMessage(cipher.encryptMessage(request, null))
            val response = async {
                try { reader.waitForMessage() }
                catch (_: MdocTransportClosedException) { null }
                catch (_: MdocTransportException) { null }
            }
            if (round == 2) {
                run.input("disconnect-$round")
                assertFalse(response.isCompleted, "Reader ended before the controlled disconnect")
                // Any response before or during disconnect is a disclosure failure, including ciphertext.
                isoDep?.close()
                if (run.configuration.startsWith("nfc")) run.onMain {
                    NfcAdapter.getDefaultAdapter(run.activity).disableReaderMode(run.activity)
                }
                reader.close()
                assertNull(withTimeout(5.seconds) { response.await() }, "Reader received data without approval")
                run.event("reader-disconnected-$round", "receivedData" to "false")
            } else {
                val (plaintext, status) = cipher.decryptMessage(assertNotNull(withTimeout(45.seconds) { response.await() }))
                assertEquals(20L, status)
                verifyResponse(assertNotNull(plaintext), transcript, fields,
                    input.getValue("root").jsonPrimitive.content.hexToByteArray(),
                    input.getValue("wrongRoot").jsonPrimitive.content.hexToByteArray())
                if (directNfc) assertTrue(assertNotNull(nfcTag).getResponseCount > 0, "Physical response did not exercise fragmentation")
                run.event("reader-verified-$round", "fieldCount" to "2", "issuerAuthenticated" to "true",
                    "deviceAuthenticated" to "true", "issuerTrusted" to "true",
                    "getResponseCount" to (nfcTag?.getResponseCount ?: 0).toString())
            }
        } finally {
            // Closing IsoDep releases any native transceive before transport coroutine cleanup.
            isoDep?.close()
            if (run.configuration.startsWith("nfc")) run.onMain {
                NfcAdapter.getDefaultAdapter(run.activity).disableReaderMode(run.activity)
            }
            transport?.close()
        }
    }

    private suspend fun awaitNfc(run: PhysicalRun): IsoDep = withTimeout(60.seconds) {
        val tag = CompletableDeferred<IsoDep>()
        val adapter = assertNotNull(NfcAdapter.getDefaultAdapter(run.activity))
        check(adapter.isEnabled) { "Enable NFC explicitly before starting the physical suite" }
        run.onMain { adapter.enableReaderMode(run.activity, { discovered ->
            val candidate = IsoDep.get(discovered)
            if (candidate != null && !tag.complete(candidate)) candidate.close()
        }, NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK, null) }
        val selected = tag.await()
        try {
            withContext(Dispatchers.IO) { selected.timeout = 10_000; selected.connect() }
            selected
        } catch (failure: Throwable) { selected.close(); throw failure }
    }

    private fun actualBleBearer(reader: MdocTransport, expectedL2cap: Boolean): Pair<String, String> {
        // Pinned peer exposes no transport-level bearer API. Inspect its owned native manager,
        // not its preference. A peer layout change fails here rather than inventing evidence.
        val field = reader.javaClass.declaredFields.single { it.name in setOf("centralManager", "peripheralManager") }
        val manager = field.apply { isAccessible = true }.get(reader)
        val usingL2cap = manager.javaClass.methods.single { it.name == "getUsingL2cap" }.invoke(manager) as Boolean
        assertEquals(expectedL2cap, usingL2cap, "The requested bearer was not actually established")
        val psm = manager.javaClass.methods.single { it.name == "getL2capPsm" }.invoke(manager) as? Int
        if (usingL2cap) assertTrue(psm != null && psm in 0x80..0xff, "Missing actual LE dynamic PSM")
        return (if (usingL2cap) "l2cap" else "gatt") to (if (usingL2cap) psm.toString() else "")
    }

    private suspend fun verifyResponse(bytes: ByteArray, transcript: ByteArray, expected: Map<String, String>, root: ByteArray, wrongRoot: ByteArray) {
        val document = DeviceResponseParser(bytes, transcript).parse().documents.single()
        assertEquals(PhysicalCredentialFixture.DOC_TYPE, document.docType)
        assertTrue(document.issuerSignedAuthenticated)
        assertTrue(document.deviceSignedAuthenticated && document.deviceSignedAuthenticatedViaSignature)
        assertEquals(0, document.numIssuerEntryDigestMatchFailures)
        assertEquals(expected.keys, document.getIssuerEntryNames(PhysicalCredentialFixture.NAMESPACE).toSet())
        expected.forEach { (name, value) -> assertEquals(value, document.getIssuerEntryString(PhysicalCredentialFixture.NAMESPACE, name)) }
        val factory = CertificateFactory.getInstance("X.509")
        fun certificate(der: ByteArray) = factory.generateCertificate(der.inputStream()) as X509Certificate
        val path = factory.generateCertPath(document.issuerCertificateChain.certificates.map { certificate(it.encoded.toByteArray()) })
        fun validate(anchor: ByteArray) = CertPathValidator.getInstance("PKIX").validate(path,
            PKIXParameters(setOf(TrustAnchor(certificate(anchor), null))).apply { isRevocationEnabled = false })
        validate(root)
        assertFailsWith<CertPathValidatorException> { validate(wrongRoot) }
    }

    private class CountingTag(private val delegate: NfcIsoTag) : NfcIsoTag() {
        var getResponseCount = 0
            private set
        override val maxTransceiveLength get() = delegate.maxTransceiveLength
        override suspend fun transceive(command: CommandApdu): ResponseApdu {
            if (command.ins == Nfc.INS_GET_RESPONSE) getResponseCount++
            return delegate.transceive(command)
        }
        override suspend fun close() = delegate.close()
        override suspend fun updateDialogMessage(message: String) = delegate.updateDialogMessage(message)
    }
}
