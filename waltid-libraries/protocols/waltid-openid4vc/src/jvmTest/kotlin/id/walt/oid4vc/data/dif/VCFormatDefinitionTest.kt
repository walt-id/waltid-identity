package id.walt.oid4vc.data.dif


import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VCFormatDefinitionTest {

    @Test
    fun `custom parameters are set although json object is empty`() {
        val definition = Json.decodeFromString<VCFormatDefinition>(
            """
            {}
        """.trimIndent()
        )
        assertNotNull(definition)
        assertNotNull(definition.customParameters)
        assertTrue(definition.customParameters.isEmpty())
    }

    @Test
    fun `customParameters is not in json object although it is initialized with empty map`() {
        val definition = VCFormatDefinition(
            alg = setOf("RS256", "RS384", "RS512"),
            proof_type = setOf("RS256", "RS384", "RS512"),
        )
        assertNotNull(definition.customParameters)
        val json = definition.toJSON()
        assertNull(json["customParameters"])
        assertNotNull(json)
    }

    @Test
    fun `customParameters is not in json object although it is initialized with empty map parameter`() {
        val json = VCFormatDefinition(
            alg = setOf("RS256", "RS384", "RS512"),
            proof_type = setOf("RS256", "RS384", "RS512"),
            customParameters = mapOf()
        ).toJSON()
        assertNotNull(json)
        assertNull(json["customParameters"])
    }

    @Test
    fun `customParameters is in json object when parameter is given`() {
        val json = VCFormatDefinition(
            alg = setOf("RS256", "RS384", "RS512"),
            proof_type = setOf("RS256", "RS384", "RS512"),
            customParameters = mapOf("param1" to JsonPrimitive("value 1"))
        ).toJSON()
        assertNotNull(json)
        assertNull(json["customParameters"])
        assertEquals("value 1", json["param1"]!!.jsonPrimitive.content)
    }
}