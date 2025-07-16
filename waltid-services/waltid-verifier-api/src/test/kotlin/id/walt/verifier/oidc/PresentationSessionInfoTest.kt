package id.walt.verifier.oidc

import id.walt.commons.testing.utils.ServiceTestUtils.loadResource
import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.oid4vc.responses.TokenErrorCode
import id.walt.oid4vc.responses.TokenResponse
import kotlinx.serialization.json.*
import kotlin.test.*

class PresentationSessionInfoTest {

    @Test
    fun shouldSerializeCustomParameters() {

        val enc = Json.encodeToJsonElement(
            PresentationSessionInfo(
                id = "testId",
                presentationDefinition = PresentationDefinition(
                    inputDescriptors = listOf(),
                    customParameters = mapOf(
                        "customArray" to JsonArray(
                            listOf(
                                JsonPrimitive("a"), JsonPrimitive("b")
                            )
                        )
                    )
                ),
                tokenResponse = TokenResponse.error(TokenErrorCode.server_error),
                customParameters = mapOf(
                    "myCustomParam" to JsonPrimitive("custom string value"),
                    "customObject" to JsonObject(
                        mapOf(
                            "id" to JsonPrimitive(20),
                            "name" to JsonPrimitive("obj-name")
                        )
                    )
                )
            )
        )
        assertNotNull(enc).apply {
            assertEquals("custom string value", assertNotNull(jsonObject["myCustomParam"]).jsonPrimitive.content)
            assertNotNull(jsonObject["customObject"]).apply {
                assertEquals(20, jsonObject["id"]!!.jsonPrimitive.int)
                assertEquals("obj-name", jsonObject["name"]!!.jsonPrimitive.content)
            }
            assertNotNull(jsonObject["presentationDefinition"]).apply {
                assertNotNull(jsonObject["customArray"]).jsonArray.apply {
                    assertEquals(2, size)
                    assertTrue(contains(JsonPrimitive("a")))
                    assertTrue(contains(JsonPrimitive("b")))
                }
            }
        }
        assertNotNull(Json.encodeToString(enc)).apply {
            assertNull(
                "customParameters".toRegex().find(this),
                "customParameters is found but should be merged with object"
            )
        }
    }

    @Test
    fun shouldDeserializeCustomParameters() {
        assertNotNull(
            loadResource("presentationSessionInfoWithCustomParameters.json"),
            "Could not load resource file"
        ).let {
            Json.decodeFromString<PresentationSessionInfo>(it)
        }.apply {
            assertNotNull(customParameters)
            assertNotNull(customParameters["customParmaA"])
            assertEquals("a session info custom param", customParameters["customParmaA"]!!.jsonPrimitive.content)

            assertNotNull(presentationDefinition).apply {
                assertNotNull(customParameters)
                assertTrue(customParameters!!.containsKey("should-present"))
                assertEquals(true, customParameters!!["should-present"]!!.jsonPrimitive.boolean)
                assertNotNull(inputDescriptors).apply {
                    assertEquals(1, size)
                    first().apply {
                        assertNotNull(customParameters)
                        assertTrue(customParameters!!.containsKey("sequence-number"))
                        assertEquals(1, customParameters!!["sequence-number"]!!.jsonPrimitive.int)
                    }
                }
            }

            assertNotNull(tokenResponse).apply {
                assertNotNull(customParameters)
                assertTrue(customParameters!!.containsKey("extra-param"))
                assertEquals("nice token", customParameters!!["extra-param"]!!.jsonPrimitive.content)
            }
        }.apply {
            assertNotNull(Json.encodeToString(this)).apply {
                assertNull(
                    "customParameters".toRegex().find(this),
                    "customParameters is found but should be merged with object"
                )
            }
        }
    }
}