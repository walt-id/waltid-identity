@file:OptIn(com.sksamuel.hoplite.ExperimentalHoplite::class, kotlinx.serialization.ExperimentalSerializationApi::class)
package id.walt.issuer2.config

import com.sksamuel.hoplite.addFileSource
import com.typesafe.config.ConfigFactory
import id.walt.commons.config.ConfigManager
import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class Issuer2MetadataConfigTest {
    @TempDir
    lateinit var directory: Path

    data class OtherConfig(val value: JsonElement)

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @AfterEach
    fun clearConfig() {
        System.clearProperty("config.file.metadata-path-test")
        ConfigManager.preclear()
    }

    private fun file(text: String): Path = Files.createTempFile(directory, "metadata-", ".conf").also { Files.writeString(it, text) }

    private fun config(path: String, extra: String = ""): String = """
      credentialConfigurations.mdoc {
        format = mso_mdoc
        doctype = example
        credential_metadata.claims = [{path = ["ns", "map", $path]}]
        client_id = 10000000000000000001
      }
      $extra
    """.trimIndent()

    private fun load(text: String, args: Array<String> = emptyArray(), replacement: String? = null): Issuer2MetadataConfig {
        ConfigManager.preclear()
        registerIssuer2ConfigDecoders()
        replacement?.let { override ->
            ConfigManager.customizeConfigLoader(Issuer2MetadataConfig::class) {
                addParser("conf", Issuer2MetadataHoconParser())
                addDecoder(Issuer2MetadataJsonDecoder())
                addFileSource(file(override).toFile())
            }
        }
        System.setProperty("config.file.metadata-path-test", file(text).toString())
        return ConfigManager.loadConfig(
            ConfigManager.ConfigData("metadata-path-test", Issuer2MetadataConfig::class), args,
        ).getOrThrow() as Issuer2MetadataConfig
    }

    private fun segment(c: Issuer2MetadataConfig): JsonPrimitive = c.credentialConfigurations.getValue("mdoc")
        .jsonObject.getValue("credential_metadata").jsonObject.getValue("claims").jsonArray[0]
        .jsonObject.getValue("path").jsonArray[2].jsonPrimitive

    @Test
    fun defaultParserPreservesUnrelatedProviderString() {
        ConfigManager.preclear()
        registerIssuer2ConfigDecoders()
        System.setProperty("config.file.metadata-path-test", file("value.client_id = 10000000000000000001").toString())
        val c = ConfigManager.loadConfig(
            ConfigManager.ConfigData("metadata-path-test", OtherConfig::class), emptyArray(),
        ).getOrThrow() as OtherConfig
        assertTrue(c.value.jsonObject.getValue("client_id").jsonPrimitive.isString)
    }

    @Test
    fun lowercaseConfigFieldUsesTheSamePathSemantics() {
        val metadata = load(config("10000000000000000001").replace("credentialConfigurations", "credentialconfigurations"))
        assertFalse(segment(metadata).isString)
        assertEquals("10000000000000000001", segment(metadata).content)
    }

    @Test
    fun clearingRegistrationsRestoresTheDefaultParser() {
        load(config("10000000000000000001"))
        ConfigManager.preclear()
        val metadata = ConfigManager.loadConfig(
            ConfigManager.ConfigData("metadata-path-test", Issuer2MetadataConfig::class), emptyArray(),
        ).getOrThrow() as Issuer2MetadataConfig
        assertTrue(segment(metadata).isString)
    }

    @Test
    fun exactPositiveNegativeOverflowSurvivesRealProtocolSerializer() {
        for (token in listOf("10000000000000000000", "10000000000000000001", "-10000000000000000001")) {
            val c = load(config(token))
            assertEquals(token, segment(c).content)
            assertFalse(segment(c).isString)
            val decoded = json.decodeFromJsonElement(CredentialConfiguration.serializer(), c.credentialConfigurations.getValue("mdoc"))
            val emitted = json.encodeToJsonElement(CredentialConfiguration.serializer(), decoded).jsonObject
                .getValue("credential_metadata").jsonObject.getValue("claims").jsonArray[0]
                .jsonObject.getValue("path").jsonArray[2].jsonPrimitive
            assertFalse(emitted.isString)
            assertEquals(token, emitted.content)
        }
    }

    @Test
    fun quotingZeroPaddingAndUnrelatedFieldsStayStrings() {
        for (token in listOf("\"10000000000000000001\"", "010000000000000000000", "-010000000000000000000")) {
            val c = load(config(token))
            assertTrue(segment(c).isString)
            val provider = c.credentialConfigurations.getValue("mdoc").jsonObject.getValue("client_id").jsonPrimitive
            assertTrue(provider.isString)
            assertEquals("10000000000000000001", provider.content)
            Json.parseToJsonElement(c.credentialConfigurations.getValue("mdoc").toString())
        }
    }

    @Test
    fun substitutionsPreserveQuoting() {
        val unquoted = load(config("${'$'}{large}", "large = 10000000000000000001"))
        assertFalse(segment(unquoted).isString)
        assertEquals("10000000000000000001", segment(unquoted).content)
        val quoted = load(config("${'$'}{large}", "large = \"10000000000000000001\""))
        assertTrue(segment(quoted).isString)
    }

    @Test
    fun typesafeConfigStillDistinguishesUnquotedStrings() {
        val root = ConfigFactory.parseString(
            "unquoted = 10000000000000000001\nquoted = \"10000000000000000001\"",
        ).root()
        assertEquals(Issuer2MetadataHoconParser.UNQUOTED_STRING_CLASS, root.getValue("unquoted").javaClass.name)
        assertNotEquals(Issuer2MetadataHoconParser.UNQUOTED_STRING_CLASS, root.getValue("quoted").javaClass.name)
    }

    @Test
    fun smallIntegersAndNullKeepExistingTypes() {
        for (token in listOf("0", "9223372036854775807", "-9223372036854775808")) {
            val metadata = load(config(token))
            assertEquals(token, segment(metadata).content)
            assertFalse(segment(metadata).isString)
        }
        assertEquals(JsonNull, segment(load(config("null"))))
    }

    @Test
    fun cliFormatOverrideIsAppliedBeforeDecidingPromotion() {
        val c = load(config("10000000000000000001"), arrayOf("--credentialConfigurations.mdoc.format=dc+sd-jwt"))
        assertEquals("dc+sd-jwt", c.credentialConfigurations.getValue("mdoc").jsonObject.getValue("format").jsonPrimitive.content)
        assertTrue(segment(c).isString)
    }

    @Test
    fun higherPriorityFileReplacesTaggedPathWithoutKeepingItsMarker() {
        val replacement = """credentialConfigurations.mdoc.credential_metadata.claims = [{path=["ns", "map", "10000000000000000001"]}]"""
        val c = load(config("10000000000000000000"), replacement = replacement)
        assertTrue(segment(c).isString)
        assertEquals("10000000000000000001", segment(c).content)
    }

    @Test
    fun issuerDisplayAndNamespaceOrDataElementPositionsAreNotPromoted() {
        val c = load(config("10000000000000000001", "issuerDisplay = [{name=10000000000000000001}]"))
        assertTrue(assertNotNull(c.issuerDisplay)[0].jsonObject.getValue("name").jsonPrimitive.isString)
        val text = config("null").replace("[\"ns\", \"map\", null]", "[10000000000000000001,10000000000000000002,null]")
        val p = load(text).credentialConfigurations.getValue("mdoc").jsonObject.getValue("credential_metadata")
            .jsonObject.getValue("claims").jsonArray[0].jsonObject.getValue("path").jsonArray
        assertTrue(p[0].jsonPrimitive.isString)
        assertTrue(p[1].jsonPrimitive.isString)
    }
}
