@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class, ExperimentalUnsignedTypes::class)

package id.walt.mdoc.proximity

import id.walt.cose.Cose
import id.walt.cose.CoseHeaders
import id.walt.cose.CoseKey
import id.walt.cose.CoseMac0
import id.walt.cose.CoseSign1
import id.walt.cose.coseCompliantCbor
import id.walt.mdoc.encoding.ByteStringWrapper
import id.walt.mdoc.encoding.ExactCbor
import id.walt.mdoc.objects.DeviceSigned
import id.walt.mdoc.objects.deviceretrieval.AlternativeDataElementsSet
import id.walt.mdoc.objects.deviceretrieval.DeviceRequest
import id.walt.mdoc.objects.deviceretrieval.DeviceRequestInfo
import id.walt.mdoc.objects.deviceretrieval.DeviceResponse
import id.walt.mdoc.objects.deviceretrieval.DocRequest
import id.walt.mdoc.objects.deviceretrieval.DocRequestInfo
import id.walt.mdoc.objects.deviceretrieval.ElementReference
import id.walt.mdoc.objects.deviceretrieval.EncryptedDocumentsPlaintext
import id.walt.mdoc.objects.deviceretrieval.EncryptionParameters
import id.walt.mdoc.objects.deviceretrieval.ItemRequest
import id.walt.mdoc.objects.deviceretrieval.ItemsRequest
import id.walt.mdoc.objects.deviceretrieval.ItemsRequestList
import id.walt.mdoc.objects.deviceretrieval.UseCase
import id.walt.mdoc.objects.deviceretrieval.ZkDocument
import id.walt.mdoc.objects.deviceretrieval.ZkDocumentData
import id.walt.mdoc.objects.deviceretrieval.ZkRequest
import id.walt.mdoc.objects.deviceretrieval.ZkSignedItem
import id.walt.mdoc.objects.deviceretrieval.ZkSystemSpec
import id.walt.mdoc.objects.document.DeviceAuth
import id.walt.mdoc.objects.document.Document
import id.walt.mdoc.objects.document.IssuerSigned
import id.walt.mdoc.objects.elements.DeviceNameSpaces
import id.walt.mdoc.objects.elements.IssuerSignedItem
import id.walt.mdoc.objects.engagement.BleCentralMode
import id.walt.mdoc.objects.engagement.BlePeripheralEndpoint
import id.walt.mdoc.objects.engagement.BlePeripheralMode
import id.walt.mdoc.objects.engagement.BlePeripheralServerOptions
import id.walt.mdoc.objects.engagement.DeviceEngagement
import id.walt.mdoc.objects.engagement.DeviceEngagementCapabilities
import id.walt.mdoc.objects.engagement.DeviceEngagementSecurity
import id.walt.mdoc.objects.engagement.DeviceRetrievalMethod
import id.walt.mdoc.objects.engagement.DeviceRetrievalMethodCodec
import id.walt.mdoc.objects.session.SessionData
import id.walt.mdoc.objects.session.SessionEstablishment
import kotlinx.datetime.LocalDate
import kotlinx.serialization.cbor.CborByteString
import kotlinx.serialization.cbor.CborElement
import kotlinx.serialization.cbor.CborInteger
import kotlinx.serialization.cbor.CborMap
import kotlinx.serialization.cbor.CborString
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ProtocolModelsTest {
    private val publicKey = CoseKey(
        kty = Cose.KeyTypes.EC2,
        crv = Cose.EllipticCurves.P_256,
        x = ByteArray(32) { it.toByte() },
        y = ByteArray(32) { (it + 1).toByte() },
    )

    @Test
    fun `exact CBOR snapshots authenticated bytes on input and output`() {
        val source = byteArrayOf(0x01, 0x02, 0x03)
        val exact = ExactCbor.of("decoded", source)

        source[0] = 0x7f
        assertContentEquals(byteArrayOf(0x01, 0x02, 0x03), exact.encodedCopy())

        val exposed = exact.encodedCopy()
        exposed[1] = 0x7f
        assertContentEquals(byteArrayOf(0x01, 0x02, 0x03), exact.encodedCopy())
    }

    @Test
    fun `device engagement retains exact key bytes and unknown extensions`() {
        val encodedKey = coseCompliantCbor.encodeToByteArray(CoseKey.serializer(), publicKey)
        val engagement = DeviceEngagement(
            version = "1.1",
            security = DeviceEngagementSecurity(1u, ByteStringWrapper(publicKey, encodedKey)),
            deviceRetrievalMethods = listOf(
                DeviceRetrievalMethod.Ble(
                    peripheralMode = BlePeripheralMode(ByteArray(16) { it.toByte() }),
                )
            ),
            originInfos = emptyList(),
            capabilities = DeviceEngagementCapabilities(readerAuthAll = true, extendedRequests = true),
            extensions = mapOf(-1L to CborString("private")),
        )
        val encoded = coseCompliantCbor.encodeToByteArray(DeviceEngagement.serializer(), engagement)
        val decoded = coseCompliantCbor.decodeFromByteArray<DeviceEngagement>(encoded)

        assertContentEquals(encodedKey, decoded.security.eDeviceKey.serialized)
        assertEquals(CborString("private"), decoded.extensions[-1L])
        assertContentEquals(encoded, coseCompliantCbor.encodeToByteArray(DeviceEngagement.serializer(), decoded))
    }

    @Test
    fun `BLE peripheral endpoint ownership is explicit and context safe`() {
        val readerMethod = DeviceRetrievalMethod.Ble(
            centralMode = BleCentralMode(ByteArray(16) { it.toByte() }),
            peripheralEndpoint = BlePeripheralEndpoint.Reader(
                BlePeripheralServerOptions(
                    deviceAddress = byteArrayOf(1, 2, 3, 4, 5, 6),
                    psm = 0x81u,
                ),
            ),
        )
        val encodedReaderMethod = DeviceRetrievalMethodCodec.encodeReaderEngagement(readerMethod)

        assertEquals(readerMethod, DeviceRetrievalMethodCodec.decodeReaderEngagement(encodedReaderMethod))
        assertEquals(readerMethod, DeviceRetrievalMethodCodec.decode(DeviceRetrievalMethodCodec.encode(readerMethod)))
        val provisionalEngagement = DeviceEngagement(
            DeviceEngagement.VERSION_1_0,
            DeviceEngagementSecurity(
                1u,
                ByteStringWrapper(
                    publicKey,
                    coseCompliantCbor.encodeToByteArray(CoseKey.serializer(), publicKey),
                ),
            ),
            deviceRetrievalMethods = listOf(readerMethod),
        )
        assertEquals(
            provisionalEngagement,
            coseCompliantCbor.decodeFromByteArray<DeviceEngagement>(
                coseCompliantCbor.encodeToByteArray(DeviceEngagement.serializer(), provisionalEngagement),
            ),
        )
        assertFailsWith<IllegalArgumentException> {
            DeviceRetrievalMethod.Ble(
                centralMode = BleCentralMode(ByteArray(16)),
                peripheralEndpoint = BlePeripheralEndpoint.Mdoc(BlePeripheralServerOptions(psm = 1u)),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            DeviceRetrievalMethod.Ble(
                peripheralMode = BlePeripheralMode(ByteArray(16)),
                peripheralEndpoint = BlePeripheralEndpoint.Reader(BlePeripheralServerOptions(psm = 1u)),
            )
        }
        val dualReaderOffer = DeviceRetrievalMethod.Ble(
            peripheralMode = BlePeripheralMode(ByteArray(16)),
            centralMode = BleCentralMode(ByteArray(16) { (it + 1).toByte() }),
            peripheralEndpoint = BlePeripheralEndpoint.Reader(BlePeripheralServerOptions(psm = 1u)),
        )
        val encodedReaderOffer = DeviceRetrievalMethodCodec.encodeReaderEngagement(dualReaderOffer)
        assertEquals(dualReaderOffer, DeviceRetrievalMethodCodec.decodeReaderEngagement(encodedReaderOffer))
        assertFailsWith<IllegalArgumentException> { DeviceRetrievalMethodCodec.encode(dualReaderOffer) }
        assertFailsWith<IllegalArgumentException> {
            DeviceEngagement(
                DeviceEngagement.VERSION_1_0,
                DeviceEngagementSecurity(
                    1u,
                    ByteStringWrapper(
                        publicKey,
                        coseCompliantCbor.encodeToByteArray(CoseKey.serializer(), publicKey),
                    ),
                ),
                deviceRetrievalMethods = listOf(dualReaderOffer),
            )
        }
        assertFailsWith<IllegalArgumentException> { BlePeripheralServerOptions() }
        assertFailsWith<IllegalArgumentException> {
            BlePeripheralServerOptions(deviceAddress = ByteArray(5))
        }
    }

    @Test
    fun `authorized DIS device engagement vector decodes and re-encodes byte for byte`() {
        val vector = (
            "a30063312e30018201d818584ba4010220012158205a88d182bce5f42efa59943f33359d2e8a968ff289d93e5f" +
                "a444b624343167fe225820b16e8cf858ddc7690407ba61d4c338237a8cfcf3de6aa672fc60a557aa32fc670281" +
                "830201a300f401f50b5045efef742b2c4837a9a3b0e1d05a6917"
            ).hexToByteArray()
        MdocCborGuard.validate(vector, 32, 1_000)
        val decoded = coseCompliantCbor.decodeFromByteArray<DeviceEngagement>(vector)

        assertEquals(DeviceEngagement.VERSION_1_0, decoded.version)
        assertContentEquals(
            vector,
            coseCompliantCbor.encodeToByteArray(DeviceEngagement.serializer(), decoded),
        )

        val extendedKnownOptions = vector.copyOf(vector.size + 3)
        val optionsIndex = vector.indexOfSubsequence(byteArrayOf(0x83.toByte(), 0x02, 0x01, 0xa3.toByte())) + 3
        extendedKnownOptions[optionsIndex] = 0xa4.toByte()
        extendedKnownOptions[vector.size] = 0x18
        extendedKnownOptions[vector.size + 1] = 0x63
        extendedKnownOptions[vector.size + 2] = 0x00
        val extended = coseCompliantCbor.decodeFromByteArray<DeviceEngagement>(extendedKnownOptions)
        val ble = assertIs<DeviceRetrievalMethod.Ble>(extended.deviceRetrievalMethods!!.single())
        assertEquals(kotlinx.serialization.cbor.CborInteger(0), ble.extensions[99u])
        assertContentEquals(
            extendedKnownOptions,
            coseCompliantCbor.encodeToByteArray(DeviceEngagement.serializer(), extended),
        )
    }

    @Test
    fun `capabilities require origin infos and future retrieval versions stay opaque`() {
        val encodedKey = coseCompliantCbor.encodeToByteArray(CoseKey.serializer(), publicKey)
        assertFailsWith<IllegalArgumentException> {
            DeviceEngagement(
                version = "1.1",
                security = DeviceEngagementSecurity(1u, ByteStringWrapper(publicKey, encodedKey)),
                capabilities = DeviceEngagementCapabilities(readerAuthAll = true),
            )
        }

        val future = DeviceRetrievalMethod.Unknown(2u, 2u, CborMap(emptyMap()))
        val engagement = DeviceEngagement(
            version = "1.0",
            security = DeviceEngagementSecurity(1u, ByteStringWrapper(publicKey, encodedKey)),
            deviceRetrievalMethods = listOf(future),
        )
        val decoded = coseCompliantCbor.decodeFromByteArray<DeviceEngagement>(
            coseCompliantCbor.encodeToByteArray(DeviceEngagement.serializer(), engagement)
        )
        assertIs<DeviceRetrievalMethod.Unknown>(decoded.deviceRetrievalMethods!!.single())
    }

    @Test
    fun `authentication retrieval and request contracts reject impossible combinations`() {
        val signature = CoseSign1(byteArrayOf(), CoseHeaders(), null, byteArrayOf(1))
        val mac = CoseMac0(byteArrayOf(), CoseHeaders(), byteArrayOf(), byteArrayOf(1))
        val bothMethods = CborMap(
            mapOf(
                CborString("deviceSignature") to coseCompliantCbor.decodeFromByteArray(
                    CborElement.serializer(),
                    coseCompliantCbor.encodeToByteArray(CoseSign1.serializer(), signature),
                ),
                CborString("deviceMac") to coseCompliantCbor.decodeFromByteArray(
                    CborElement.serializer(),
                    coseCompliantCbor.encodeToByteArray(CoseMac0.serializer(), mac),
                ),
            )
        )
        val bothMethodsBytes = coseCompliantCbor.encodeToByteArray(CborElement.serializer(), bothMethods)
        val noMethodBytes = coseCompliantCbor.encodeToByteArray(CborElement.serializer(), CborMap(emptyMap()))

        assertFailsWith<kotlinx.serialization.SerializationException> {
            coseCompliantCbor.decodeFromByteArray<DeviceAuth>(bothMethodsBytes)
        }
        assertFailsWith<kotlinx.serialization.SerializationException> {
            coseCompliantCbor.decodeFromByteArray<DeviceAuth>(noMethodBytes)
        }
        assertFailsWith<IllegalArgumentException> { DeviceRetrievalMethod.Ble() }
        assertFailsWith<IllegalArgumentException> { BlePeripheralServerOptions(psm = 0u) }
        assertFailsWith<IllegalArgumentException> { BlePeripheralServerOptions(psm = 65_536u) }
        assertFailsWith<IllegalArgumentException> {
            DeviceRetrievalMethod.Unknown(2u, 1u, CborMap(emptyMap()))
        }
        assertFailsWith<IllegalArgumentException> { DeviceRetrievalMethod.Nfc(254u, 256u) }
        assertFailsWith<IllegalArgumentException> { DeviceRetrievalMethod.Nfc(255u, 257u + 65_280u) }
        assertFailsWith<IllegalArgumentException> { DeviceRetrievalMethod.NfcV2(0u) }
        assertFailsWith<IllegalArgumentException> { DeviceRetrievalMethod.NfcV2(65_537u) }
        assertFailsWith<kotlinx.serialization.SerializationException> {
            DeviceRetrievalMethodCodec.decode("830501a0".hexToByteArray())
        }

        val items = ItemsRequest(
            docType = "org.example.document",
            namespaces = mapOf("org.example" to ItemsRequestList(listOf(ItemRequest("name", false)))),
        )
        val docRequest = DocRequest(ByteStringWrapper(items))
        val invalidUseCase = DeviceRequestInfo(
            useCases = listOf(UseCase(mandatory = true, documentSets = listOf(listOf(1u)))),
        )
        assertFailsWith<IllegalArgumentException> {
            DeviceRequest(
                version = DeviceRequest.VERSION_WITH_SIGNING,
                docRequests = listOf(docRequest),
                deviceRequestInfo = ByteStringWrapper(invalidUseCase),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            ItemsRequest(
                docType = items.docType,
                namespaces = items.namespaces,
                requestInfo = DocRequestInfo(
                    alternativeDataElements = listOf(
                        AlternativeDataElementsSet(
                            requestedElement = ElementReference("org.example", "not-requested"),
                            alternativeElementSets = listOf(
                                listOf(ElementReference("org.example", "full_name"))
                            ),
                        )
                    )
                ),
            )
        }
        val duplicateSystem = ZkSystemSpec("proof", "example")
        assertFailsWith<IllegalArgumentException> {
            ZkRequest(zkRequired = true, systemSpecs = listOf(duplicateSystem, duplicateSystem))
        }
        assertFailsWith<IllegalArgumentException> { MdocAuthenticationMethod.Signature(emptySet()) }
    }

    @Test
    fun `guarded CBOR fails closed on ambiguous and nested input`() {
        assertFailsWith<MdocCborValidationException> {
            MdocCborGuard.validate(
                byteArrayOf(0xa2.toByte(), 0x61, 0x61, 0x01, 0x61, 0x61, 0x02),
                maximumDepth = 32,
                maximumItems = 100,
            )
        }
        assertFailsWith<MdocCborValidationException> {
            MdocCborGuard.validate(byteArrayOf(0x81.toByte(), 0x81.toByte(), 0x80.toByte()), 1, 100)
        }
        assertFailsWith<MdocCborValidationException> {
            MdocCborGuard.validate(byteArrayOf(0x01, 0x02), 32, 100)
        }
        assertFailsWith<MdocCborValidationException> {
            MdocCborGuard.validate(byteArrayOf(0x18, 0x01), 32, 100)
        }
        assertFailsWith<MdocCborValidationException> {
            MdocCborGuard.validate(byteArrayOf(0x9f.toByte(), 0x01, 0xff.toByte()), 32, 100)
        }
    }

    @Test
    fun `private engagement keys and capability downgrades are rejected`() {
        val privateKey = publicKey.copy(d = ByteArray(32))
        assertFailsWith<IllegalArgumentException> {
            DeviceEngagementSecurity(1u, ByteStringWrapper(privateKey, byteArrayOf(1)))
        }
        assertFailsWith<kotlinx.serialization.SerializationException> {
            coseCompliantCbor.decodeFromByteArray<DeviceEngagement>(
                byteArrayOf(
                    0xa3.toByte(), 0x00, 0x63, 0x31, 0x2e, 0x31,
                    0x01, 0x82.toByte(), 0x01, 0x40,
                    0x06, 0xa1.toByte(), 0x03, 0xf4.toByte(),
                )
            )
        }
    }

    @Test
    fun `request extension points are encoded as flat wire fields`() {
        val deviceInfo = DeviceRequestInfo(extensions = mapOf("futureDevice" to CborString("value")))
        val docInfo = DocRequestInfo(
            issuerIdentifiers = listOf(byteArrayOf(1, 2, 3)),
            extensions = mapOf("futureDocument" to CborString("value")),
        )
        val encodedDevice = coseCompliantCbor.encodeToByteArray(DeviceRequestInfo.serializer(), deviceInfo)
        val encodedDocument = coseCompliantCbor.encodeToByteArray(DocRequestInfo.serializer(), docInfo)
        val deviceMap = coseCompliantCbor.decodeFromByteArray<CborMap>(encodedDevice)
        val documentMap = coseCompliantCbor.decodeFromByteArray<CborMap>(encodedDocument)

        assertEquals(CborString("value"), deviceMap[CborString("futureDevice")])
        assertEquals(CborString("value"), documentMap[CborString("futureDocument")])
        assertIs<CborByteString>(
            (documentMap[CborString("issuerIdentifiers")] as kotlinx.serialization.cbor.CborArray).single(),
        )
        assertContentEquals(
            encodedDevice,
            coseCompliantCbor.encodeToByteArray(
                DeviceRequestInfo.serializer(),
                coseCompliantCbor.decodeFromByteArray<DeviceRequestInfo>(encodedDevice),
            ),
        )
    }

    @Test
    fun `extended request and ZKP structures preserve RFU fields and required embedded CBOR tags`() {
        val encryptionParameters = EncryptionParameters(
            recipientPublicKey = publicKey,
            nonce = byteArrayOf(1, 2, 3),
            recipientCertificate = listOf(byteArrayOf(4, 5, 6)),
            extensions = mapOf("futureEncryption" to CborString("encryption")),
        )
        val exactEncryptionParameters = coseCompliantCbor.encodeToByteArray(
            EncryptionParameters.serializer(),
            encryptionParameters,
        )
        val docInfo = DocRequestInfo(
            alternativeDataElements = listOf(
                AlternativeDataElementsSet(
                    requestedElement = ElementReference("org.example", "name"),
                    alternativeElementSets = listOf(
                        listOf(ElementReference("org.example", "full_name"))
                    ),
                    extensions = mapOf("futureAlternative" to CborString("alternative")),
                )
            ),
            zkRequest = ZkRequest(
                zkRequired = true,
                systemSpecs = listOf(
                    ZkSystemSpec(
                        zkSystemId = "proof-1",
                        system = "example-system",
                        extensions = mapOf("futureSystem" to CborString("system")),
                    )
                ),
                extensions = mapOf("futureZkRequest" to CborString("request")),
            ),
            docResponseEncryption = ByteStringWrapper(encryptionParameters, exactEncryptionParameters),
        )
        val docInfoBytes = coseCompliantCbor.encodeToByteArray(DocRequestInfo.serializer(), docInfo)
        val docInfoMap = coseCompliantCbor.decodeFromByteArray<CborMap>(docInfoBytes)
        val encryptionBytes = assertIs<CborByteString>(docInfoMap[CborString("docResponseEncryption")])
        assertTrue(24uL in encryptionBytes.tags)
        assertContentEquals(exactEncryptionParameters, encryptionBytes.toByteArray())

        val decodedDocInfo = coseCompliantCbor.decodeFromByteArray<DocRequestInfo>(docInfoBytes)
        val decodedZkRequest = requireNotNull(decodedDocInfo.zkRequest)
        assertEquals(
            CborString("alternative"),
            decodedDocInfo.alternativeDataElements!!.single().extensions["futureAlternative"],
        )
        assertEquals(CborString("request"), decodedZkRequest.extensions["futureZkRequest"])
        assertEquals(
            CborString("system"),
            decodedZkRequest.systemSpecs.single().extensions["futureSystem"],
        )
        assertEquals(
            CborString("encryption"),
            decodedDocInfo.docResponseEncryption!!.value.extensions["futureEncryption"],
        )
        assertContentEquals(
            docInfoBytes,
            coseCompliantCbor.encodeToByteArray(DocRequestInfo.serializer(), decodedDocInfo),
        )

        val signedItem = ZkSignedItem(
            elementIdentifier = "age_over_18",
            elementValue = CborInteger(1),
            extensions = mapOf("futureSignedItem" to CborString("item")),
        )
        val documentData = ZkDocumentData(
            docType = "org.example.document",
            zkSystemId = "proof-1",
            timestamp = LocalDate(2026, 8, 27),
            issuerSigned = mapOf("org.example" to listOf(signedItem)),
            msoX5chain = listOf(byteArrayOf(7, 8, 9)),
            extensions = mapOf("futureDocumentData" to CborString("data")),
        )
        val exactDocumentData = coseCompliantCbor.encodeToByteArray(ZkDocumentData.serializer(), documentData)
        val zkDocument = ZkDocument(
            documentData = ByteStringWrapper(documentData, exactDocumentData),
            proof = byteArrayOf(10, 11),
            extensions = mapOf("futureZkDocument" to CborString("document")),
        )
        val zkDocumentBytes = coseCompliantCbor.encodeToByteArray(ZkDocument.serializer(), zkDocument)
        val zkDocumentMap = coseCompliantCbor.decodeFromByteArray<CborMap>(zkDocumentBytes)
        val documentDataBytes = assertIs<CborByteString>(zkDocumentMap[CborString("documentData")])
        assertTrue(24uL in documentDataBytes.tags)
        assertContentEquals(exactDocumentData, documentDataBytes.toByteArray())

        val decodedZkDocument = coseCompliantCbor.decodeFromByteArray<ZkDocument>(zkDocumentBytes)
        assertEquals(CborString("document"), decodedZkDocument.extensions["futureZkDocument"])
        assertEquals(CborString("data"), decodedZkDocument.documentData.value.extensions["futureDocumentData"])
        assertEquals(
            CborString("item"),
            decodedZkDocument.documentData.value.issuerSigned!!["org.example"]!!.single()
                .extensions["futureSignedItem"],
        )
        assertContentEquals(
            zkDocumentBytes,
            coseCompliantCbor.encodeToByteArray(ZkDocument.serializer(), decodedZkDocument),
        )
    }

    @Test
    fun `use case and response substructures preserve flat RFU fields`() {
        val useCase = UseCase(
            mandatory = true,
            purposeHints = mapOf("org.example.purpose" to 1),
            documentSets = listOf(listOf(1u, 2u)),
            extensions = mapOf("futureUseCase" to CborString("use-case")),
        )
        val requestInfoBytes = coseCompliantCbor.encodeToByteArray(
            DeviceRequestInfo.serializer(),
            DeviceRequestInfo(useCases = listOf(useCase)),
        )
        val decodedRequestInfo = coseCompliantCbor.decodeFromByteArray<DeviceRequestInfo>(requestInfoBytes)
        assertEquals(CborString("use-case"), decodedRequestInfo.useCases!!.single().extensions["futureUseCase"])
        assertContentEquals(
            requestInfoBytes,
            coseCompliantCbor.encodeToByteArray(DeviceRequestInfo.serializer(), decodedRequestInfo),
        )

        val issuerItem = IssuerSignedItem(
            digestId = 1u,
            random = ByteArray(16) { 1 },
            elementIdentifier = "name",
            elementValue = CborString("Jane"),
            extensions = mapOf("futureIssuerItem" to CborString("issuer-item")),
        )
        val dummySignature = CoseSign1(byteArrayOf(), CoseHeaders(), null, byteArrayOf(1))
        val issuerSigned = IssuerSigned.fromIssuerSignedItems(
            namespacedItems = mapOf("org.example" to listOf(issuerItem)),
            issuerAuth = dummySignature,
            extensions = mapOf("futureIssuerSigned" to CborString("issuer")),
        )
        val deviceNameSpaces = DeviceNameSpaces(emptyMap())
        val exactDeviceNameSpaces = coseCompliantCbor.encodeToByteArray(
            DeviceNameSpaces.serializer(),
            deviceNameSpaces,
        )
        val document = Document(
            docType = "org.example.document",
            issuerSigned = issuerSigned,
            deviceSigned = DeviceSigned(
                namespaces = ByteStringWrapper(deviceNameSpaces, exactDeviceNameSpaces),
                deviceAuth = DeviceAuth.Signature(
                    signature = dummySignature,
                    extensions = mapOf("futureDeviceAuth" to CborString("auth")),
                ),
                extensions = mapOf("futureDeviceSigned" to CborString("device")),
            ),
            extensions = mapOf("futureDocument" to CborString("document")),
        )
        val response = DeviceResponse(version = "1.0", documents = listOf(document), status = 0u)
        val responseBytes = coseCompliantCbor.encodeToByteArray(DeviceResponse.serializer(), response)
        val decodedResponse = coseCompliantCbor.decodeFromByteArray<DeviceResponse>(responseBytes)
        val decodedDocument = decodedResponse.documents!!.single()
        val decodedDeviceSigned = requireNotNull(decodedDocument.deviceSigned)
        assertEquals(CborString("document"), decodedDocument.extensions["futureDocument"])
        assertEquals(CborString("issuer"), decodedDocument.issuerSigned.extensions["futureIssuerSigned"])
        assertEquals(
            CborString("issuer-item"),
            decodedDocument.issuerSigned.namespaces!!["org.example"]!!.entries.single().value
                .extensions["futureIssuerItem"],
        )
        assertEquals(CborString("device"), decodedDeviceSigned.extensions["futureDeviceSigned"])
        assertEquals(CborString("auth"), decodedDeviceSigned.deviceAuth.extensions["futureDeviceAuth"])
        assertContentEquals(
            responseBytes,
            coseCompliantCbor.encodeToByteArray(DeviceResponse.serializer(), decodedResponse),
        )
    }

    @Test
    fun `response containers reject documents without device authentication`() {
        val issuerSigned = IssuerSigned.fromIssuerSignedItems(
            namespacedItems = emptyMap(),
            issuerAuth = CoseSign1(byteArrayOf(), CoseHeaders(), null, byteArrayOf(1)),
        )
        val incompleteDocument = Document("org.example.document", issuerSigned)

        assertFailsWith<IllegalArgumentException> {
            DeviceResponse(version = "1.0", documents = listOf(incompleteDocument), status = 0u)
        }
        assertFailsWith<IllegalArgumentException> {
            EncryptedDocumentsPlaintext(documents = listOf(incompleteDocument))
        }
    }

    @Test
    fun `request response and session RFU fields survive a wire round trip`() {
        val items = ItemsRequest(
            docType = "org.example.document",
            namespaces = mapOf("org.example" to ItemsRequestList(listOf(ItemRequest("name", false)))),
            extensions = mapOf("futureItems" to CborString("items")),
        )
        val request = DeviceRequest(
            version = DeviceRequest.VERSION,
            docRequests = listOf(
                DocRequest(
                    itemsRequest = ByteStringWrapper(items),
                    extensions = mapOf("futureDocument" to CborString("document")),
                )
            ),
            extensions = mapOf("futureRequest" to CborString("request")),
        )
        val requestBytes = coseCompliantCbor.encodeToByteArray(DeviceRequest.serializer(), request)
        val decodedRequest = coseCompliantCbor.decodeFromByteArray<DeviceRequest>(requestBytes)
        assertEquals(CborString("request"), decodedRequest.extensions["futureRequest"])
        assertEquals(CborString("document"), decodedRequest.docRequests.single().extensions["futureDocument"])
        assertEquals(
            CborString("items"),
            decodedRequest.docRequests.single().itemsRequest.value.extensions["futureItems"],
        )
        assertContentEquals(requestBytes, coseCompliantCbor.encodeToByteArray(DeviceRequest.serializer(), decodedRequest))

        val response = DeviceResponse(
            version = "1.0",
            status = 10u,
            extensions = mapOf("futureResponse" to CborString("response")),
        )
        val responseBytes = coseCompliantCbor.encodeToByteArray(DeviceResponse.serializer(), response)
        val decodedResponse = coseCompliantCbor.decodeFromByteArray<DeviceResponse>(responseBytes)
        assertEquals(CborString("response"), decodedResponse.extensions["futureResponse"])
        assertContentEquals(
            responseBytes,
            coseCompliantCbor.encodeToByteArray(DeviceResponse.serializer(), decodedResponse),
        )

        val session = SessionData(
            status = 99u,
            extensions = mapOf("futureSession" to CborString("session")),
        )
        val sessionBytes = coseCompliantCbor.encodeToByteArray(SessionData.serializer(), session)
        val decodedSession = coseCompliantCbor.decodeFromByteArray<SessionData>(sessionBytes)
        assertEquals(null, decodedSession.statusCode)
        assertEquals(CborString("session"), decodedSession.extensions["futureSession"])
        assertContentEquals(sessionBytes, coseCompliantCbor.encodeToByteArray(SessionData.serializer(), decodedSession))
    }


    @Test
    fun `later minor versions of the known major are accepted while unknown majors fail`() {
        val encodedKey = coseCompliantCbor.encodeToByteArray(CoseKey.serializer(), publicKey)
        DeviceEngagement(
            version = "1.2",
            security = DeviceEngagementSecurity(1u, ByteStringWrapper(publicKey, encodedKey)),
        )
        assertFailsWith<IllegalArgumentException> {
            DeviceEngagement(
                version = "2.0",
                security = DeviceEngagementSecurity(1u, ByteStringWrapper(publicKey, encodedKey)),
            )
        }
    }

    @Test
    fun `session status uses a deterministic exact encoding`() {
        val encoded = coseCompliantCbor.encodeToByteArray(SessionData.serializer(), SessionData(status = 20u))
        assertContentEquals("a16673746174757314".hexToByteArray(), encoded)

        val dataEncoded = coseCompliantCbor.encodeToByteArray(
            SessionData.serializer(),
            SessionData(data = ByteArray(16)),
        )
        val dataMap = coseCompliantCbor.decodeFromByteArray<CborMap>(dataEncoded)
        assertIs<CborByteString>(dataMap[CborString("data")])
    }

    @Test
    fun `conventional and provisional NFC methods have distinct wire contracts`() {
        val conventional = DeviceRetrievalMethod.Nfc(255u, 65_536u)
        val nfcV2 = DeviceRetrievalMethod.NfcV2(maximumResponseDataLength = 4_096u)
        val encodedKey = coseCompliantCbor.encodeToByteArray(CoseKey.serializer(), publicKey)
        val engagement = DeviceEngagement(
            version = DeviceEngagement.VERSION_1_0,
            security = DeviceEngagementSecurity(1u, ByteStringWrapper(publicKey, encodedKey)),
            deviceRetrievalMethods = listOf(conventional, nfcV2),
        )

        val encoded = coseCompliantCbor.encodeToByteArray(DeviceEngagement.serializer(), engagement)
        val decoded = coseCompliantCbor.decodeFromByteArray<DeviceEngagement>(encoded)

        assertEquals(conventional, decoded.deviceRetrievalMethods!![0])
        assertEquals(nfcV2, decoded.deviceRetrievalMethods!![1])
        assertContentEquals(
            "830501a100191000".hexToByteArray(),
            DeviceRetrievalMethodCodec.encode(nfcV2),
        )
        assertContentEquals(encoded, coseCompliantCbor.encodeToByteArray(DeviceEngagement.serializer(), decoded))
    }

    @Test
    fun `session sequence numbers are explicit fields and conventional messages remain unchanged`() {
        val conventional = coseCompliantCbor.encodeToByteArray(SessionData.serializer(), SessionData(status = 20u))
        assertContentEquals("a16673746174757314".hexToByteArray(), conventional)

        val sequenced = SessionData(status = 20u, seq = 0u)
        val sequencedBytes = coseCompliantCbor.encodeToByteArray(SessionData.serializer(), sequenced)
        val sequencedMap = coseCompliantCbor.decodeFromByteArray<CborMap>(sequencedBytes)
        assertEquals(CborInteger(0), sequencedMap[CborString("seq")])
        assertEquals(sequenced, coseCompliantCbor.decodeFromByteArray<SessionData>(sequencedBytes))

        val establishment = SessionEstablishment(
            eReaderKey = ByteStringWrapper(
                publicKey,
                coseCompliantCbor.encodeToByteArray(CoseKey.serializer(), publicKey),
            ),
            data = ByteArray(16),
            seq = UInt.MAX_VALUE,
        )
        val establishmentBytes = coseCompliantCbor.encodeToByteArray(SessionEstablishment.serializer(), establishment)
        assertEquals(
            CborInteger(UInt.MAX_VALUE.toULong()),
            coseCompliantCbor.decodeFromByteArray<CborMap>(establishmentBytes)[CborString("seq")],
        )
        assertEquals(establishment, coseCompliantCbor.decodeFromByteArray(establishmentBytes))

        val malformedSequence = coseCompliantCbor.encodeToByteArray(
            CborElement.serializer(),
            CborMap(
                mapOf(
                    CborString("status") to CborInteger(20),
                    CborString("seq") to CborString("not-an-unsigned-integer"),
                )
            ),
        )
        assertFailsWith<kotlinx.serialization.SerializationException> {
            coseCompliantCbor.decodeFromByteArray<SessionData>(malformedSequence)
        }
    }

    private fun ByteArray.indexOfSubsequence(needle: ByteArray): Int {
        for (index in 0..size - needle.size) {
            if (needle.indices.all { offset -> this[index + offset] == needle[offset] }) return index
        }
        error("Expected byte sequence was not found")
    }
}
