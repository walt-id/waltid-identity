package id.walt.crypto2.migration.v1

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.ProviderId
import id.walt.crypto2.keys.StoredKey
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.CryptographySoftwareKeyProvider
import id.walt.crypto2.serialization.BinaryData
import id.walt.crypto2.serialization.StoredKeyCodec
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class V1KeyMigrationTest {
    @Test
    fun `private local JWK migrates offline and survives restart`() = runTest {
        val provider = CryptographySoftwareKeyProvider()
        val generated = provider.generate(
            GenerateSoftwareKeyRequest(
                id = KeyId("source"),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            )
        )
        val jwk = assertIs<EncodedKey.Jwk>(generated.storedKey.material)
        val source = """{"type":"jwk","jwk":${jwk.data.toByteArray().decodeToString()},"_keyId":"legacy-kid"}"""
        val migrated = assertIs<StoredKey.Software>(
            V1KeyMigration().migrate(
                KeyId("database-id"),
                source,
                setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            )
        )
        assertEquals(KeyId("database-id"), migrated.id)
        assertEquals(KeySpec.Ec(EcCurve.P256), migrated.spec)
        assertTrue(assertIs<EncodedKey.Jwk>(migrated.material).privateMaterial)

        val runtime = CryptoRuntime(listOf(provider))
        val restored = runtime.restore(
            StoredKeyCodec.decodeFromByteArray(StoredKeyCodec.encodeToByteArray(migrated))
        )
        val algorithm = SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256)
        val signature = assertNotNull(restored.capabilities.signer).sign(byteArrayOf(1), algorithm)
        assertTrue(assertNotNull(restored.capabilities.verifier).verify(byteArrayOf(1), signature, algorithm))
    }

    @Test
    fun `public RSA derives modulus size and rejects private usage`() = runTest {
        val modulus = ByteArray(384).apply { this[0] = 0x80.toByte() }
        val encoded = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
        val source = """{"type":"jwk","jwk":{"kty":"RSA","n":"${encoded.encode(modulus)}","e":"AQAB"}}"""

        val migrated = V1KeyMigration().migrate(KeyId("rsa"), source, setOf(KeyUsage.VERIFY))
        assertEquals(KeySpec.Rsa(3072), migrated.spec)
        assertFailsWith<IllegalArgumentException> {
            V1KeyMigration().migrate(KeyId("rsa"), source, setOf(KeyUsage.SIGN))
        }
    }

    @Test
    fun `managed migration delegates without persisting embedded credentials`() = runTest {
        val x = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(ByteArray(32) { 1 })
        val y = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(ByteArray(32) { 2 })
        val safeMigrator = V1ManagedKeyMigrator { record ->
            assertEquals(KeySpec.Ec(EcCurve.P256), record.spec)
            StoredKey.Managed(
                version = StoredKey.CURRENT_VERSION,
                id = record.id,
                spec = requireNotNull(record.spec),
                usages = record.usages,
                provider = ProviderId("aws-kms-rest"),
                providerSchemaVersion = 1,
                providerData = BinaryData("""{"credentialReference":"aws-prod"}""".encodeToByteArray()),
                publicKey = null,
            )
        }
        val source = Json.parseToJsonElement(
            """
            {
              "type":"aws-rest-api",
              "config":{"auth":{"accessKeyId":"embedded-access","secretAccessKey":"embedded-secret","region":"eu"}},
              "id":"remote",
              "_keyType":"secp256r1",
              "_publicKey":"{\"kty\":\"EC\",\"crv\":\"P-256\",\"x\":\"$x\",\"y\":\"$y\"}"
            }
            """.trimIndent()
        ).let { it as JsonObject }
        val migrated = assertIs<StoredKey.Managed>(
            V1KeyMigration(mapOf("aws-rest-api" to safeMigrator))
                .migrate(KeyId("aws"), source, setOf(KeyUsage.SIGN, KeyUsage.VERIFY))
        )
        assertFalse(migrated.providerData.toByteArray().decodeToString().contains("embedded"))

        val leaking = V1ManagedKeyMigrator { record ->
            safeMigrator.migrate(record).copy(
                providerData = BinaryData("embedded-secret".encodeToByteArray())
            )
        }
        assertFailsWith<IllegalArgumentException> {
            V1KeyMigration(mapOf("aws-rest-api" to leaking))
                .migrate(KeyId("aws"), source, setOf(KeyUsage.SIGN))
        }
    }

    @Test
    fun `mobile software and platform records use separate one-way paths`() = runTest {
        val base64Url = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
        val x = base64Url.encode(ByteArray(32) { 1 })
        val d = base64Url.encode(ByteArray(32) { 2 })
        val softwareJwk = """{"kty":"OKP","crv":"Ed25519","x":"$x","d":"$d"}"""
        val software = V1KeyMigration().migrateMobileReference(
            V1MobileKeyReference(
                id = KeyId("mobile-software"),
                keyType = "Ed25519",
                platform = V1MobilePlatform.ANDROID,
                platformBacked = false,
                keyMaterial = softwareJwk,
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            )
        )
        assertIs<StoredKey.Software>(software)

        val platformMigration = V1PlatformKeyMigrator { record ->
            StoredKey.Managed(
                version = StoredKey.CURRENT_VERSION,
                id = record.id,
                spec = KeySpec.Ec(EcCurve.P256),
                usages = record.usages,
                provider = ProviderId("android-keystore-signum"),
                providerSchemaVersion = 1,
                providerData = BinaryData("{}".encodeToByteArray()),
            )
        }
        val platform = V1KeyMigration(platformMigrator = platformMigration).migrateMobileReference(
            V1MobileKeyReference(
                id = KeyId("alias"),
                keyType = "secp256r1",
                platform = V1MobilePlatform.ANDROID,
                platformBacked = true,
                keyMaterial = null,
                usages = setOf(KeyUsage.SIGN),
            )
        )
        assertIs<StoredKey.Managed>(platform)

        assertFailsWith<V1KeyMigrationException.MissingPlatformMigrator> {
            V1KeyMigration().migrateMobileReference(
                V1MobileKeyReference(
                    id = KeyId("alias"),
                    keyType = "secp256r1",
                    platform = V1MobilePlatform.ANDROID,
                    platformBacked = true,
                    keyMaterial = null,
                    usages = setOf(KeyUsage.SIGN),
                )
            )
        }
    }

    @Test
    fun `unknown malformed and policy-free records fail explicitly`() = runTest {
        assertFailsWith<V1KeyMigrationException.MissingManagedMigrator> {
            V1KeyMigration().migrate(
                KeyId("unknown"),
                """{"type":"unknown-provider"}""",
                setOf(KeyUsage.SIGN),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            V1KeyMigration().migrate(
                KeyId("missing-type"),
                "{}",
                setOf(KeyUsage.SIGN),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            V1KeyMigration().migrate(
                KeyId("no-policy"),
                """{"type":"jwk","jwk":{"kty":"EC","crv":"P-256","x":"AQ","y":"Ag"}}""",
                emptySet(),
            )
        }
    }
}
