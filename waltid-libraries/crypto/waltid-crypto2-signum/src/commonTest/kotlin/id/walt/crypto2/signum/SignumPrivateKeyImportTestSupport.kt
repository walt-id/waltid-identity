package id.walt.crypto2.signum

import id.walt.crypto2.keys.HardwarePreference
import id.walt.crypto2.keys.KeyProtectionLevel
import id.walt.crypto2.keys.KeyOrigin
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.*
import id.walt.crypto2.providers.GenerateManagedKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.crypto2.serialization.BinaryData
import kotlin.test.*
import kotlin.uuid.Uuid

/** Fixed private fixture derived independently with Python HMAC and OpenSSL P-256. Never production material. */
internal val nativeImportTestMaterial = EncodedKey.Jwk(BinaryData(("""{"kty":"EC","crv":"P-256",\
"x":"Wy87Jza-MAxBSOvUNi73uIaWmnTDrZ5wSTf_PqIaNYc",\
"y":"emY2LnzjX8MeSJxPM1mP9c924_V6drBDVB9BCxDYKb8",\
"d":"uqDwic1tOTnxFp6amne7WMZU8-6SqRMQbn95fClcK3g"}""").replace("\\\n", "").encodeToByteArray()), true)

internal suspend fun exerciseNativePrivateImport(backend: SignumPlatformBackend, policy: SignumKeyPolicy) {
    val provider = SignumManagedKeyProvider(backend)
    val id = KeyId("wal749-import-test-${Uuid.random()}")
    val material = nativeImportTestMaterial
    val spec = KeySpec.Ec(EcCurve.P256)
    val usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY)
    val request = GenerateManagedKeyRequest(id, spec, usages, providerOptions = SignumKeyOptions(policy = policy).encode())
    val original = CryptoRuntime(defaultSoftwareKeyProviders()).restore(
        StoredKey.Software(StoredKey.CURRENT_VERSION, id, spec, usages, material))
    var stored: StoredKey.Managed? = null
    try {
        repeat(2) {
            val imported = provider.importPrivateKey(request, material)
            stored = imported.storedKey
            assertEquals(KeyOrigin.IMPORTED, imported.origin)
            if (imported.protectionLevel == KeyProtectionLevel.HARDWARE) assertNull(imported.capabilities.privateKeyExporter)
            assertEquals(material.toSpkiDer(spec), imported.storedKey.publicKey)
            if (policy.hardware == HardwarePreference.REQUIRED) assertEquals(KeyProtectionLevel.HARDWARE, imported.protectionLevel)
            val reopened = SignumManagedKeyProvider(backend).restoreSignumKey(imported.storedKey)
            assertEquals(KeyOrigin.IMPORTED, reopened.origin)
            val data = "WAL-749 original-key proof".encodeToByteArray()
            val algorithm = SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256)
            val signature = assertNotNull(reopened.capabilities.signer).sign(data, algorithm)
            assertTrue(assertNotNull(original.capabilities.verifier).verify(data, signature, algorithm))
            assertFalse(assertNotNull(original.capabilities.verifier).verify("wrong message".encodeToByteArray(), signature, algorithm))
            provider.delete(imported.storedKey)
            stored = null
        }
    } finally { stored?.let { provider.delete(it) } }
}
