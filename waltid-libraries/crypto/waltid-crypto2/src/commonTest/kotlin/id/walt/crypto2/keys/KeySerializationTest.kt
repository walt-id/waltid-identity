package id.walt.crypto2.keys

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.providers.GenerateManagedKeyRequest
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.ManagedKeyProvider
import id.walt.crypto2.providers.cryptography.CryptographySoftwareKeyProvider
import id.walt.crypto2.serialization.BinaryData
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KeySerializationTest {
    private val algorithm = SignatureAlgorithm.RsaPkcs1(DigestAlgorithm.SHA_256)

    @Test
    fun `inferred software key encodes as its StoredKey`() = runTest {
        val generatedSoftwareKey = softwareRuntime().generateSoftwareKey(softwareRequest())

        val encoded = Json.encodeToString(generatedSoftwareKey)

        assertEquals(Json.encodeToString<StoredKey>(generatedSoftwareKey.storedKey), encoded)
        assertContains(encoded, "\"kind\":\"software\"")
        assertContains(encoded, "\"version\":${StoredKey.CURRENT_VERSION}")
    }

    @Test
    fun `decoded software key requires explicit runtime restoration before signing`() = runTest {
        val runtime = softwareRuntime()
        val generated = runtime.generateSoftwareKey(softwareRequest())
        val decoded = Json.decodeFromString<SoftwareKey>(Json.encodeToString(generated))

        assertEquals(generated.storedKey, decoded.storedKey)
        assertNull(decoded.capabilities.signer)
        assertNull(decoded.capabilities.verifier)
        assertFalse(decoded is Signer)
        assertContains(decoded.toString(), "CryptoRuntime.restore")

        val restored = runtime.restore(decoded)
        val message = "explicit restoration".encodeToByteArray()
        val signature = assertNotNull(restored.capabilities.signer).sign(message, algorithm)
        assertTrue(assertNotNull(restored.capabilities.verifier).verify(message, signature, algorithm))
    }

    @Test
    fun `Key typed roundtrip preserves the key kind and descriptor`() = runTest {
        val generated: Key = softwareRuntime().generateSoftwareKey(softwareRequest())

        val decoded = Json.decodeFromString<Key>(Json.encodeToString(generated))

        assertIs<SoftwareKey>(decoded)
        assertEquals((generated as StorableKey).storedKey, decoded.storedKey)
        assertNull(decoded.capabilities.signer)
    }

    @Test
    fun `managed key descriptor roundtrips and restores through its provider`() = runTest {
        val provider = RecordingManagedProvider()
        val runtime = CryptoRuntime(softwareProviders = emptyList(), managedProviders = listOf(provider))
        val generated = runtime.generateManagedKey(
            provider = provider.id,
            request = GenerateManagedKeyRequest(
                id = KeyId("managed-serialization"),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.SIGN),
                providerOptions = BinaryData("provider-reference".encodeToByteArray()),
            ),
        )

        val encoded = Json.encodeToString(generated)
        val decoded = Json.decodeFromString<ManagedKey>(encoded)

        assertEquals(Json.encodeToString<StoredKey>(generated.storedKey), encoded)
        assertEquals(generated.storedKey, decoded.storedKey)
        assertNull(decoded.capabilities.signer)
        val restored = runtime.restore(decoded)
        assertEquals(1, provider.restoreCalls)
        assertContentEquals(
            "managed".encodeToByteArray().reversedArray(),
            assertNotNull(restored.capabilities.signer).sign("managed".encodeToByteArray(), algorithm),
        )
    }

    @Test
    fun `key serializers reject malformed and tampered versions`() {
        val encoded = Json.encodeToString<SoftwareKey>(TestSoftwareKey(softwareStoredKey()))
        val missingVersion = encoded.replace("\"version\":1,", "")
        val tamperedVersion = encoded.replace("\"version\":1", "\"version\":2")

        assertFailsWith<SerializationException> { Json.decodeFromString<SoftwareKey>(missingVersion) }
        val failure = assertFailsWith<SerializationException> {
            Json.decodeFromString<SoftwareKey>(tamperedVersion)
        }
        assertContains(failure.message.orEmpty(), "Unsupported software key version: 2")
    }

    @Test
    fun `Key serializer clearly rejects non-storable custom keys`() {
        val key: Key = object : Key {
            override val id = KeyId("not-storable")
            override val spec = KeySpec.Custom("external")
            override val usages = setOf(KeyUsage.SIGN)
        }

        val failure = assertFailsWith<SerializationException> { Json.encodeToString(key) }

        assertContains(failure.message.orEmpty(), "anonymous Key implementation")
        assertContains(failure.message.orEmpty(), "does not implement StorableKey")
        assertContains(failure.message.orEmpty(), "CryptoRuntime.restore")
    }

    @Test
    fun `private and public software handles restore only their declared capabilities`() = runTest {
        val runtime = softwareRuntime()
        val privateKey = runtime.generateSoftwareKey(softwareRequest())
        val privateHandle = Json.decodeFromString<SoftwareKey>(Json.encodeToString(privateKey))
        assertTrue(assertIs<EncodedKey.Jwk>(privateHandle.storedKey.material).privateMaterial)
        val restoredPrivate = runtime.restore(privateHandle)
        val message = "private and public".encodeToByteArray()
        val signature = assertNotNull(restoredPrivate.capabilities.signer).sign(message, algorithm)

        val publicMaterial = assertNotNull(privateKey.capabilities.publicKeyExporter).exportPublicKey()
        val publicKey = assertIs<SoftwareKey>(
            runtime.restore(
                privateKey.storedKey.copy(
                    id = KeyId("public-serialization"),
                    usages = setOf(KeyUsage.VERIFY),
                    material = publicMaterial,
                ),
            ),
        )
        val publicHandle = Json.decodeFromString<SoftwareKey>(Json.encodeToString(publicKey))
        assertFalse(assertIs<EncodedKey.Jwk>(publicHandle.storedKey.material).privateMaterial)
        assertNull(publicHandle.capabilities.signer)
        assertNull(publicHandle.capabilities.verifier)

        val restoredPublic = runtime.restore(publicHandle)
        assertNull(restoredPublic.capabilities.signer)
        assertTrue(assertNotNull(restoredPublic.capabilities.verifier).verify(message, signature, algorithm))
    }

    private fun softwareRuntime() = CryptoRuntime(listOf(CryptographySoftwareKeyProvider()))

    private fun softwareRequest() = GenerateSoftwareKeyRequest(
        id = KeyId("software-serialization"),
        spec = KeySpec.Rsa(2048),
        usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
    )

    private fun softwareStoredKey() = StoredKey.Software(
        version = StoredKey.CURRENT_VERSION,
        id = KeyId("software-fixture"),
        spec = KeySpec.Ec(EcCurve.P256),
        usages = setOf(KeyUsage.SIGN),
        material = EncodedKey.Jwk(BinaryData("{}".encodeToByteArray()), privateMaterial = true),
    )

    private class TestSoftwareKey(override val storedKey: StoredKey.Software) : SoftwareKey

    private class RecordingManagedProvider : ManagedKeyProvider {
        override val id = ProviderId("managed-test")
        var restoreCalls = 0

        override suspend fun generate(request: GenerateManagedKeyRequest): ManagedKey = OperationalManagedKey(
            StoredKey.Managed(
                version = StoredKey.CURRENT_VERSION,
                id = request.id,
                spec = request.spec,
                usages = request.usages,
                provider = id,
                providerSchemaVersion = 1,
                providerData = request.providerOptions,
                metadata = request.metadata,
            ),
        )

        override suspend fun restore(stored: StoredKey.Managed): ManagedKey {
            restoreCalls++
            return OperationalManagedKey(stored)
        }
    }

    private class OperationalManagedKey(override val storedKey: StoredKey.Managed) : ManagedKey {
        override val capabilities = KeyCapabilities(
            signer = Signer { data, _ -> data.reversedArray() },
        )
    }
}
