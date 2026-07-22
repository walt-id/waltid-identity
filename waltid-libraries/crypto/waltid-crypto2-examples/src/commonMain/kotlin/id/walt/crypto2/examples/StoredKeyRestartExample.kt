package id.walt.crypto2.examples

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.SoftwareKey
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.CryptographySoftwareKeyProvider
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class StoredKeyRestartResult(
    val persistedBytes: Int,
    val restoredKeyId: String,
    val verified: Boolean,
)

object StoredKeyRestartExample {
    suspend fun run(output: ExampleOutput): StoredKeyRestartResult {
        output("=== kotlinx.serialization key persistence + restart ===")
        val provider = CryptographySoftwareKeyProvider()
        val initialRuntime = CryptoRuntime(softwareProviders = listOf(provider))
        output("1. Create provider=${provider.id.value} and generate P-256 SIGN|VERIFY key")
        val original = initialRuntime.generateSoftwareKey(
            request = GenerateSoftwareKeyRequest(
                id = KeyId("persistent-key"),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            ),
        )

        // This example intentionally reveals the private StoredKey JSON to demonstrate its exact persisted shape.
        val persisted = Json.encodeToString(original)
        output(
            "2. Json.encodeToString(key): version=${original.storedKey.version}, " +
                "persistedBytes=${persisted.encodeToByteArray().size}",
        )
        output("   WARNING: The next line contains private key material. Never emit it to application logs.")
        output("   Private StoredKey JSON: $persisted")

        // Decoding is provider-neutral and synchronous: this handle intentionally has no signing capability.
        val storableKey = Json.decodeFromString<SoftwareKey>(persisted)
        output("3. Decode a non-operational SoftwareKey handle: canSign=${storableKey.capabilities.signer != null}")

        // A new runtime and explicit provider materialize the handle after an in-process service restart.
        val restartedRuntime = CryptoRuntime(softwareProviders = listOf(CryptographySoftwareKeyProvider()))
        val restored = restartedRuntime.restore(storableKey)
        output("4. CryptoRuntime.restore(handle): keyId=${restored.id.value}, spec=P-256")
        val algorithm = SignatureAlgorithm.Ecdsa(digest = DigestAlgorithm.SHA_256)
        val message = "restored after restart".encodeToByteArray()
        val signature = requireNotNull(restored.capabilities.signer).sign(data = message, algorithm = algorithm)
        val verified = requireNotNull(restored.capabilities.verifier).verify(
            data = message,
            signature = signature,
            algorithm = algorithm,
        )
        output("5. Verification result after restart: verified=$verified, signatureBytes=${signature.size}")

        check(verified)
        return StoredKeyRestartResult(persisted.encodeToByteArray().size, restored.id.value, verified)
    }
}
