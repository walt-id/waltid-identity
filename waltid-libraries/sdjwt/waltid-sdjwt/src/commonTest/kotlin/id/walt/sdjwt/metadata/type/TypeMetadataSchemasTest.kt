package id.walt.sdjwt.metadata.type

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

class TypeMetadataSchemasTest {
    @Test
    fun `every ancestor schema constrains the final payload and resolves relative references`() = runTest {
        val leaf = Json.parseToJsonElement(TypeMetadataFixture.LEAF).jsonObject
        val schema = Json.parseToJsonElement("""{"type":"object","required":["iss"],"properties":{"account_id":{"${'$'}ref":"account.json"}}}""")
        val changedLeaf = JsonObject(leaf + ("schema" to schema)).toString().encodeToByteArray()
        val resolved = TypeMetadataResolver({ vct -> if (vct == "https://issuer.example/leaf") changedLeaf else TypeMetadataFixture.documents.getValue(vct) })
            .resolve("https://issuer.example/leaf")
        val requested = mutableListOf<String>()
        val schemas = TypeMetadataSchemas.compile(resolved) { uri ->
            requested += uri
            """{"type":"string","minLength":3}""".encodeToByteArray()
        }
        assertEquals(listOf("https://issuer.example/account.json"), requested)
        assertEquals(2, schemas.size)
        fun valid(value: String) = schemas.all { it.validate(Json.parseToJsonElement(value)) { } }
        assertTrue(valid("""{"account_id":"123","iss":"https://issuer.example"}"""))
        assertFalse(valid("""{"iss":"https://issuer.example"}"""))
        assertFalse(valid("""{"account_id":"123"}"""))
        assertFalse(valid("""{"account_id":"x","iss":"https://issuer.example"}"""))
    }

    @Test
    fun `retrieved schema preserves relative root identity and reference base`() = runTest {
        val uri = "https://issuer.example/schemas/retrieved.json"
        val metadata = """{"vct":"https://issuer.example/type","schema_uri":"$uri"}""".encodeToByteArray()
        val resolved = TypeMetadataResolver({ metadata }).resolve("https://issuer.example/type")
        val requested = mutableListOf<String>()
        val schema = TypeMetadataSchemas.compile(resolved) { target ->
            requested += target
            (when (target) {
                uri -> """{"${'$'}id":"root.json","type":"object","properties":{"a":{"${'$'}ref":"defs.json"}}}"""
                "https://issuer.example/schemas/defs.json" -> """{"type":"string","minLength":3}"""
                else -> error("Unexpected schema request")
            }).encodeToByteArray()
        }.single()
        assertEquals(listOf(uri, "https://issuer.example/schemas/defs.json"), requested)
        assertTrue(schema.validate(Json.parseToJsonElement("""{"a":"abc"}""")) {})
        assertFalse(schema.validate(Json.parseToJsonElement("""{"a":"x"}""")) {})
    }

    @Test
    fun `embedded schema resources and reference shaped instance data require no network`() = runTest {
        val metadata = """{"vct":"https://issuer.example/type","schema":{
            "${'$'}id":"https://issuer.example/root.json","type":"object",
            "${'$'}defs":{"str":{"${'$'}id":"string.json","type":"string","minLength":3}},
            "properties":{"a":{"${'$'}ref":"string.json"}},
            "examples":[{"${'$'}ref":"https://not-a-schema.example/data"}],
            "propertiesForFutureVocabulary":{"${'$'}ref":"https://not-a-schema.example/extension"}
        }}""".encodeToByteArray()
        val resolved = TypeMetadataResolver({ metadata }).resolve("https://issuer.example/type")
        var requests = 0
        val schema = TypeMetadataSchemas.compile(resolved) {
            requests++; error("Embedded resources and instance data must not be fetched")
        }.single()
        assertEquals(0, requests)
        assertTrue(schema.validate(Json.parseToJsonElement("""{"a":"abc"}""")) {})
        assertFalse(schema.validate(Json.parseToJsonElement("""{"a":"x"}""")) {})
    }

    @Test
    fun `URN inline schemas keep fragment references local for flat and inherited types`() = runTest {
        val vct = "urn:eudi:pid:1"
        val parentVct = "urn:eudi:base:1"
        val schema = Json.parseToJsonElement("""{
            "type":"object","${'$'}defs":{"name":{"type":"string","minLength":3}},
            "properties":{"name":{"${'$'}ref":"#/${'$'}defs/name"}}
        }""")
        val parent = buildJsonObject {
            put("vct", parentVct); put("schema", buildJsonObject { put("required", buildJsonArray { add("name") }) })
        }.toString().encodeToByteArray()
        for (inherited in listOf(false, true)) {
            val leaf = buildJsonObject {
                put("vct", vct); put("schema", schema)
                if (inherited) {
                    put("extends", parentVct)
                    put("extends#integrity", MetadataIntegrity.sha256(parent))
                }
            }.toString().encodeToByteArray()
            val resolved = TypeMetadataResolver({ if (it == vct) leaf else parent }).resolve(vct)
            var requests = 0
            val schemas = TypeMetadataSchemas.compile(resolved) {
                requests++; error("Local schema references must not be fetched")
            }
            assertEquals(0, requests)
            assertTrue(schemas.all { it.validate(Json.parseToJsonElement("""{"name":"Ada"}""")) {} })
            assertFalse(schemas.all { it.validate(Json.parseToJsonElement("""{"name":"x"}""")) {} })
            if (inherited) assertFalse(schemas.all { it.validate(Json.parseToJsonElement("{}")) {} })
        }
    }
}
