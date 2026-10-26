package id.walt.policies.policies.status.entry

import id.walt.policies.policies.status.model.IETFEntry
import id.walt.policies.policies.status.status.IetfStatusListTokens
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MDocEntryExtractorTest {
    private val extractor = MDocEntryExtractor()
    private val ietfExtractor = IETFEntryExtractor(extractor)
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `extracts IETF status from an mdoc MSO status_list`() {
        val uri = "https://status.example/list"
        val mdoc = IetfStatusListTokens.mdocWithStatus(index = 42u, uri = uri)
        val extracted = extractor.extract(buildJsonObject { put("mdoc", mdoc.toCBORHex()) })
        val entry = json.decodeFromJsonElement<IETFEntry>(extracted!!)
        assertEquals(42uL, entry.index)
        assertEquals(uri, entry.uri)
    }

    @Test
    fun `returns null when the credential has no mdoc field`() {
        assertNull(extractor.extract(buildJsonObject { put("vct", "urn:example:credential") }))
    }

    @Test
    fun `IETF extractor prefers JSON status claim over mdoc`() {
        val mdoc = IetfStatusListTokens.mdocWithStatus(index = 1u, uri = "https://mdoc.example/list")
        val data = buildJsonObject {
            put("mdoc", mdoc.toCBORHex())
            putJsonObject("status") {
                putJsonObject("status_list") {
                    put("idx", 7)
                    put("uri", "https://json.example/list")
                }
            }
        }
        val extracted = ietfExtractor.extract(data)
        val entry = json.decodeFromJsonElement<IETFEntry>(extracted!!)
        assertEquals(7uL, entry.index)
        assertEquals("https://json.example/list", entry.uri)
    }

    @Test
    fun `IETF extractor falls back to mdoc when JSON status is absent`() {
        val uri = "https://mdoc.example/list"
        val mdoc = IetfStatusListTokens.mdocWithStatus(index = 9u, uri = uri)
        val extracted = ietfExtractor.extract(buildJsonObject { put("mdoc", mdoc.toCBORHex()) })
        val entry = json.decodeFromJsonElement<IETFEntry>(extracted!!)
        assertEquals(9uL, entry.index)
        assertEquals(uri, entry.uri)
    }
}
