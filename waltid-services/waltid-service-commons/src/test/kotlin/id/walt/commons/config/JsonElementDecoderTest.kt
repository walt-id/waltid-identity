package id.walt.commons.config

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JsonElementDecoderTest {

    data class JsonConfig(val value: JsonElement)

    @TempDir
    lateinit var temporaryDirectory: Path

    @AfterEach
    fun clearConfig() {
        System.clearProperty("config.file.$CONFIG_ID")
        ConfigManager.preclear()
    }

    @Test
    fun numberLookingProviderIdentifiersRemainStrings() {
        for (token in listOf("10000000000000000001", "010000000000000000001", "-10000000000000000001")) {
            val value = loadValue("{ client_id = $token }").jsonObject.getValue("client_id").jsonPrimitive
            assertTrue(value.isString)
            assertEquals(token, value.content)
        }
    }

    @Test
    fun quotedIntegerBeyondInt64StaysString() {
        val segment = loadValue("""["map", "10000000000000000000"]""").jsonArray[1].jsonPrimitive

        assertTrue(segment.isString)
        assertEquals("10000000000000000000", segment.content)
    }

    @Test
    fun valuesWithinInt64AndOtherScalarsAreUnchanged() {
        val value = loadValue(
            """{ small = 0, max = 9223372036854775807, min = -9223372036854775808, nothing = null, flag = true, text = abc, decimal = 1.5 }"""
        ).jsonObject

        assertEquals(
            JsonObject(
                mapOf(
                    "small" to JsonPrimitive(0L),
                    "max" to JsonPrimitive(Long.MAX_VALUE),
                    "min" to JsonPrimitive(Long.MIN_VALUE),
                    "nothing" to JsonNull,
                    "flag" to JsonPrimitive(true),
                    "text" to JsonPrimitive("abc"),
                    "decimal" to JsonPrimitive(1.5),
                )
            ),
            value,
        )
    }

    @Test
    fun substitutionDoesNotReinterpretNumberLookingStrings() {
        val value = loadValue("\${base}", prefix = "base = 10000000000000000000\n")

        assertEquals(JsonPrimitive("10000000000000000000"), value)
    }

    @Test
    fun unquotedDigitsMixedWithTextStayString() {
        val value = loadValue("[10000000000000000000abc]").jsonArray[0].jsonPrimitive

        assertTrue(value.isString)
        assertEquals("10000000000000000000abc", value.content)
    }

    @Test
    fun commandLineOverrideOfIntegerBeyondInt64StaysString() {
        val value = loadValue("1", args = arrayOf("--value=10000000000000000000")).jsonPrimitive

        assertTrue(value.isString)
        assertEquals("10000000000000000000", value.content)
    }

    private fun loadValue(hocon: String, prefix: String = "", args: Array<String> = emptyArray()): JsonElement {
        val file = Files.createTempFile(temporaryDirectory, "$CONFIG_ID-", ".conf")
        Files.writeString(file, "${prefix}value = $hocon\n")
        System.setProperty("config.file.$CONFIG_ID", file.toString())

        val config = ConfigManager.loadConfig(ConfigManager.ConfigData(CONFIG_ID, JsonConfig::class), args).getOrThrow()
        return (config as JsonConfig).value
    }

    private companion object {
        const val CONFIG_ID = "json-element-decoder-test"
    }
}
