package id.walt.issuer2.utils

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class JsonObjectPathMapperTest {

    @Test
    fun `maps ID token claims to mdoc namespace paths with dotted object keys`() {
        val credentialData = JsonObjectPathMapper.fromSourceToDestinationJsonPathsMap(
            source = buildJsonObject {
                put("family_name", "Doe")
                put("given_name", "Jane")
            },
            destination = buildJsonObject {
                put("org.iso.23220.photoid.1", buildJsonObject {
                    put("family_name_unicode", "")
                    put("given_name_unicode", "")
                })
            },
            jsonPathMapConfig = mapOf(
                "$.family_name" to "$.['org.iso.23220.photoid.1'].family_name_unicode",
                "$.given_name" to "$.['org.iso.23220.photoid.1'].given_name_unicode",
            ),
        )

        val namespace = credentialData["org.iso.23220.photoid.1"]!!.jsonObject
        assertEquals("Doe", namespace["family_name_unicode"]!!.jsonPrimitive.content)
        assertEquals("Jane", namespace["given_name_unicode"]!!.jsonPrimitive.content)
    }

    @Test
    fun `maps quoted source claim keys with dots`() {
        val credentialData = JsonObjectPathMapper.fromSourceToDestinationJsonPathsMap(
            source = buildJsonObject {
                put("family.name", "Doe")
            },
            destination = buildJsonObject {
                put("credentialSubject", buildJsonObject {
                    put("familyName", "")
                })
            },
            jsonPathMapConfig = mapOf(
                "$.['family.name']" to "$.credentialSubject.familyName",
            ),
        )

        assertEquals(
            "Doe",
            credentialData["credentialSubject"]!!.jsonObject["familyName"]!!.jsonPrimitive.content,
        )
    }
}
