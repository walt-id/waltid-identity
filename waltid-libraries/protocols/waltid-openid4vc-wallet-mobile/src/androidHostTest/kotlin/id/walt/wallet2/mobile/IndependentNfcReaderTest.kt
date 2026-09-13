@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@file:Suppress("DEPRECATION") // Pinned peer's byte-oriented parser exposes each authentication verdict.

package id.walt.wallet2.mobile

import id.walt.certificate.x509.X509CertificateUtil
import id.walt.certificate.x509.profile.IsoDocumentSignerX509CertificateProfile.profileDocumentSignerCertificate
import id.walt.cose.*
import id.walt.credentials.CredentialParser
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.algorithms.*
import id.walt.crypto2.keys.*
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.mdoc.issuance.MdocIssuer
import id.walt.mdoc.objects.document.Document
import id.walt.mdoc.proximity.mobile.*
import id.walt.wallet2.data.*
import id.walt.wallet2.stores.inmemory.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.json.*
import org.multipaz.cbor.*
import org.multipaz.crypto.Crypto
import org.multipaz.crypto.EcCurve as PeerCurve
import org.multipaz.crypto.Algorithm
import org.multipaz.mdoc.connectionmethod.MdocConnectionMethodNfc
import org.multipaz.mdoc.engagement.EngagementParser
import org.multipaz.mdoc.nfc.mdocReaderNfcHandover
import org.multipaz.mdoc.request.DeviceRequestGenerator
import org.multipaz.mdoc.response.DeviceResponseParser
import org.multipaz.mdoc.role.MdocRole
import org.multipaz.mdoc.sessionencryption.SessionEncryption
import org.multipaz.mdoc.transport.MdocTransportOptions
import org.multipaz.mdoc.transport.NfcTransportMdocReader
import id.walt.wallet2.mobile.peer.SelectCorrectedNfcReader
import org.multipaz.nfc.*
import org.multipaz.util.Logger
import java.security.cert.CertificateFactory
import java.security.cert.CertPathValidator
import java.security.cert.PKIXParameters
import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

/** Production coordinator, APDU router, consent and signer against Multipaz 0.100.0. No radio. */
class IndependentNfcReaderTest {
    private var previousLogger: Logger.LogPrinter? = null

    @org.junit.Before
    fun suppressPeerPayloadLogging() {
        previousLogger = Logger.logPrinter
        Logger.logPrinter = Logger.LogPrinter { _, _, _, _ -> }
    }

    @org.junit.After
    fun restorePeerLogging() { Logger.logPrinter = previousLogger }

    @Test
    fun stockPeerRetrievalSelectIsRejectedAndCorrectedSelectCanRecover() = runTest(timeout = 60.seconds) {
        withHarness { harness ->
            val stock = NfcTransportMdocReader(MdocRole.MDOC_READER, MdocTransportOptions(), harness.method)
            stock.setTag(harness.host)
            try {
                assertFailsWith<org.multipaz.mdoc.transport.MdocTransportException> { stock.open(harness.deviceKey) }
                val rejected = harness.host.exchanges.last()
                assertEquals(0, rejected.first.p2)
                assertEquals(0x6a86, rejected.second.status)
                assertFalse(harness.session.state.value is ProximityState.ReviewRequired)
            } finally {
                stock.close()
            }
            harness.open()
            val review = harness.request(setOf("given_name"))
            harness.approve(review, setOf("given_name"))
            harness.verifyResponse(mapOf("given_name" to "Ada"), 20)
        }
    }

    @Test
    fun staticHandoverAndFragmentedResponseAreIndependentlyAuthenticated() = runTest(timeout = 60.seconds) {
        withHarness { harness ->
            harness.open()
            val review = harness.request(setOf("given_name", "family_name"))
            harness.approve(review, setOf("given_name", "family_name"))
            harness.verifyResponse(mapOf("given_name" to "Ada", "family_name" to "Lovelace"), 20)
            assertTrue(harness.host.exchanges.any { it.first.ins == Nfc.INS_GET_RESPONSE }, "Response must actually fragment")
            assertIs<ProximityState.Completed>(harness.session.state.first { it is ProximityState.Completed })
            harness.assertResponseOrdering()
        }
    }

    @Test
    fun requestTwoRetainsConnectionCountersAndRequiresNewSelectiveConsent() = runTest(timeout = 60.seconds) {
        withHarness { harness ->
            harness.open()
            val first = harness.request(setOf("given_name", "family_name"))
            harness.approve(first, setOf("given_name"), continueAfterResponse = true)
            harness.verifyResponse(mapOf("given_name" to "Ada"), null)
            assertEquals(1, assertIs<ProximityState.AwaitingNextRequest>(
                harness.session.state.first { it is ProximityState.AwaitingNextRequest }).completedExchanges)
            val second = harness.request(setOf("family_name"))
            assertNotEquals(first.reviewId, second.reviewId)
            assertEquals(2, second.exchange)
            assertIs<ProximityActionResult.Rejected>(harness.session.dispatch(ProximityAction.Approve(
                first.reviewId, harness.submission(setOf("given_name"), false))))
            assertEquals(second.reviewId, assertIs<ProximityState.ReviewRequired>(harness.session.state.value).review.reviewId)
            harness.approve(second, setOf("family_name"))
            harness.verifyResponse(mapOf("family_name" to "Lovelace"), 20)
            assertEquals(1, harness.host.exchanges.count { it.first.ins == Nfc.INS_SELECT &&
                it.first.payload == Nfc.ISO_MDOC_NFC_DATA_TRANSFER_APPLICATION_ID })
            harness.assertResponseOrdering()
        }
    }

    @Test
    fun tamperedCiphertextIsRejectedWithoutConsentAndFreshSessionRecovers() = runTest(timeout = 60.seconds) {
        withReusableFixture { shared ->
            withHarness(shared) { harness ->
                harness.open()
                val original = Cbor.decode(harness.cipher.encryptMessage(harness.requestBytes(setOf("given_name")), null))
                val corrupted = original["data"].asBstr.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
                val encoded = Cbor.encode(buildCborMap {
                    original.asMap.forEach { (key, value) -> put(key, if (key == Tstr("data")) Bstr(corrupted) else value) }
                })
                harness.reader.sendMessage(encoded)
                val (plaintext, status) = harness.cipher.decryptMessage(harness.reader.waitForMessage())
                assertNull(plaintext)
                assertEquals(10L, status)
                assertFalse(harness.session.state.value is ProximityState.ReviewRequired)
                assertFalse(harness.host.exchanges.any { it.first.ins == Nfc.INS_GET_RESPONSE })
            }
            withHarness(shared) { harness ->
                harness.open()
                val review = harness.request(setOf("given_name"))
                harness.approve(review, setOf("given_name"))
                harness.verifyResponse(mapOf("given_name" to "Ada"), 20)
            }
        }
    }

    @Test
    fun cancelledReviewCannotDiscloseAndFreshSessionRecovers() = runTest(timeout = 60.seconds) {
        withReusableFixture { shared ->
            withHarness(shared) { harness ->
                harness.open()
                val review = harness.request(setOf("given_name"))
                assertEquals(ProximityActionResult.Accepted, harness.session.dispatch(ProximityAction.Cancel))
                assertIs<ProximityState.Cancelled>(harness.session.state.first { it is ProximityState.Cancelled })
                assertIs<ProximityActionResult.Rejected>(harness.session.dispatch(ProximityAction.Approve(
                    review.reviewId, harness.submission(setOf("given_name"), false))))
                // Any successful ENVELOPE data would be disclosed bytes; cancelled pending APDUs must not return them.
                assertFalse(harness.host.exchanges.any { it.first.ins == Nfc.INS_ENVELOPE && it.second.payload.size > 0 })
            }
            withHarness(shared) { harness ->
                harness.open()
                val review = harness.request(setOf("family_name"))
                harness.approve(review, setOf("family_name"))
                harness.verifyResponse(mapOf("family_name" to "Lovelace"), 20)
            }
        }
    }

    private suspend fun withReusableFixture(block: suspend (Fixture) -> Unit) {
        val fixture = fixture()
        try { block(fixture) } finally { fixture.runtime.close() }
    }

    private suspend fun withHarness(shared: Fixture? = null, block: suspend (Harness) -> Unit) = withContext(Dispatchers.Default) {
        val fixture = shared ?: fixture()
        val host = LoopbackHost()
        val session = ProximityCoordinator(fixture.wallet, null, host).start(configuration())
        try {
            withTimeout(30.seconds) {
                session.state.first { it is ProximityState.EngagementReady }
                val handover = assertNotNull(mdocReaderNfcHandover(host, emptyList()))
                val engagement = EngagementParser(handover.encodedDeviceEngagement.toByteArray()).parse()
                val readerKey = Crypto.createEcPrivateKey(PeerCurve.P256)
                val transcript = Cbor.encode(buildCborArray {
                    add(Tagged(24, Bstr(handover.encodedDeviceEngagement.toByteArray())))
                    add(Tagged(24, Bstr(Cbor.encode(readerKey.publicKey.toCoseKey().toDataItem()))))
                    add(handover.handover)
                })
                val method = handover.connectionMethods.filterIsInstance<MdocConnectionMethodNfc>().single()
                val created = Harness(fixture, host, session, method, engagement.eSenderKey, transcript,
                    SessionEncryption(MdocRole.MDOC_READER, readerKey, engagement.eSenderKey, transcript))
                try { block(created) } finally { created.reader.close() }
            }
        } finally {
            host.close()
            session.close()
            if (shared == null) fixture.runtime.close()
        }
    }

    private inner class Harness(
        val fixture: Fixture,
        val host: LoopbackHost,
        val session: ProximitySession,
        val method: MdocConnectionMethodNfc,
        val deviceKey: org.multipaz.crypto.EcPublicKey,
        val transcript: ByteArray,
        val cipher: SessionEncryption,
    ) {
        val reader = SelectCorrectedNfcReader(MdocRole.MDOC_READER, MdocTransportOptions(), method).also { it.setTag(host) }

        suspend fun open() { reader.open(deviceKey) }

        suspend fun requestBytes(fields: Set<String>) = DeviceRequestGenerator(transcript).addDocumentRequest(
            DOC_TYPE, mapOf(NAMESPACE to fields.associateWith { false }), null, null, Algorithm.UNSET, null,
        ).generate()

        suspend fun request(fields: Set<String>): ProximityReview {
            reader.sendMessage(cipher.encryptMessage(requestBytes(fields), null))
            val review = assertIs<ProximityState.ReviewRequired>(session.state.first { it is ProximityState.ReviewRequired }).review
            assertEquals(ProximityConnectedRoute(ProximityEngagementMethod.Nfc, ProximityTransport.Nfc), session.connectedRoute)
            assertEquals(fields, review.documents.single().credentialOptions.single().requestedElements.map { it.elementIdentifier }.toSet())
            return review
        }

        fun submission(fields: Set<String>, continueAfterResponse: Boolean) = ProximitySubmission(listOf(
            ProximityDocumentSubmission(0, "peer-mdl", fields.map { ProximityElementReference(NAMESPACE, it) }.toSet()),
        ), continueAfterResponse)

        suspend fun approve(review: ProximityReview, fields: Set<String>, continueAfterResponse: Boolean = false) {
            assertEquals(ProximityActionResult.Accepted, session.dispatch(ProximityAction.Approve(
                review.reviewId, submission(fields, continueAfterResponse))))
        }

        suspend fun verifyResponse(expected: Map<String, String>, expectedStatus: Long?) {
            val (plaintext, status) = cipher.decryptMessage(reader.waitForMessage())
            assertEquals(expectedStatus, status)
            val response = DeviceResponseParser(assertNotNull(plaintext), transcript).parse()
            val document = response.documents.single()
            assertEquals(DOC_TYPE, document.docType)
            assertTrue(document.issuerSignedAuthenticated)
            assertTrue(document.deviceSignedAuthenticated)
            assertTrue(document.deviceSignedAuthenticatedViaSignature)
            assertEquals(0, document.numIssuerEntryDigestMatchFailures)
            assertEquals(expected.keys, document.getIssuerEntryNames(NAMESPACE).toSet())
            expected.forEach { (field, value) -> assertEquals(value, document.getIssuerEntryString(NAMESPACE, field)) }
            // Signature validity and issuer trust are separate acceptance conditions.
            val certificates = document.issuerCertificateChain.certificates.map { certificate(it.encoded.toByteArray()) }
            val path = CertificateFactory.getInstance("X.509").generateCertPath(certificates)
            fun validate(root: ByteArray) = CertPathValidator.getInstance("PKIX").validate(path,
                PKIXParameters(setOf(TrustAnchor(certificate(root), null))).apply { isRevocationEnabled = false })
            validate(fixture.root)
            assertFailsWith<java.security.cert.CertPathValidatorException> { validate(fixture.untrustedRoot) }
        }

        fun assertResponseOrdering() {
            val retrieval = host.exchanges.filter { it.first.ins in setOf(Nfc.INS_ENVELOPE, Nfc.INS_GET_RESPONSE) }
            retrieval.forEachIndexed { index, (command, response) ->
                if (command.le > 0) assertTrue(response.payload.size <= command.le)
                if (response.sw1 == 0x61) {
                    val next = retrieval[index + 1].first
                    assertEquals(Nfc.INS_GET_RESPONSE, next.ins)
                    assertEquals(if (response.sw2 == 0) method.responseDataFieldMaxLength.toInt() else response.sw2, next.le)
                }
            }
            assertEquals(0x9000, retrieval.last().second.status)
        }
    }

    private class LoopbackHost : NfcIsoTag(), NfcHostPlatformAdapter {
        override val maxTransceiveLength = 512
        lateinit var router: NfcHostApduRouter
        val exchanges = mutableListOf<Pair<CommandApdu, ResponseApdu>>()
        private var delivered = CompletableDeferred(Unit)
        override suspend fun capability() = NfcHostAvailability.Available
        override suspend fun prepare(router: NfcHostApduRouter, sessionScope: CoroutineScope): NfcHostPreparation {
            this.router = router
            return NfcHostPreparation.Ready(object : PreparedNfcHostSession {
                override suspend fun close(reason: id.walt.mdoc.proximity.ProximityCloseReason) {
                    if (reason == id.walt.mdoc.proximity.ProximityCloseReason.COMPLETED) delivered.await()
                }
            })
        }
        override suspend fun transceive(command: CommandApdu): ResponseApdu {
            assertTrue(command.encode().size <= maxTransceiveLength)
            if (command.ins == Nfc.INS_ENVELOPE && command.cla == 0) delivered = CompletableDeferred()
            return ResponseApdu.decode(router.process(command.encode()).copy()).also {
                assertTrue(it.encode().size <= maxTransceiveLength)
                exchanges += command to it
                if (command.ins in setOf(Nfc.INS_ENVELOPE, Nfc.INS_GET_RESPONSE) && command.cla == 0 && it.status == 0x9000) {
                    delivered.complete(Unit)
                }
            }
        }
        override suspend fun close() {
            delivered.complete(Unit)
            if (::router.isInitialized) router.deactivate()
        }
        override suspend fun updateDialogMessage(message: String) = Unit
    }

    private data class Fixture(val wallet: Wallet, val runtime: CryptoRuntime, val root: ByteArray, val untrustedRoot: ByteArray)

    private suspend fun fixture(): Fixture {
        val runtime = CryptoRuntime(defaultSoftwareKeyProviders())
        suspend fun key(id: String) = runtime.generateSoftwareKey(GenerateSoftwareKeyRequest(
            id = KeyId(id), spec = KeySpec.Ec(EcCurve.P256), usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY)))
        val holder = key("peer-holder")
        val issuer = key("peer-issuer")
        val root = key("peer-root")
        val algorithm = SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256, EcdsaSignatureEncoding.DER)
        val rootCertificate = X509CertificateUtil.createSelfSignedCertificate(root, algorithm) {
            subjectDn = "CN=Independent peer test root"
        }
        val untrustedRoot = X509CertificateUtil.createSelfSignedCertificate(key("untrusted-root"), algorithm) {
            subjectDn = "CN=Untrusted peer test root"
        }
        val signer = X509CertificateUtil.createCertificate(root, rootCertificate, algorithm) {
            profileDocumentSignerCertificate(crlDistributionPointUri = "https://issuer.example/crl",
                issuerUri = "https://issuer.example", subjectKey = issuer, subjectDnCountryCode = "AT",
                subjectDnOrganizationName = "Synthetic test", subjectDnCommonName = "Peer document signer")
        }
        val issued = MdocIssuer.issueUniversal(issuerKey = issuer, signatureAlgorithm = Cose.Algorithm.ES256,
            issuerCertificate = listOf(CoseCertificate(signer.encodedDer.toByteArray())),
            holderKey = assertIs<EncodedKey.Jwk>(assertNotNull(holder.capabilities.publicKeyExporter).exportPublicKey()).toCoseKey(),
            docType = DOC_TYPE, data = MdocIssuer.MdocUniversalIssuanceData(mapOf(NAMESPACE to JsonObject(mapOf(
                "given_name" to JsonPrimitive("Ada"), "family_name" to JsonPrimitive("Lovelace"),
            )))))
        val wallet = Wallet("independent-peer", keyStores = listOf(InMemoryKeyStore().also { it.addCrypto2Key(holder) }),
            credentialStores = listOf(InMemoryCredentialStore()))
        val encoded = coseCompliantCbor.encodeToByteArray(Document.serializer(), Document(DOC_TYPE, issued)).encodeToBase64Url()
        wallet.addCredential(wallet.withImportedHolderKeyBinding(StoredCredential("peer-mdl", CredentialParser.detectAndParse(encoded).second)))
        return Fixture(wallet, runtime, rootCertificate.encodedDer.toByteArray(), untrustedRoot.encodedDer.toByteArray())
    }

    private fun certificate(der: ByteArray) = CertificateFactory.getInstance("X.509")
        .generateCertificate(der.inputStream()) as X509Certificate

    private fun configuration() = ProximityConfiguration(session = ProximitySessionConfiguration.ConventionalNfc(
        handover = ProximityNfcHandover.Static,
        retrieval = ProximityRetrievalOptions(bluetoothLowEnergy = null, nfc = ProximityNfcRetrievalConfiguration(maximumCommandDataLength = 255, maximumResponseDataLength = 256)),
    ))

    private companion object {
        const val NAMESPACE = "org.iso.18013.5.1"
        const val DOC_TYPE = "org.iso.18013.5.1.mDL"
    }
}
