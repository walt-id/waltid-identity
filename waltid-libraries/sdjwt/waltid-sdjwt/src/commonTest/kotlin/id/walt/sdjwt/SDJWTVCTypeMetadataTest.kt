package id.walt.sdjwt


import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SDJWTVCTypeMetadataTest {

    @Test
    fun shouldDeserializeMetadataWithCustomParameters() {
        val metadataJsonString = """
{
  "vct": "https://credentials.walt.id/identity_credential",
  "additional-parameter": "why not do it"
}             
         """.trimIndent()

        listOf(
            Json.decodeFromString<SDJWTVCTypeMetadata>(metadataJsonString),
            SDJWTVCTypeMetadata.fromJSONString(metadataJsonString)
        ).forEach {
            assertNotNull(it)
            assertNotNull(it.customParameters)
            assertEquals("https://credentials.walt.id/identity_credential", it.vct)
            assertEquals("why not do it", it.customParameters["additional-parameter"]?.jsonPrimitive?.content)
        }
    }

    @Test
    fun shouldSerializeMetadataWithCustomParameters() {
        val metadata = SDJWTVCTypeMetadata(
            vct = "https://credentials.walt.id/identity_credential",
            customParameters = mapOf("hello-p" to JsonPrimitive("world"))
        )
        listOf(
            Json.encodeToString(metadata),
            metadata.toJSONString()
        ).forEach {
            assertNotNull(it)
            //there must not be a property named "customParameters" in the json
            assertNull("customParameters".toRegex().find(it))
            //instead we expect a property "hello-p" in the json
            assertNotNull("hello-p\"\\s*:\\s*\"world\"".toRegex().find(it))
        }
    }
}


