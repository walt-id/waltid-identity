@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class, ExperimentalUnsignedTypes::class)

package id.walt.mdoc.proximity

import id.walt.cose.Cose
import id.walt.cose.toCoseKey
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.MontgomeryCurve
import id.walt.crypto2.keys.HpkeCiphertext
import id.walt.crypto2.hpke.Hpke
import id.walt.crypto2.serialization.BinaryData
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.CryptoOperation
import id.walt.crypto2.providers.CryptoRequirement
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.mdoc.crypto.MdocCrypto
import id.walt.mdoc.crypto.MdocCryptoHelper
import id.walt.mdoc.objects.SessionTranscript
import id.walt.mdoc.encoding.ByteStringWrapper
import id.walt.mdoc.objects.deviceretrieval.ElementReference
import id.walt.mdoc.objects.deviceretrieval.EncryptedDocumentsPlaintext
import id.walt.mdoc.objects.deviceretrieval.EncryptionParameters
import id.walt.mdoc.objects.document.Document
import id.walt.mdoc.objects.document.DeviceAuth
import id.walt.mdoc.objects.document.IssuerSigned
import id.walt.mdoc.objects.elements.DeviceNameSpaces
import id.walt.mdoc.objects.elements.DeviceSignedItem
import id.walt.mdoc.objects.elements.DeviceSignedItemList
import id.walt.mdoc.objects.handover.NFCHandover
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.cbor.CborString
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MdocResponseBuilderTest {
    private val runtime = CryptoRuntime(defaultSoftwareKeyProviders())

    @Test
    fun `signature and MAC responses bind exact namespaces and the MSO holder key`() = runTest {
        val signatureHolderKey = key("presentation-signature-holder", setOf(KeyUsage.SIGN, KeyUsage.VERIFY))
        val macHolderKey = key("presentation-mac-holder", setOf(KeyUsage.KEY_AGREEMENT))
        val readerKey = key("presentation-reader", setOf(KeyUsage.KEY_AGREEMENT))
        val issued = issue(signatureHolderKey)
        val signatureSource = issued.copy(
            issuerSigned = IssuerSigned.fromIssuerSignedLists(
                namespaces = issued.issuerSigned.namespaces!!,
                issuerAuth = issued.issuerSigned.issuerAuth,
                extensions = mapOf("futureIssuerSigned" to CborString("issuer")),
            ),
            extensions = mapOf("futureDocument" to CborString("document")),
        )
        val macSource = issue(macHolderKey)
        val readerPublic = (readerKey.capabilities.publicKeyExporter!!.exportPublicKey() as EncodedKey.Jwk).toCoseKey()
        val transcript = SessionTranscript.forQr(byteArrayOf(1, 2), byteArrayOf(3, 4))
        val selection = setOf(ElementReference("org.example", "given_name"))
        val builder = MdocResponseBuilder()

        assertTrue(macHolderKey.supportsMdocDeviceMac(readerPublic))
        assertFalse(signatureHolderKey.supportsMdocDeviceMac(readerPublic))

        val signatureDocument = builder.buildDocument(
            MdocDocumentPresentation(
                signatureSource,
                signatureHolderKey,
                selection,
                authentication = MdocAuthenticationMethod.Signature(),
            ),
            transcript,
        )
        assertEquals(CborString("document"), signatureDocument.extensions["futureDocument"])
        assertEquals(CborString("issuer"), signatureDocument.issuerSigned.extensions["futureIssuerSigned"])
        val originalItem = signatureSource.issuerSigned.namespaces!!["org.example"]!!.entries.single()
        val disclosedItem = signatureDocument.issuerSigned.namespaces!!["org.example"]!!.entries.single()
        assertContentEquals(originalItem.serialized, disclosedItem.serialized)
        val signaturePayload = MdocCryptoHelper.buildDeviceAuthenticationBytes(
            transcript,
            signatureDocument.docType,
            signatureDocument.deviceSigned!!.namespaces,
        )
        val devicePublic = MdocCrypto.coseKeyToCrypto2Key(
            signatureSource.issuerSigned.decodeMobileSecurityObject().deviceKeyInfo.deviceKey
        )
        assertTrue(
            MdocCrypto.verifyDeviceSignature(
                signaturePayload,
                assertIs<DeviceAuth.Signature>(signatureDocument.deviceSigned!!.deviceAuth).signature,
                devicePublic,
                setOf(Cose.Algorithm.ES256),
            )
        )

        val macDocument = builder.buildDocument(
            MdocDocumentPresentation(
                macSource,
                macHolderKey,
                selection,
                authentication = MdocAuthenticationMethod.Mac(readerPublic),
            ),
            transcript,
        )
        val macPayload = MdocCryptoHelper.buildDeviceAuthenticationBytes(
            transcript,
            macDocument.docType,
            macDocument.deviceSigned!!.namespaces,
        )
        assertTrue(
            MdocCrypto.verifyDeviceMac(
                macPayload,
                assertIs<DeviceAuth.Mac>(macDocument.deviceSigned!!.deviceAuth).mac,
                MdocCryptoHelper.buildSessionTranscriptBytes(transcript),
                readerKey,
                macSource.issuerSigned.decodeMobileSecurityObject().deviceKeyInfo.deviceKey,
            )
        )
        val tamperedMac = assertIs<DeviceAuth.Mac>(macDocument.deviceSigned!!.deviceAuth).mac.let { mac ->
            mac.copy(tag = mac.tag.copyOf().also { it[it.lastIndex] = (it.last() + 1).toByte() })
        }
        assertFalse(
            MdocCrypto.verifyDeviceMac(
                macPayload,
                tamperedMac,
                MdocCryptoHelper.buildSessionTranscriptBytes(transcript),
                readerKey,
                macSource.issuerSigned.decodeMobileSecurityObject().deviceKeyInfo.deviceKey,
            )
        )
    }

    @Test
    fun `signature and MAC authentication bind QR static NFC and negotiated NFC transcripts`() = runTest {
        val signatureHolderKey = key("handover-signature-holder", setOf(KeyUsage.SIGN, KeyUsage.VERIFY))
        val macHolderKey = key("handover-mac-holder", setOf(KeyUsage.KEY_AGREEMENT))
        val readerKey = key("handover-reader", setOf(KeyUsage.KEY_AGREEMENT))
        val signatureSource = issue(signatureHolderKey)
        val macSource = issue(macHolderKey)
        val readerPublic = (readerKey.capabilities.publicKeyExporter!!.exportPublicKey() as EncodedKey.Jwk).toCoseKey()
        val selection = setOf(ElementReference("org.example", "given_name"))
        val transcripts = linkedMapOf(
            "SM_DEVICE_AUTH_QR" to SessionTranscript.forQr(byteArrayOf(1, 2), byteArrayOf(3, 4)),
            "SM_DEVICE_AUTH_NFC_STATIC" to SessionTranscript.forNfc(
                byteArrayOf(1, 2),
                byteArrayOf(3, 4),
                NFCHandover(handoverSelect = byteArrayOf(5), handoverRequest = null),
            ),
            "SM_DEVICE_AUTH_NFC_NEGOTIATED" to SessionTranscript.forNfc(
                byteArrayOf(1, 2),
                byteArrayOf(3, 4),
                NFCHandover(handoverSelect = byteArrayOf(5), handoverRequest = byteArrayOf(6)),
            ),
        )
        val builder = MdocResponseBuilder()

        transcripts.forEach { (scenario, transcript) ->
            val signatureDocument = builder.buildDocument(
                MdocDocumentPresentation(
                    signatureSource,
                    signatureHolderKey,
                    selection,
                    authentication = MdocAuthenticationMethod.Signature(),
                ),
                transcript,
            )
            val signaturePayload = MdocCryptoHelper.buildDeviceAuthenticationBytes(
                transcript,
                signatureDocument.docType,
                signatureDocument.deviceSigned!!.namespaces,
            )
            val devicePublic = MdocCrypto.coseKeyToCrypto2Key(
                signatureSource.issuerSigned.decodeMobileSecurityObject().deviceKeyInfo.deviceKey
            )
            assertTrue(
                MdocCrypto.verifyDeviceSignature(
                    signaturePayload,
                    assertIs<DeviceAuth.Signature>(signatureDocument.deviceSigned!!.deviceAuth).signature,
                    devicePublic,
                    setOf(Cose.Algorithm.ES256),
                ),
                "$scenario:signature",
            )

            val macDocument = builder.buildDocument(
                MdocDocumentPresentation(
                    macSource,
                    macHolderKey,
                    selection,
                    authentication = MdocAuthenticationMethod.Mac(readerPublic),
                ),
                transcript,
            )
            val macPayload = MdocCryptoHelper.buildDeviceAuthenticationBytes(
                transcript,
                macDocument.docType,
                macDocument.deviceSigned!!.namespaces,
            )
            assertTrue(
                MdocCrypto.verifyDeviceMac(
                    macPayload,
                    assertIs<DeviceAuth.Mac>(macDocument.deviceSigned!!.deviceAuth).mac,
                    MdocCryptoHelper.buildSessionTranscriptBytes(transcript),
                    readerKey,
                    macSource.issuerSigned.decodeMobileSecurityObject().deviceKeyInfo.deviceKey,
                ),
                "$scenario:MAC",
            )
        }
    }

    @Test
    fun `device MAC supports every provider-backed XDH session curve`() = runTest {
        val specs = listOf(MontgomeryCurve.X25519, MontgomeryCurve.X448)
            .map(KeySpec::Montgomery)
            .filter(::supportsAgreementKeyGeneration)
        for ((index, spec) in specs.withIndex()) {
            val holderKey = key(
                "xdh-mac-holder-$index",
                setOf(KeyUsage.KEY_AGREEMENT),
                spec,
            )
            val readerKey = key(
                "xdh-mac-reader-$index",
                setOf(KeyUsage.KEY_AGREEMENT),
                spec,
            )
            val source = issue(holderKey)
            val readerPublic = (readerKey.capabilities.publicKeyExporter!!.exportPublicKey() as EncodedKey.Jwk)
                .toCoseKey()
            val transcript = SessionTranscript.forQr(byteArrayOf(1, 2), byteArrayOf(3, 4))
            val document = MdocResponseBuilder().buildDocument(
                MdocDocumentPresentation(
                    source,
                    holderKey,
                    setOf(ElementReference("org.example", "given_name")),
                    authentication = MdocAuthenticationMethod.Mac(readerPublic),
                ),
                transcript,
            )
            val payload = MdocCryptoHelper.buildDeviceAuthenticationBytes(
                transcript,
                document.docType,
                document.deviceSigned!!.namespaces,
            )

            assertTrue(
                MdocCrypto.verifyDeviceMac(
                    payload,
                    assertIs<DeviceAuth.Mac>(document.deviceSigned!!.deviceAuth).mac,
                    MdocCryptoHelper.buildSessionTranscriptBytes(transcript),
                    readerKey,
                    source.issuerSigned.decodeMobileSecurityObject().deviceKeyInfo.deviceKey,
                )
            )
        }
    }

    @Test
    fun `response builder rejects an unrelated wallet key`() = runTest {
        val holderKey = key("bound-holder", setOf(KeyUsage.SIGN, KeyUsage.VERIFY, KeyUsage.KEY_AGREEMENT))
        val wrongKey = key("wrong-holder", setOf(KeyUsage.SIGN, KeyUsage.VERIFY, KeyUsage.KEY_AGREEMENT))
        val source = issue(holderKey)

        assertFailsWith<IllegalArgumentException> {
            MdocResponseBuilder().buildDocument(
                MdocDocumentPresentation(
                    source,
                    wrongKey,
                    setOf(ElementReference("org.example", "given_name")),
                    authentication = MdocAuthenticationMethod.Signature(),
                ),
                SessionTranscript.forQr(byteArrayOf(1), byteArrayOf(2)),
            )
        }
    }

    @Test
    fun `response builder accepts holder keys whose public exporter defaults to SPKI`() = runTest {
        val holderKey = key("spki-holder", setOf(KeyUsage.SIGN, KeyUsage.VERIFY))
        val source = issue(holderKey)
        val spkiHolderKey = holderKey.withSpkiPublicExport()

        val document = MdocResponseBuilder().buildDocument(
            MdocDocumentPresentation(
                source = source,
                holderKey = spkiHolderKey,
                selectedIssuerElements = setOf(ElementReference("org.example", "given_name")),
                authentication = MdocAuthenticationMethod.Signature(),
            ),
            SessionTranscript.forQr(byteArrayOf(1), byteArrayOf(2)),
        )

        assertIs<DeviceAuth.Signature>(document.deviceSigned!!.deviceAuth)
    }

    @Test
    fun `response builder enforces device namespace authorization algorithms and precise errors`() = runTest {
        val holderKey = key("policy-holder", setOf(KeyUsage.SIGN, KeyUsage.VERIFY, KeyUsage.KEY_AGREEMENT))
        val source = issue(holderKey)
        val selection = setOf(ElementReference("org.example", "given_name"))
        val transcript = SessionTranscript.forQr(byteArrayOf(1), byteArrayOf(2))
        val builder = MdocResponseBuilder()

        assertFailsWith<IllegalArgumentException> {
            builder.buildDocument(
                MdocDocumentPresentation(
                    source,
                    holderKey,
                    selection,
                    deviceNameSpaces = DeviceNameSpaces(
                        mapOf("org.example.device" to DeviceSignedItemList(listOf(DeviceSignedItem("age", 21))))
                    ),
                    authentication = MdocAuthenticationMethod.Signature(),
                ),
                transcript,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            builder.buildDocument(
                MdocDocumentPresentation(
                    source,
                    holderKey,
                    selection,
                    authentication = MdocAuthenticationMethod.Signature(setOf(Cose.Algorithm.HMAC_256)),
                ),
                transcript,
            )
        }

        val response = builder.buildResponse(
            presentations = listOf(
                MdocDocumentPresentation(
                    source,
                    holderKey,
                    selection,
                    elementErrors = mapOf("org.example" to mapOf("portrait" to -1L)),
                    authentication = MdocAuthenticationMethod.Signature(),
                )
            ),
            transcript = transcript,
            documentErrors = listOf(MdocDocumentError("org.example.other", 0L)),
        )
        assertEquals(-1L, response.documents!!.single().errors!!["org.example"]!!["portrait"])
        assertEquals(0L, response.documentErrors!!.single()["org.example.other"])
    }

    @Test
    fun `document response encryption binds HPKE and device authentication to the substituted transcript`() = runTest {
        val holderKey = key("encrypted-holder", setOf(KeyUsage.SIGN, KeyUsage.VERIFY))
        val readerKey = key("encrypted-reader", setOf(KeyUsage.KEY_AGREEMENT))
        val readerPublic = (readerKey.capabilities.publicKeyExporter!!.exportPublicKey() as EncodedKey.Jwk).toCoseKey()
        val parameters = EncryptionParameters(readerPublic)
        val exactParameters = id.walt.cose.coseCompliantCbor.encodeToByteArray(parameters)
        val wrappedParameters = ByteStringWrapper(parameters, exactParameters)
        val transcript = SessionTranscript.forQr(byteArrayOf(1, 2), byteArrayOf(3, 4))
        val source = issue(holderKey)
        val encrypted = MdocResponseBuilder().buildEncryptedDocuments(
            docRequestId = 7u,
            presentations = listOf(
                MdocDocumentPresentation(
                    source = source,
                    holderKey = holderKey,
                    selectedIssuerElements = setOf(ElementReference("org.example", "given_name")),
                    authentication = MdocAuthenticationMethod.Signature(),
                )
            ),
            transcript = transcript,
            encryptionParameters = wrappedParameters,
        )
        val encryptionTranscript = transcript.withDocumentEncryptionParameters(exactParameters)
        val plaintext = Hpke.openBase(
            recipientKey = readerKey,
            ciphertext = HpkeCiphertext(
                suite = Hpke.P256_HKDF_SHA256_AES_128_GCM,
                encapsulatedKey = BinaryData(encrypted.enc),
                ciphertext = BinaryData(encrypted.cipherText),
            ),
            info = id.walt.cose.coseCompliantCbor.encodeToByteArray(encryptionTranscript),
        )
        val document = id.walt.cose.coseCompliantCbor
            .decodeFromByteArray<EncryptedDocumentsPlaintext>(plaintext)
            .documents!!.single()
        val authentication = assertIs<DeviceAuth.Signature>(document.deviceSigned!!.deviceAuth).signature
        val devicePublic = MdocCrypto.coseKeyToCrypto2Key(
            source.issuerSigned.decodeMobileSecurityObject().deviceKeyInfo.deviceKey
        )

        assertEquals(7u, encrypted.docRequestID)
        assertTrue(
            MdocCrypto.verifyDeviceSignature(
                MdocCryptoHelper.buildDeviceAuthenticationBytes(
                    encryptionTranscript,
                    document.docType,
                    document.deviceSigned!!.namespaces,
                ),
                authentication,
                devicePublic,
                setOf(Cose.Algorithm.ES256),
            )
        )
        assertFalse(
            MdocCrypto.verifyDeviceSignature(
                MdocCryptoHelper.buildDeviceAuthenticationBytes(
                    transcript,
                    document.docType,
                    document.deviceSigned!!.namespaces,
                ),
                authentication,
                devicePublic,
                setOf(Cose.Algorithm.ES256),
            )
        )
    }

    private suspend fun issue(holderKey: id.walt.crypto2.keys.Key): Document =
        runtime.issueMdocTestDocument(holderKey)

    private suspend fun key(
        id: String,
        usages: Set<KeyUsage>,
        spec: KeySpec = KeySpec.Ec(EcCurve.P256),
    ) = runtime.generateSoftwareKey(GenerateSoftwareKeyRequest(KeyId(id), spec, usages))

    private fun supportsAgreementKeyGeneration(spec: KeySpec): Boolean = runCatching {
        runtime.resolveSoftwareProvider(
            CryptoRequirement(
                operation = CryptoOperation.GENERATE_KEY,
                spec = spec,
                usages = setOf(KeyUsage.KEY_AGREEMENT),
            )
        )
    }.isSuccess
}
