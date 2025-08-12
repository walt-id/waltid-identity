@file:OptIn(ExperimentalTime::class)

package id.walt.vical

import id.walt.cose.Cose
import id.walt.cose.CoseCertificate
import id.walt.cose.toCoseSigner
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.time.ExperimentalTime

class MultiplatformVicalUsageExamples {

    @Test
    fun `Multiplatform VICAL usage`() = runTest {
        println("Creating VICAL Provider Key...")
        val vicalProviderKey: Key = JWKKey.generate(KeyType.secp256r1)
        println("Step 1: Generated VICAL provider key with ID: ${vicalProviderKey.getKeyId()}")

        // 2. SETUP: Create a DER-encoded certificate for the provider.
        // In a real scenario, a proper X.509 certificate containing the public key would be used.
        // For this simple test, we'll use the key's public representation as a stand-in for the certificate bytes for now.
        println("Provider certificate setup...")
        val vicalProviderCertificate = CoseCertificate(vicalProviderKey.getPublicKeyRepresentation())

        // 3. CREATE VICAL DATA: Define the content of the VICAL payload.
        println("IACA Certificate info...")
        val iacaCertificateInfo = CertificateInfo(
            certificate = Random.nextBytes(256), // A dummy IACA cert
            serialNumber = byteArrayOf(0x01, 0x02, 0x03, 0x04),
            ski = Random.nextBytes(20),
            docType = listOf("org.iso.18013.5.1.mDL"),
            certificateProfile = listOf("org.iso.18013.5.1.mDL.IACA"),
            issuingAuthority = "Utopia DMV",
            issuingCountry = "UT"
        )
        println("Created IACA certificate info: ${iacaCertificateInfo.toString().replace("\n", " ")}")

        val vicalData = VicalData(
            vicalProvider = "walt.id VICAL Service",
            date = kotlin.time.Clock.System.now(),
            vicalIssueID = 1L,
            nextUpdate = kotlin.time.Instant.parse("2026-08-01T00:00:00Z"),
            certificateInfos = listOf(iacaCertificateInfo)
        )
        println("Step 2: VICAL data created for provider: ${vicalData.vicalProvider}")
        println("Vical data: ${vicalData.toString().replace("\n", " ")}")

        // 4. SIGN: Create a CoseSigner from the key and sign the VICAL data.
        println("Signing VICAL...")
        val signer = vicalProviderKey.toCoseSigner()
        val signedVical = Vical.createAndSign(
            vicalData = vicalData,
            signer = signer,
            algorithmId = Cose.Algorithm.ES256, // ES256 for secp256r1 = -7
            signerCertificateChain = listOf(vicalProviderCertificate)
        )
        println("Step 3: VICAL has been signed: ${signedVical.toString().replace("\n", " ")}")

        // 5. ENCODE: Get the final CBOR byte array to be distributed to clients.
        println("Encoding VICAL...")
        val vicalBytes = signedVical.toTaggedCbor()
        println("Step 4: Signed VICAL encoded to ${vicalBytes.size} CBOR bytes.")
        println("Signed VICAL hex: ${vicalBytes.toHexString()}")

        // --- At this point, the `vicalBytes` would be sent to a client ---

        // 6. DECODE: A client receives the bytes and decodes them into a Vical object.
        println("Decoding VICAL...")
        val receivedVical = Vical.decode(vicalBytes)
        println("Step 5: Client decoded the VICAL.")
        println(" - Provider: ${receivedVical.vicalData.vicalProvider}")
        println(" - Certificates found: ${receivedVical.vicalData.certificateInfos.size}")
    }
}
