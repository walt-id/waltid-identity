
package id.walt.sdjwt

import kotlinx.serialization.json.*
import kotlin.test.*

class SDJwtTest {
    @Test
    fun testParseSdJwt() {
        val sdJwtString =
            "eyJraWQiOiJkaWQ6a2V5Ono2TWtuM2dWOFY2M2JScEJNdEFwbm5BaWhDTXZEYVBlcno2aWFyMURwZE5LZTNrMSN6Nk1rbjNnVjhWNjNiUnBCTXRBcG5uQWloQ012RGFQZXJ6NmlhcjFEcGROS2UzazEiLCJ0eXAiOiJKV1QiLCJhbGciOiJFZERTQSJ9.eyJzdWIiOiJkaWQ6a2V5Ono2TWtuM2dWOFY2M2JScEJNdEFwbm5BaWhDTXZEYVBlcno2aWFyMURwZE5LZTNrMSIsIm5iZiI6MTY4NDkxOTg2NCwiaXNzIjoiZGlkOmtleTp6Nk1rbjNnVjhWNjNiUnBCTXRBcG5uQWloQ012RGFQZXJ6NmlhcjFEcGROS2UzazEiLCJpYXQiOjE2ODQ5MTk4NjQsInZjIjp7InR5cGUiOlsiVmVyaWZpYWJsZUNyZWRlbnRpYWwiLCJWZXJpZmlhYmxlQXR0ZXN0YXRpb24iLCJWZXJpZmlhYmxlSWQiXSwiQGNvbnRleHQiOlsiaHR0cHM6Ly93d3cudzMub3JnLzIwMTgvY3JlZGVudGlhbHMvdjEiXSwiaWQiOiJ1cm46dXVpZDplMDNjMDY2OC0yMDhmLTRkNzctYTBjNi02ZDBkZjAxYWRmYWQiLCJpc3N1ZXIiOiJkaWQ6a2V5Ono2TWtuM2dWOFY2M2JScEJNdEFwbm5BaWhDTXZEYVBlcno2aWFyMURwZE5LZTNrMSIsImlzc3VhbmNlRGF0ZSI6IjIwMjMtMDUtMjRUMDk6MTc6NDRaIiwiaXNzdWVkIjoiMjAyMy0wNS0yNFQwOToxNzo0NFoiLCJ2YWxpZEZyb20iOiIyMDIzLTA1LTI0VDA5OjE3OjQ0WiIsImNyZWRlbnRpYWxTY2hlbWEiOnsiaWQiOiJodHRwczovL3Jhdy5naXRodWJ1c2VyY29udGVudC5jb20vd2FsdC1pZC93YWx0aWQtc3Npa2l0LXZjbGliL21hc3Rlci9zcmMvdGVzdC9yZXNvdXJjZXMvc2NoZW1hcy9WZXJpZmlhYmxlSWQuanNvbiIsInR5cGUiOiJGdWxsSnNvblNjaGVtYVZhbGlkYXRvcjIwMjEifSwiZXZpZGVuY2UiOlt7ImRvY3VtZW50UHJlc2VuY2UiOlsiUGh5c2ljYWwiXSwiZXZpZGVuY2VEb2N1bWVudCI6WyJQYXNzcG9ydCJdLCJzdWJqZWN0UHJlc2VuY2UiOiJQaHlzaWNhbCIsInR5cGUiOlsiRG9jdW1lbnRWZXJpZmljYXRpb24iXSwidmVyaWZpZXIiOiJkaWQ6ZWJzaToyQTlCWjlTVWU2QmF0YWNTcHZzMVY1Q2RqSHZMcFE3YkVzaTJKYjZMZEhLblF4YU4ifV0sIl9zZCI6WyJzRkoxY1BOR2d5NktrRzAxOW9tdFBvVm5GYXR6clRXQkV0Si1yQmpzQU1VIl19LCJqdGkiOiJ1cm46dXVpZDplMDNjMDY2OC0yMDhmLTRkNzctYTBjNi02ZDBkZjAxYWRmYWQifQ.XqhNYYB9CITa0KCMOj1v1tbQvj3sfuDmGaKL3sDDJndQPGEa_QosbunSnBef5O4optTslUOSaplR7BiTiY2cCQ~WyJfZ2RWc3dIS2U2b3ZVMElYT3NXQ1Z3IiwiZGF0ZU9mQmlydGgiLCIxOTgzLTA3LTA1Il0~WyJsYVpHcU5rczE1YU5USEFvYnFfWEx3IiwiZmlyc3ROYW1lIiwiU2V2ZXJpbiJd~WyJDc1FfVkFaeUVzVmxSWWQ1YjJPNWhRIiwiY3JlZGVudGlhbFN1YmplY3QiLHsiaWQiOiJkaWQ6a2V5Ono2TWtuM2dWOFY2M2JScEJNdEFwbm5BaWhDTXZEYVBlcno2aWFyMURwZE5LZTNrMSIsImN1cnJlbnRBZGRyZXNzIjpbIlZpZW5uYSJdLCJmYW1pbHlOYW1lIjoiU3RhbXBsZXIiLCJnZW5kZXIiOiJtYWxlIiwibmFtZUFuZEZhbWlseU5hbWVBdEJpcnRoIjoiSmFuZSBET0UiLCJwZXJzb25hbElkZW50aWZpZXIiOiIwOTA0MDA4MDg0SCIsInBsYWNlT2ZCaXJ0aCI6IkdyYXoiLCJfc2QiOlsiUXh4ZXlNbHk3dU9feWNRaThuU2Zmb2VUb2JQSDRVZ0JfVGh3LUMyMTJDNCIsImNJeXVGWVNBUjAtZ2JpMGxQbmZTWVQySExEdlpsTlBOSGoxUGY0am9uek0iXX1d"
        val sdJwt = SDJwt.parse(sdJwtString)
        assertContains(map = sdJwt.undisclosedPayload, key = "sub")
        assertContains(map = sdJwt.undisclosedPayload, key = "vc")
        assertContains(map = sdJwt.undisclosedPayload["vc"]!!.jsonObject, key = SDJwt.DIGESTS_KEY)
        assertFalse(actual = sdJwt.undisclosedPayload["vc"]!!.jsonObject.containsKey("credentialSubject"))

        assertContains(map = sdJwt.fullPayload["vc"]!!.jsonObject, key = "credentialSubject")

        println(sdJwt.fullPayload.toString())
    }

    @Test
    fun testSDPayloadGeneration() {
        val fullPayload = buildJsonObject {
            put("sub", "1234")
            put("nestedObject", buildJsonObject {
                put("arrProp", buildJsonArray {
                    add("item 1")
                    add("item 2")
                })
            })
        }

        val sdPayload_1 = SDPayload.createSDPayload(fullPayload, buildJsonObject { })

        assertContains(map = sdPayload_1.undisclosedPayload, key = SDJwt.DIGESTS_KEY)
        assertFalse(actual = sdPayload_1.undisclosedPayload.keys.any { setOf("sub", "nestedObject").contains(it) })
        assertEquals(
            expected = fullPayload,
            actual = Json.parseToJsonElement(sdPayload_1.fullPayload.toString()).jsonObject
        )
        assertEquals(expected = sdPayload_1.digestedDisclosures.size, actual = sdPayload_1.undisclosedPayload[SDJwt.DIGESTS_KEY]!!.jsonArray.size)


        val sdPayload_2 = SDPayload.createSDPayload(fullPayload, buildJsonObject {
            put("nestedObject", buildJsonObject { })
        })

        assertContains(map = sdPayload_2.undisclosedPayload, key = SDJwt.DIGESTS_KEY)
        assertContains(map = sdPayload_2.undisclosedPayload, key = "nestedObject")
        assertFalse(actual = sdPayload_2.undisclosedPayload.containsKey("sub"))
        assertContains(map = sdPayload_2.undisclosedPayload["nestedObject"]!!.jsonObject, key = SDJwt.DIGESTS_KEY)
        assertFalse(actual = sdPayload_2.undisclosedPayload["nestedObject"]!!.jsonObject.containsKey("arrProp"))
        assertEquals(expected = fullPayload, actual = Json.parseToJsonElement(sdPayload_2.fullPayload.toString()).jsonObject)
        assertEquals(expected = sdPayload_2.digestedDisclosures.size, actual = sdPayload_2.undisclosedPayload[SDJwt.DIGESTS_KEY]!!.jsonArray.size +
                sdPayload_2.undisclosedPayload["nestedObject"]!!.jsonObject[SDJwt.DIGESTS_KEY]!!.jsonArray.size)


        val sdPayload_3 = SDPayload.createSDPayload(
            fullPayload, mapOf(
                "sub" to SDField(true),
                "nestedObject" to SDField(
                    true, mapOf(
                        "arrProp" to SDField(true)
                    ).toSDMap()
                )
            ).toSDMap()
        )

        assertContains(map = sdPayload_3.undisclosedPayload, key = SDJwt.DIGESTS_KEY)
        assertFalse(actual = sdPayload_3.undisclosedPayload.keys.any { setOf("sub", "nestedObject").contains(it) })
        val nestedDisclosure = sdPayload_3.sDisclosures.firstOrNull { sd -> sd.key == "nestedObject" && sd.value is JsonObject }
        assertNotNull(actual = nestedDisclosure)
        assertContains(map = nestedDisclosure!!.value.jsonObject, key = SDJwt.DIGESTS_KEY)
        assertFalse(actual = nestedDisclosure.value.jsonObject.containsKey("arrProp"))
        assertEquals(expected = fullPayload, actual = Json.parseToJsonElement(sdPayload_3.fullPayload.toString()))
        assertEquals(expected = sdPayload_3.digestedDisclosures.size, actual = sdPayload_3.undisclosedPayload[SDJwt.DIGESTS_KEY]!!.jsonArray.size +
                nestedDisclosure.value.jsonObject[SDJwt.DIGESTS_KEY]!!.jsonArray.size)
    }

    @Test
    fun testSDPayloadGenerationWithDecoys() {
        val fullPayload = buildJsonObject {
            put("sub", "1234")
            put("nestedObject", buildJsonObject {
                put("arrProp", buildJsonArray {
                    add("item 1")
                    add("item 2")
                })
            })
        }

        val sdPayload_4 = SDPayload.createSDPayload(
            fullPayload, mapOf(
                "sub" to SDField(true),
                "nestedObject" to SDField(
                    true, mapOf(
                        "arrProp" to SDField(true)
                    ).toSDMap(decoyMode = DecoyMode.FIXED, decoys = 5)
                )
            ).toSDMap(decoyMode = DecoyMode.RANDOM, decoys = 5)
        )

        assertContains(map = sdPayload_4.undisclosedPayload, key = SDJwt.DIGESTS_KEY)
        assertFalse(actual = sdPayload_4.undisclosedPayload.keys.any { setOf("sub", "nestedObject").contains(it) })
        val nestedDisclosure = sdPayload_4.sDisclosures.firstOrNull { sd -> sd.key == "nestedObject" && sd.value is JsonObject }
        assertNotNull(actual = nestedDisclosure)
        assertContains(map = nestedDisclosure!!.value.jsonObject, key = SDJwt.DIGESTS_KEY)
        assertFalse(actual = nestedDisclosure.value.jsonObject.containsKey("arrProp"))
        val numSdFieldsLevel1 = sdPayload_4.sdMap.count { it.value.sd }
        assertTrue(actual = sdPayload_4.undisclosedPayload[SDJwt.DIGESTS_KEY]!!.jsonArray.size in IntRange(numSdFieldsLevel1 + 1, numSdFieldsLevel1 + 5))
        val numSdFieldsLevel2 = sdPayload_4.sdMap["nestedObject"]!!.children!!.count { it.value.sd }
        assertEquals(expected = numSdFieldsLevel2 + 5, actual = nestedDisclosure.value.jsonObject[SDJwt.DIGESTS_KEY]!!.jsonArray.size)
    }

    @Test
    fun testSdMapFromJsonPaths() {
        val sdmap1 = SDMap.generateSDMap(listOf("credentialSubject", "credentialSubject.firstName"))
        assertContains(map = sdmap1, key = "credentialSubject")
        assertTrue(actual = sdmap1["credentialSubject"]!!.sd)
        assertContains(map = sdmap1["credentialSubject"]!!.children!!, key = "firstName")
        assertTrue(actual = sdmap1["credentialSubject"]!!.children!!["firstName"]!!.sd)

        val sdmap2 = SDMap.generateSDMap(listOf("credentialSubject.firstName"))
        assertContains(map = sdmap2, key = "credentialSubject")
        assertFalse(actual = sdmap2["credentialSubject"]!!.sd)
        assertContains(map = sdmap2["credentialSubject"]!!.children!!, key = "firstName")
        assertTrue(actual = sdmap2["credentialSubject"]!!.children!!["firstName"]!!.sd)

    }
}
