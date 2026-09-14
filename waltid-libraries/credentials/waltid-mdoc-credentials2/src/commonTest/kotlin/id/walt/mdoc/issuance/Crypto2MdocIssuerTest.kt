@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.mdoc.issuance

import id.walt.certificate.x509.X509CertificateUtil
import id.walt.cose.Cose
import id.walt.cose.CoseCertificate
import id.walt.cose.toCoseKey
import id.walt.cose.verify
import id.walt.cose.coseCompliantCbor
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.EcdsaSignatureEncoding
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.*
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.mdoc.credsdata.DrivingPrivilege
import id.walt.mdoc.credsdata.Mdl
import id.walt.mdoc.encoding.mapPortraitCaptureDate
import id.walt.mdoc.encoding.PortraitCaptureDateMapping
import id.walt.mdoc.crypto.MdocCrypto.getSharedSecret
import id.walt.mdoc.objects.document.Document
import id.walt.mdoc.objects.document.IssuerSigned
import id.walt.mdoc.objects.mso.MobileSecurityObject
import id.walt.mdoc.verification.verifyIssuerAuthentication
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.serialization.cbor.CborElement
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonNull
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull
import kotlin.time.Instant

class Crypto2MdocIssuerTest {
    private val runtime = CryptoRuntime(defaultSoftwareKeyProviders())

    @Test
    fun `mdoc issuance and issuer verification use crypto2`() = runTest {
        val issuerKey = runtime.generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId("issuer"),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            )
        )
        val holderKey = runtime.generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId("holder"),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.KEY_AGREEMENT),
            )
        )
        val holderJwk = holderKey.capabilities.publicKeyExporter!!.exportPublicKey() as EncodedKey.Jwk
        val holderCoseKey = holderJwk.toCoseKey()
        val sigAlg = SignatureAlgorithm.Ecdsa(
            DigestAlgorithm.SHA_256,
            EcdsaSignatureEncoding.DER,
        )
        val certificate = X509CertificateUtil.createSelfSignedCertificate(issuerKey, sigAlg) {
            subjectDn = "CN=Crypto2 mdoc issuer"
        }
        val issued = MdocIssuer.issueUniversal(
            issuerKey = issuerKey,
            signatureAlgorithm = Cose.Algorithm.ES256,
            issuerCertificate = listOf(CoseCertificate(certificate.encodedDer.toByteArray())),
            holderKey = holderCoseKey,
            docType = "org.example.mdoc",
            data = MdocIssuer.MdocUniversalIssuanceData(
                namespaces = mapOf(
                    "org.example" to JsonObject(mapOf("given_name" to JsonPrimitive("Jane")))
                )
            ),
        )

        assertTrue(issued.issuerAuth.verify(issuerKey, Cose.Algorithm.ES256))
        assertEquals("org.example.mdoc", issued.issuerAuth.decodeIsoPayload<MobileSecurityObject>().docType)
        val parsedIssuerAuth = issued.getParsedIssuerAuthCrypto2()
        assertTrue(issued.issuerAuth.verify(parsedIssuerAuth.signerKey, Cose.Algorithm.ES256))
        val verification = verifyIssuerAuthentication(
            document = Document(docType = "org.example.mdoc", issuerSigned = issued),
            validateCertificateConstraints = false,
        )
        assertEquals(Cose.Algorithm.ES256, verification.coseAlgorithm)

        val issueDate = LocalDate(2026, 1, 1)
        val typesafeIssued = MdocIssuer.issueTypesafe(
            issuerKey = issuerKey,
            signatureAlgorithm = Cose.Algorithm.ES256,
            issuerCertificate = listOf(CoseCertificate(certificate.encodedDer.toByteArray())),
            holderKey = holderCoseKey,
            typesafeData = Mdl(
                familyName = "Doe",
                givenName = "Jane",
                issueDate = issueDate,
                expiryDate = LocalDate(2036, 1, 1),
                documentNumber = "DOC-1",
                drivingPrivileges = listOf(DrivingPrivilege("B", issueDate)),
                portraitCaptureDate = LocalDate(2024, 2, 29),
            ),
        )
        assertTrue(typesafeIssued.issuerAuth.verify(issuerKey, Cose.Algorithm.ES256))
        assertEquals(
            "org.iso.18013.5.1.mDL",
            typesafeIssued.issuerAuth.decodeIsoPayload<MobileSecurityObject>().docType,
        )
        val decoded = coseCompliantCbor.decodeFromByteArray<IssuerSigned>(coseCompliantCbor.encodeToByteArray(typesafeIssued))
        val portraitTimestamp = assertNotNull(decoded.namespaces)["org.iso.18013.5.1"]!!.entries
            .single { it.value.elementIdentifier == "portrait_capture_date" }.value
        assertContentEquals(
            byteArrayOf(0xc0.toByte(), 0x74) + "2024-02-29T00:00:00Z".encodeToByteArray(),
            coseCompliantCbor.encodeToByteArray(CborElement.serializer(), portraitTimestamp.elementValue),
        )
        assertTrue(decoded.issuerAuth.verify(issuerKey, Cose.Algorithm.ES256))

        // Direct namespace issuance requires the explicit callback documented in the README.
        for ((input, expected) in listOf(
            JsonPrimitive("2024-02-29") to "2024-02-29T00:00:00Z",
            JsonPrimitive("2024-02-29T13:34:56.987+01:00") to "2024-02-29T12:34:56Z",
            JsonNull to null,
        )) {
            val namespaceIssued = MdocIssuer.issueUniversal(
                issuerKey = issuerKey,
                signatureAlgorithm = Cose.Algorithm.ES256,
                issuerCertificate = listOf(CoseCertificate(certificate.encodedDer.toByteArray())),
                holderKey = holderCoseKey,
                docType = "org.iso.18013.5.1.mDL",
                data = MdocIssuer.MdocUniversalIssuanceData(mapOf(
                    "org.iso.18013.5.1" to JsonObject(mapOf(
                        "portrait_capture_date" to input,
                        "birth_date" to JsonPrimitive("2000-01-01"),
                    )),
                )),
                valueMappingFunction = { docType, namespace, elementIdentifier, value ->
                    when (val portrait = mapPortraitCaptureDate(namespace, elementIdentifier, value)) {
                        PortraitCaptureDateMapping.NotApplicable ->
                            MdocIssuer.defaultSchemalessMappingFunction(docType, namespace, elementIdentifier, value)
                        PortraitCaptureDateMapping.Omit -> null
                        is PortraitCaptureDateMapping.Mapped -> portrait.value
                    }
                },
            )
            val roundTripped = coseCompliantCbor.decodeFromByteArray<IssuerSigned>(coseCompliantCbor.encodeToByteArray(namespaceIssued))
            assertTrue(roundTripped.issuerAuth.verify(issuerKey, Cose.Algorithm.ES256))
            val items = roundTripped.namespaces!!.getValue("org.iso.18013.5.1").entries
                .associate { it.value.elementIdentifier to it.value.elementValue }
            if (expected == null) {
                assertTrue("portrait_capture_date" !in items)
            } else {
                assertContentEquals(
                    byteArrayOf(0xc0.toByte(), 0x74) + expected.encodeToByteArray(),
                    coseCompliantCbor.encodeToByteArray(CborElement.serializer(), items.getValue("portrait_capture_date")),
                )
            }
            assertContentEquals(
                byteArrayOf(0xd9.toByte(), 0x03, 0xec.toByte(), 0x6a) + "2000-01-01".encodeToByteArray(),
                coseCompliantCbor.encodeToByteArray(CborElement.serializer(), items.getValue("birth_date")),
            )
        }
    }

    @Test
    fun `crypto2 ECDH derives the same mdoc shared secret`() = runTest {
        val first = agreementKey("first")
        val second = agreementKey("second")
        val firstPublic = first.capabilities.publicKeyExporter!!.exportPublicKey()
        val secondPublic = second.capabilities.publicKeyExporter!!.exportPublicKey()

        assertContentEquals(
            first.getSharedSecret(secondPublic),
            second.getSharedSecret(firstPublic),
        )
    }

    private suspend fun agreementKey(id: String) = runtime.generateSoftwareKey(
        GenerateSoftwareKeyRequest(
            id = KeyId(id),
            spec = KeySpec.Ec(EcCurve.P256),
            usages = setOf(KeyUsage.KEY_AGREEMENT),
        )
    )
}
