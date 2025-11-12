package id.walt.sdjwt

import id.walt.sdjwt.metadata.type.ClaimInformation
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ClaimSdMetadataSerializationTest {

    @Serializable
    private data class Wrapper(val claim: ClaimInformation.ClaimSdMetadata)

    @Test
    fun `encode each enum to its SerialName`() {
        assertEquals(
            expected = "\"always\"",
            actual = Json.encodeToString(ClaimInformation.ClaimSdMetadata.Always),
        )
        assertEquals(
            expected = "\"allowed\"",
            actual = Json.encodeToString(ClaimInformation.ClaimSdMetadata.Allowed),
        )
        assertEquals(
            expected = "\"never\"",
            actual = Json.encodeToString(ClaimInformation.ClaimSdMetadata.Never),
        )

    }

    @Test
    fun `decode each SerialName to enum`() {
        assertEquals(
            expected = ClaimInformation.ClaimSdMetadata.Always,
            actual = Json.decodeFromString<ClaimInformation.ClaimSdMetadata>("\"always\""),
        )
        assertEquals(
            expected = ClaimInformation.ClaimSdMetadata.Allowed,
            actual = Json.decodeFromString<ClaimInformation.ClaimSdMetadata>("\"allowed\""),
        )
        assertEquals(
            expected = ClaimInformation.ClaimSdMetadata.Never,
            actual = Json.decodeFromString<ClaimInformation.ClaimSdMetadata>("\"never\""),
        )
    }

    @Test
    fun `roundtrip as field in an object`() {
        val w = Wrapper(ClaimInformation.ClaimSdMetadata.Allowed)
        val s = Json.encodeToString(w)
        assertEquals("""{"claim":"allowed"}""", s)
        assertEquals(w, Json.decodeFromString<Wrapper>(s))
    }

    @Test
    fun `roundtrip in a list`() {
        val list = listOf(ClaimInformation.ClaimSdMetadata.Always, ClaimInformation.ClaimSdMetadata.Allowed, ClaimInformation.ClaimSdMetadata.Never)
        val s = Json.encodeToString(list)
        assertEquals("""["always","allowed","never"]""", s)
        assertEquals(list, Json.decodeFromString(s))
    }

    @Test
    fun `enum as map key serializes to its SerialName`() {
        val map = mapOf(
            ClaimInformation.ClaimSdMetadata.Always  to 1,
            ClaimInformation.ClaimSdMetadata.Allowed to 2,
            ClaimInformation.ClaimSdMetadata.Never   to 3
        )
        val s = Json.encodeToString(map)
        // Order may vary; verify by decoding back
        val decoded: Map<ClaimInformation.ClaimSdMetadata, Int> = Json.decodeFromString(s)
        assertEquals(map, decoded)
        // And spot-check that keys are the serial names
        assertTrue(s.contains("\"always\""))
        assertTrue(s.contains("\"allowed\""))
        assertTrue(s.contains("\"never\""))
    }

    @Test
    fun `decoding unknown value fails`() {
        assertFailsWith<SerializationException> {
            Json.decodeFromString<ClaimInformation.ClaimSdMetadata>("\"sometimes\"")
        }
    }

    @Test
    fun `decoding wrong-cased value fails (case-sensitive)`() {
        assertFailsWith<SerializationException> {
            Json.decodeFromString<ClaimInformation.ClaimSdMetadata>("\"Always\"") // capital A but not the serial name
        }
    }
}