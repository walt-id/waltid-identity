package id.walt.sdjwt

import id.walt.sdjwt.metadata.type.ClaimSdMetadata
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ClaimSdMetadataSerializationTest {

    @Serializable
    private data class Wrapper(val claim: ClaimSdMetadata)

    @Test
    fun `encode each enum to its SerialName`() {
        assertEquals(
            expected = "\"always\"",
            actual = Json.encodeToString(ClaimSdMetadata.Always),
        )
        assertEquals(
            expected = "\"allowed\"",
            actual = Json.encodeToString(ClaimSdMetadata.Allowed),
        )
        assertEquals(
            expected = "\"never\"",
            actual = Json.encodeToString(ClaimSdMetadata.Never),
        )

    }

    @Test
    fun `decode each SerialName to enum`() {
        assertEquals(
            expected = ClaimSdMetadata.Always,
            actual = Json.decodeFromString<ClaimSdMetadata>("\"always\""),
        )
        assertEquals(
            expected = ClaimSdMetadata.Allowed,
            actual = Json.decodeFromString<ClaimSdMetadata>("\"allowed\""),
        )
        assertEquals(
            expected = ClaimSdMetadata.Never,
            actual = Json.decodeFromString<ClaimSdMetadata>("\"never\""),
        )
    }

    @Test
    fun `roundtrip as field in an object`() {
        val w = Wrapper(ClaimSdMetadata.Allowed)
        val s = Json.encodeToString(w)
        assertEquals("""{"claim":"allowed"}""", s)
        assertEquals(w, Json.decodeFromString<Wrapper>(s))
    }

    @Test
    fun `roundtrip in a list`() {
        val list = listOf(ClaimSdMetadata.Always, ClaimSdMetadata.Allowed, ClaimSdMetadata.Never)
        val s = Json.encodeToString(list)
        assertEquals("""["always","allowed","never"]""", s)
        assertEquals(list, Json.decodeFromString(s))
    }

    @Test
    fun `enum as map key serializes to its SerialName`() {
        val map = mapOf(
            ClaimSdMetadata.Always  to 1,
            ClaimSdMetadata.Allowed to 2,
            ClaimSdMetadata.Never   to 3
        )
        val s = Json.encodeToString(map)
        // Order may vary; verify by decoding back
        val decoded: Map<ClaimSdMetadata, Int> = Json.decodeFromString(s)
        assertEquals(map, decoded)
        // And spot-check that keys are the serial names
        assertTrue(s.contains("\"always\""))
        assertTrue(s.contains("\"allowed\""))
        assertTrue(s.contains("\"never\""))
    }

    @Test
    fun `decoding unknown value fails`() {
        assertFailsWith<SerializationException> {
            Json.decodeFromString<ClaimSdMetadata>("\"sometimes\"")
        }
    }

    @Test
    fun `decoding wrong-cased value fails (case-sensitive)`() {
        assertFailsWith<SerializationException> {
            Json.decodeFromString<ClaimSdMetadata>("\"Always\"") // capital A but not the serial name
        }
    }
}