@file:OptIn(ExperimentalSerializationApi::class)

import id.walt.cose.*
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.mdoc.credsdata.PhotoId
import id.walt.mdoc.encoding.ByteStringWrapper
import id.walt.mdoc.objects.DeviceSigned
import id.walt.mdoc.objects.digest.ValueDigest
import id.walt.mdoc.objects.digest.ValueDigestList
import id.walt.mdoc.objects.document.DeviceAuth
import id.walt.mdoc.objects.document.Document
import id.walt.mdoc.objects.document.IssuerSigned
import id.walt.mdoc.objects.elements.DeviceNameSpaces
import id.walt.mdoc.objects.elements.IssuerSignedItem
import id.walt.mdoc.objects.mso.DeviceKeyInfo
import id.walt.mdoc.objects.mso.MobileSecurityObject
import id.walt.mdoc.objects.mso.ValidityInfo
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlin.random.Random
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.ExperimentalTime

class PhotoIdCborTest {

    @Test
    fun `Photo ID serialization round-trip test`() {
        // 1. Create a sample PhotoId object with realistic data
        val photoId = PhotoId(
            familyNameUnicode = "Müller",
            givenNameUnicode = "Anna",
            familyNameLatin1 = "Muller",
            givenNameLatin1 = "Anna",
            birthDate = LocalDate(1990, 8, 15),
            portrait = Random.nextBytes(32), // Using random bytes for the portrait image
            issueDate = LocalDate(2025, 9, 8),
            expiryDate = LocalDate(2035, 9, 7),
            issuingAuthorityUnicode = "Stadt Wien",
            issuingCountry = "AT",
            ageOver18 = true, // Consistent with the birth date
            administrativeNumber = "12345-ABC-XYZ", // Optional field
            personId = "AT-555-1234" // Optional field
        )

        // 2. Serialize the PhotoId object to a CBOR byte array
        val cborBytes = coseCompliantCbor.encodeToByteArray(photoId)
        val serializedHex = cborBytes.toHexString()

        println("Serialized PhotoId (Hex): $serializedHex")

        // 3. Deserialize the CBOR byte array back into a PhotoId object
        val deserializedPhotoId = coseCompliantCbor.decodeFromByteArray<PhotoId>(cborBytes)

        // 4. Assert that the deserialized object is identical to the original
        // This is the most important check for a round-trip test.
        assertEquals(photoId, deserializedPhotoId)

        // 5. (Optional but recommended) Assert that key fields are present in the hex string
        assertContains(serializedHex, "family_name_unicode".encodeToByteArray().toHexString())
        assertContains(serializedHex, "Müller".encodeToByteArray().toHexString())
        assertContains(serializedHex, "issuing_country".encodeToByteArray().toHexString())
        assertContains(serializedHex, "AT".encodeToByteArray().toHexString())
        assertContains(serializedHex, "age_over_18".encodeToByteArray().toHexString())
        assertContains(serializedHex, "f5") // CBOR encoding for `true`
    }

    @OptIn(ExperimentalTime::class, ExperimentalSerializationApi::class)
    @Test
    fun `Photo ID signing and verification structure test`() = runTest {
        val issuerKey = JWKKey.generate(KeyType.secp256r1)
        val coseSigner = issuerKey.toCoseSigner()
        val coseVerifier = issuerKey.getPublicKey().toCoseVerifier()

        // --- 1. SETUP: Issuer & Device Keys (placeholders) and PhotoID data ---
        val deviceKey = CoseKey(
            kty = Cose.KeyTypes.EC2,
            crv = Cose.EllipticCurves.P_256,
            x = Random.nextBytes(32),
            y = Random.nextBytes(32)
        )

        val photoId = PhotoId(
            familyNameUnicode = "Müller",
            givenNameUnicode = "Anna",
            familyNameLatin1 = "Muller",
            givenNameLatin1 = "Anna",
            birthDate = LocalDate(1990, 8, 15),
            portrait = Random.nextBytes(32),
            issueDate = LocalDate(2025, 9, 8),
            expiryDate = LocalDate(2035, 9, 7),
            issuingAuthorityUnicode = "Stadt Wien",
            issuingCountry = "AT",
            ageOver18 = true
        )
        println("Photo ID: $photoId")

        val photoIdDocType = "org.iso.23220.photoid.1"
        // As per the spec, use the 23220-2 namespace for common elements
        val commonNamespace = "org.iso.23220.2"

        // --- 2. SIGNING PROCESS: Create Issuer-Signed data structures ---

        // Map PhotoId data to a list of IssuerSignedItem objects
        val issuerSignedItems = listOf(
            IssuerSignedItem(0u, Random.nextBytes(16), "family_name_unicode", photoId.familyNameUnicode),
            IssuerSignedItem(1u, Random.nextBytes(16), "given_name_unicode", photoId.givenNameUnicode),
            IssuerSignedItem(2u, Random.nextBytes(16), "birth_date", photoId.birthDate),
            IssuerSignedItem(3u, Random.nextBytes(16), "issuing_country", photoId.issuingCountry)
        )
        issuerSignedItems.forEachIndexed { idx, issuerSignedItem ->
            println("Issuer Signed Item $idx: $issuerSignedItem")
        }

        // Calculate digests for each item
        val valueDigests = issuerSignedItems.map {
            ValueDigest.fromIssuerSignedItem(it, commonNamespace, "SHA-256")
        }
        valueDigests.forEachIndexed { idx, digests ->
            println("Value Digest $idx: $digests")
        }

        // Create the Mobile Security Object (MSO)
        val mso = MobileSecurityObject(
            version = "1.0",
            digestAlgorithm = "SHA-256",
            docType = photoIdDocType,
            valueDigests = mapOf(commonNamespace to ValueDigestList(valueDigests)),
            deviceKeyInfo = DeviceKeyInfo(deviceKey = deviceKey),
            validityInfo = ValidityInfo(
                signed = Clock.System.now(),
                validFrom = Clock.System.now(),
                validUntil = Clock.System.now().plus(365.days)
            )
        )
        println("Mobile Security Object: $mso")

        // The MSO is wrapped in a tagged bytestring to become the payload
        val msoBytes = coseCompliantCbor.encodeToByteArray(mso)
        val msoPayload = byteArrayOf(0xd8.toByte(), 24.toByte()) + coseCompliantCbor.encodeToByteArray(ByteArraySerializer(), msoBytes)


        // Create the CoseSign1 object (issuerAuth) with a signature
        val issuerAuth = CoseSign1.createAndSign(
            protectedHeaders = CoseHeaders(algorithm = Cose.Algorithm.ES256),
            unprotectedHeaders = CoseHeaders(), // Placeholder for cert chain
            payload = msoPayload,
            signer = coseSigner
        )
        println("Issuer Auth: $issuerAuth")

        // Construct the final Document
        val document = Document(
            docType = photoIdDocType,
            issuerSigned = IssuerSigned.fromIssuerSignedItems(
                namespacedItems = mapOf(commonNamespace to issuerSignedItems),
                issuerAuth = issuerAuth
            ),
            // Empty device signed part for this test
            deviceSigned = DeviceSigned(ByteStringWrapper(DeviceNameSpaces(mapOf())), DeviceAuth(deviceMac = CoseMac0(ByteArray(0), CoseHeaders(), ByteArray(0), ByteArray(0))))
        )
        println("Document: $document")

        // --- 3. VERIFICATION PROCESS: Simulate what a verifier would do ---

        // A verifier receives the `document`. First, they parse the MSO.
        val receivedIssuerAuth = document.issuerSigned.issuerAuth

        // A. Verify the COSE signature
        val isSignatureValid = receivedIssuerAuth.verify(coseVerifier)
        assertTrue(isSignatureValid, "COSE signature verification failed!")
        println("COSE signature on MSO is valid.")

        val decodedMso = receivedIssuerAuth.decodeIsoPayload<MobileSecurityObject>()

        // Assert that the MSO content is as expected
        assertEquals("1.0", decodedMso.version)
        assertEquals(photoIdDocType, decodedMso.docType)
        assertEquals(deviceKey, decodedMso.deviceKeyInfo.deviceKey)

        // The core of the verification: check if the digests in the MSO match the received data.
        val receivedDigests = decodedMso.valueDigests[commonNamespace]!!.entries
        val receivedIssuerItems = document.issuerSigned.namespaces!![commonNamespace]!!.entries

        assertEquals(issuerSignedItems.size, receivedDigests.size)

        // For each received item, calculate its digest and verify it matches the one in the MSO
        for (signedItemWrapper in receivedIssuerItems) {
            val signedItem = signedItemWrapper.value
            val expectedDigest = ValueDigest.fromIssuerSignedItem(signedItem, commonNamespace, "SHA-256")
            val receivedDigest = receivedDigests.find { it.key == signedItem.digestId }

            assertNotNull(receivedDigest, "Digest with ID ${signedItem.digestId} not found in MSO")
            assertEquals(
                expectedDigest.value.toHexString(),
                receivedDigest.value.toHexString(),
                "Digest for '${signedItem.elementIdentifier}' does not match!"
            )
        }

        println("Successfully verified ${receivedDigests.size} item digests against the MSO.")
    }
}
