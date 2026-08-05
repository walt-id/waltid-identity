package id.walt.crypto2.examples

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.KeyEncodingFormat
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.decodePrivateKeyPem
import id.walt.crypto2.keys.decodePublicKeyPem
import id.walt.crypto2.keys.encodePem
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.CryptographySoftwareKeyProvider

data class PemImportExportResult(
    val publicPemChars: Int,
    val privatePemChars: Int,
    val verified: Boolean,
    val wrongLabelRejected: Boolean,
)

object PemImportExportExample {
    suspend fun run(output: ExampleOutput): PemImportExportResult {
        output("=== Strict PEM SPKI/PKCS8 export + import ===")
        val provider = CryptographySoftwareKeyProvider()
        val runtime = CryptoRuntime(softwareProviders = listOf(provider))
        output("1. Create provider=${provider.id.value} and generate P-256 key as PKCS8 DER")
        val generated = runtime.generateSoftwareKey(
            request = GenerateSoftwareKeyRequest(
                id = KeyId("pem-key"),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
                keyEncoding = KeyEncodingFormat.PKCS8_DER,
            ),
        )

        // SPKI is the public-key container; PKCS8 is the private-key container and must remain confidential.
        val publicDer = requireNotNull(generated.capabilities.publicKeyExporter)
            .exportPublicKey(format = KeyEncodingFormat.SPKI_DER) as EncodedKey.SpkiDer
        val privateDer = requireNotNull(generated.capabilities.privateKeyExporter)
            .exportPrivateKey(format = KeyEncodingFormat.PKCS8_DER) as EncodedKey.Pkcs8Der
        val publicPem = publicDer.encodePem()
        val privatePem = privateDer.encodePem()
        output("2. Export PUBLIC KEY/SPKI chars=${publicPem.length} and PRIVATE KEY/PKCS8 chars=${privatePem.length}")
        output("   PEM contents are intentionally not printed")

        // Strict decoders reject swapped labels, non-canonical base64, and extra PEM documents.
        val importedPrivate = runtime.restore(
            stored = generated.storedKey.copy(material = privatePem.decodePrivateKeyPem()),
        )
        val importedPublic = runtime.restore(
            stored = generated.storedKey.copy(
                usages = setOf(KeyUsage.VERIFY),
                material = publicPem.decodePublicKeyPem(),
            ),
        )
        output("3. Import PKCS8 for SIGN|VERIFY and SPKI for VERIFY only")
        val algorithm = SignatureAlgorithm.Ecdsa(digest = DigestAlgorithm.SHA_256)
        val message = "strict PEM import".encodeToByteArray()
        val signature = requireNotNull(importedPrivate.capabilities.signer).sign(data = message, algorithm = algorithm)
        val verified = requireNotNull(importedPublic.capabilities.verifier).verify(
            data = message,
            signature = signature,
            algorithm = algorithm,
        )
        val wrongLabelRejected = try {
            publicPem.decodePrivateKeyPem()
            false
        } catch (_: IllegalArgumentException) {
            true
        }
        output("4. Verification result: verified=$verified, wrongLabelRejected=$wrongLabelRejected")

        check(verified && wrongLabelRejected)
        return PemImportExportResult(publicPem.length, privatePem.length, verified, wrongLabelRejected)
    }
}
