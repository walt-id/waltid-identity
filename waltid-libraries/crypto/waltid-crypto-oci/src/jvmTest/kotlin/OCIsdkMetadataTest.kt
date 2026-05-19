import id.walt.crypto.keys.KeyManager
import id.walt.crypto.keys.oci.OCIsdkMetadata
import id.walt.crypto.keys.oci.WaltCryptoOci
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@DisplayName("OCIsdkMetadata")
class OCIsdkMetadataTest {

    private val vaultId = "ocid1.vault.oc1.eu-frankfurt-1.entbf645aabf2.example"
    private val compartmentId = "ocid1.compartment.oc1..examplecompartment"

    @Nested
    @DisplayName("Default values")
    inner class DefaultValues {

        @Test
        fun `authType defaults to INSTANCE_PRINCIPAL`() {
            val meta = OCIsdkMetadata(vaultId, compartmentId)
            assertEquals("INSTANCE_PRINCIPAL", meta.authType)
        }

        @Test
        fun `configFilePath defaults to null`() {
            val meta = OCIsdkMetadata(vaultId, compartmentId)
            assertNull(meta.configFilePath)
        }

        @Test
        fun `configProfile defaults to null`() {
            val meta = OCIsdkMetadata(vaultId, compartmentId)
            assertNull(meta.configProfile)
        }

        @Test
        fun `vaultId and compartmentId are stored correctly`() {
            val meta = OCIsdkMetadata(vaultId, compartmentId)
            assertEquals(vaultId, meta.vaultId)
            assertEquals(compartmentId, meta.compartmentId)
        }
    }

    @Nested
    @DisplayName("Auth type configuration")
    inner class AuthTypeConfiguration {

        @Test
        fun `CONFIG_FILE auth type stores all fields`() {
            val meta = OCIsdkMetadata(
                vaultId = vaultId,
                compartmentId = compartmentId,
                authType = "CONFIG_FILE",
                configFilePath = "/home/user/.oci/config",
                configProfile = "staging",
            )
            assertEquals("CONFIG_FILE", meta.authType)
            assertEquals("/home/user/.oci/config", meta.configFilePath)
            assertEquals("staging", meta.configProfile)
        }

        @Test
        fun `RESOURCE_PRINCIPAL auth type is accepted`() {
            val meta = OCIsdkMetadata(vaultId, compartmentId, authType = "RESOURCE_PRINCIPAL")
            assertEquals("RESOURCE_PRINCIPAL", meta.authType)
        }

        @Test
        fun `CONFIG_FILE with null path and profile uses SDK defaults`() {
            val meta = OCIsdkMetadata(vaultId, compartmentId, authType = "CONFIG_FILE")
            assertEquals("CONFIG_FILE", meta.authType)
            assertNull(meta.configFilePath)
            assertNull(meta.configProfile)
        }
    }

    @Nested
    @DisplayName("JSON serialization")
    inner class JsonSerialization {

        private val lenientJson = Json { ignoreUnknownKeys = true }

        @Test
        fun `default metadata round-trips through JSON`() {
            val original = OCIsdkMetadata(vaultId, compartmentId)
            val json = Json.encodeToString(original)
            val restored = Json.decodeFromString<OCIsdkMetadata>(json)
            assertEquals(original, restored)
        }

        @Test
        fun `CONFIG_FILE metadata round-trips through JSON`() {
            val original = OCIsdkMetadata(
                vaultId, compartmentId,
                authType = "CONFIG_FILE",
                configFilePath = "/etc/oci/config",
                configProfile = "prod",
            )
            val json = Json.encodeToString(original)
            val restored = Json.decodeFromString<OCIsdkMetadata>(json)
            assertEquals(original, restored)
        }

        @Test
        fun `explicit INSTANCE_PRINCIPAL JSON round-trips correctly`() {
            val original = OCIsdkMetadata(vaultId, compartmentId, "INSTANCE_PRINCIPAL", null, null)
            val json = Json.encodeToString(original)
            val restored = lenientJson.decodeFromString<OCIsdkMetadata>(json)
            assertEquals(original, restored)
        }

        @Test
        fun `serialized JSON contains vaultId and compartmentId`() {
            val json = Json.encodeToString(OCIsdkMetadata(vaultId, compartmentId))
            assertTrue(json.contains(vaultId), "JSON should contain vaultId")
            assertTrue(json.contains(compartmentId), "JSON should contain compartmentId")
        }

        @Test
        fun `null optional fields are omitted or null in JSON`() {
            val meta = OCIsdkMetadata(vaultId, compartmentId)
            val json = Json.encodeToString(meta)
            val restored = Json.decodeFromString<OCIsdkMetadata>(json)
            assertNull(restored.configFilePath)
            assertNull(restored.configProfile)
        }
    }

    @Nested
    @DisplayName("Data class contract")
    inner class DataClassContract {

        @Test
        fun `two instances with same values are equal`() {
            val a = OCIsdkMetadata(vaultId, compartmentId)
            val b = OCIsdkMetadata(vaultId, compartmentId)
            assertEquals(a, b)
        }

        @Test
        fun `instances with different vaultId are not equal`() {
            val a = OCIsdkMetadata(vaultId, compartmentId)
            val b = OCIsdkMetadata("other-vault-id", compartmentId)
            assertFalse(a == b)
        }

        @Test
        fun `instances with different authType are not equal`() {
            val a = OCIsdkMetadata(vaultId, compartmentId, authType = "INSTANCE_PRINCIPAL")
            val b = OCIsdkMetadata(vaultId, compartmentId, authType = "CONFIG_FILE")
            assertFalse(a == b)
        }

        @Test
        fun `copy preserves unmodified fields and updates specified ones`() {
            val original = OCIsdkMetadata(vaultId, compartmentId)
            val updated = original.copy(authType = "CONFIG_FILE", configFilePath = "/tmp/config")
            assertEquals(vaultId, updated.vaultId)
            assertEquals(compartmentId, updated.compartmentId)
            assertEquals("CONFIG_FILE", updated.authType)
            assertEquals("/tmp/config", updated.configFilePath)
        }

        @Test
        fun `hashCode is consistent with equals`() {
            val a = OCIsdkMetadata(vaultId, compartmentId)
            val b = OCIsdkMetadata(vaultId, compartmentId)
            assertEquals(a.hashCode(), b.hashCode())
        }

        @Test
        fun `toString contains vaultId`() {
            val meta = OCIsdkMetadata(vaultId, compartmentId)
            assertTrue(meta.toString().contains(vaultId))
        }
    }

    @Nested
    @DisplayName("WaltCryptoOci.init()")
    inner class WaltCryptoOciInit {

        @Test
        fun `init registers the oci key backend in KeyManager`() {
            WaltCryptoOci.init()
            assertTrue(KeyManager.keyTypeGeneration.containsKey("oci"))
        }

        @Test
        fun `calling init multiple times is idempotent`() {
            WaltCryptoOci.init()
            WaltCryptoOci.init()
            assertTrue(KeyManager.keyTypeGeneration.containsKey("oci"))
        }
    }
}
