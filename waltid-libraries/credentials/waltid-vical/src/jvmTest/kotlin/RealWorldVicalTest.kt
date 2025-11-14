import id.walt.cose.toCoseVerifier
import id.walt.crypto.keys.JvmJWKKeyCreator.convertDerCertificateToPemCertificate
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.vical.Vical
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.ExperimentalSerializationApi
import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue


@OptIn(ExperimentalSerializationApi::class)
class RealWorldVicalTest {

    fun getResource(name: String) = this::class.java.classLoader.getResource(name) ?: error("Resource for test not found: $name")

    @Test
    fun `Load live American Association of Motor Vehicles Administrators VICAL`() = runTest {
        println("Loading \"American Association of Motor Vehicles Administrators\" VICAL...")
        val aamvaVicalRawFile = getResource("vicals/aamva.cbor").readBytes()

        println("Decoding VICAL...")

        val vical = Vical.decode(aamvaVicalRawFile)
        println(vical.vicalData)

        println("\n")
        println("--- Allowed credential issuers: ---")
        val allowedIssuers = vical.vicalData.getAllAllowedIssuers().entries
        vical.vicalData.getAllAllowedIssuers().entries.forEachIndexed { idx, (certificateInfo, certKeyResult) ->
            println("--- ${idx + 1}: Certificate key for: ${certificateInfo.issuingAuthority}")
            val certKey = certKeyResult.getOrNull()
            println("Key: $certKeyResult (${certKey?.getKeyId() ?: "Error"})")
        }
        assertTrue { allowedIssuers.all { (certInfo, certKey) -> certKey.isSuccess } }
        println("All keys successful. Allowed issuers per this VICAL: ${allowedIssuers.size}")
    }

    @Test
    fun `Load live Austroads VICAL`() = runTest {
        println("Loading \"Austroads\" VICAL...")

        /* # Download current VICAL from Austroads like this:
         curl -X GET 'https://beta.nationaldts.com.au/api/vical' \
              -H 'Accept: application/cbor' \
              --output austroads-vical.cbor
         */
        val austroadsVicalRawFile = getResource("vicals/austroads.cbor").readBytes()

        println("Decoding VICAL...")
        val vical = Vical.decode(austroadsVicalRawFile)
        println(vical.vicalData)

        println()
        println("Reading signer certificate...")
        val signerCertificate = vical.getCertificateChain()!!.first().rawBytes
        val signerPem = convertDerCertificateToPemCertificate(signerCertificate)
        println("Signer PEM:")
        println(signerPem)

        println("\nReading signer key from signer certificate...")
        val signerKey = JWKKey.importFromDerCertificate(signerCertificate).getOrThrow()
        println("Signer key to be used: $signerKey")

        println("\nVerifying VICAL against signer certificate...")
        val vicalVerified = vical.verify(signerKey.toCoseVerifier())

        println("VICAL verified against signer certificate: $vicalVerified")
        assertTrue(vicalVerified)


        val austroadsRootCertificate = getResource("root-certificates/austroads_root-certificate.pem").readText()
        println("-- Verifying against root certificate --")
        println("Root certificate:")
        println(austroadsRootCertificate)

        val rootKey = JWKKey.importPEM(austroadsRootCertificate).getOrThrow()
        println("Root certificate key: $rootKey")

        // Certificate validation:
        val certFactory = CertificateFactory.getInstance("X.509")
        val signerCert = certFactory.generateCertificate(ByteArrayInputStream(signerCertificate)) as X509Certificate
        println("Signer Certificate Subject: ${signerCert.getSubjectX500Principal()}")

        // Parse the trusted root certificate from the PEM file
        val rootCert = certFactory.generateCertificate(austroadsRootCertificate.byteInputStream()) as X509Certificate
        assertEquals(rootCert.publicKey, rootKey.getInternalPublicKey())
        println("Root Certificate Subject: ${rootCert.getSubjectX500Principal()}")

        // --- 4. Verify the Certificate Chain ---
        println("Verifying signer certificate against the root certificate's public key...")

        signerCert.verify(rootKey.getInternalPublicKey())
        println("SUCCESS: The signer certificate is trusted by the root CA.")

        println("\n")
        println("--- Allowed credential issuers: ---")
        val allowedIssuers = vical.vicalData.getAllAllowedIssuers().entries
        allowedIssuers.forEachIndexed { idx, (certificateInfo, certKeyResult) ->
            println("--- ${idx + 1}: Certificate key for: ${certificateInfo.issuingAuthority}")
            val certKey = certKeyResult.getOrNull()
            println("Key: $certKeyResult (${certKey?.getKeyId() ?: "could not parse key"})")
        }
        println("Allowed issuers per this VICAL: ${allowedIssuers.size}")
    }
}
