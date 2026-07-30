package id.walt.crypto2.migration.v1

import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.ProviderId
import id.walt.crypto2.keys.StoredKey
import id.walt.crypto2.serialization.BinaryData
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs

class V1KeyMigrationTest {
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
