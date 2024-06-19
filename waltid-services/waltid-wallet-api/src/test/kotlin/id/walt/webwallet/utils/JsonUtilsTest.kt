package id.walt.webwallet.utils

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class JsonUtilsTest {
    @Test
    fun `test root level`() {
        val json = Json.parseToJsonElement("""{"property":"value"}""".trimIndent()).jsonObject
        val result = JsonUtils.tryGetData(json, "property")
        assertEquals(expected = "value", actual = result!!.jsonPrimitive.content)
    }

    @Test
    fun `test nested level`() {
        val json = Json.parseToJsonElement("""{"root":{"nested":"value"}}""".trimIndent()).jsonObject
        val result = JsonUtils.tryGetData(json, "root.nested")
        assertEquals(expected = "value", actual = result!!.jsonPrimitive.content)
    }

    @Test
    fun `test nested array level any index`() {
        val json =
            Json.parseToJsonElement("""{"root":{"nested":[{"no-item": "no-value"},{"item": "value"}]}}""".trimIndent()).jsonObject
        val result = JsonUtils.tryGetData(json, "root.nested.item")
        assertEquals(expected = "value", actual = result!!.jsonPrimitive.content)
    }

    @Test
    fun `test nested level missing property`() {
        val json = Json.parseToJsonElement("""{"root":{"nested":"value"}}""".trimIndent()).jsonObject
        val result = JsonUtils.tryGetData(json, "root.item")
        assertEquals(expected = null, actual = result)
    }

    @Test
    fun `test nested array level missing property`() {
        val json = Json.parseToJsonElement("""{"root":{"nested":[{"no-item": "no-value"}]}}""".trimIndent()).jsonObject
        val result = JsonUtils.tryGetData(json, "root.nested.item")
        assertEquals(expected = null, actual = result)
    }
}