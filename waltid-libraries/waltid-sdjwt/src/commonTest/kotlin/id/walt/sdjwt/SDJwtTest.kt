
package id.walt.sdjwt

import korlibs.crypto.SHA256
import korlibs.crypto.encoding.ASCII
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
        assertContains(map = nestedDisclosure.value.jsonObject, key = SDJwt.DIGESTS_KEY)
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
        assertContains(map = nestedDisclosure.value.jsonObject, key = SDJwt.DIGESTS_KEY)
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

    @Test
    fun testParseKeyBindingJwt() {
        val issuerJwt = "eyJhbGciOiAiRVMyNTYiLCAidHlwIjogImV4YW1wbGUrc2Qtand0In0.eyJfc2QiOiBbIkNyUWU3UzVrcUJBSHQtbk1ZWGdjNmJkdDJTSDVhVFkxc1VfTS1QZ2tqUEkiLCAiSnpZakg0c3ZsaUgwUjNQeUVNZmVadTZKdDY5dTVxZWhabzdGN0VQWWxTRSIsICJQb3JGYnBLdVZ1Nnh5bUphZ3ZrRnNGWEFiUm9jMkpHbEFVQTJCQTRvN2NJIiwgIlRHZjRvTGJnd2Q1SlFhSHlLVlFaVTlVZEdFMHc1cnREc3JaemZVYW9tTG8iLCAiWFFfM2tQS3QxWHlYN0tBTmtxVlI2eVoyVmE1TnJQSXZQWWJ5TXZSS0JNTSIsICJYekZyendzY002R242Q0pEYzZ2Vks4QmtNbmZHOHZPU0tmcFBJWmRBZmRFIiwgImdiT3NJNEVkcTJ4Mkt3LXc1d1BFemFrb2I5aFYxY1JEMEFUTjNvUUw5Sk0iLCAianN1OXlWdWx3UVFsaEZsTV8zSmx6TWFTRnpnbGhRRzBEcGZheVF3TFVLNCJdLCAiaXNzIjogImh0dHBzOi8vaXNzdWVyLmV4YW1wbGUuY29tIiwgImlhdCI6IDE2ODMwMDAwMDAsICJleHAiOiAxODgzMDAwMDAwLCAic3ViIjogInVzZXJfNDIiLCAibmF0aW9uYWxpdGllcyI6IFt7Ii4uLiI6ICJwRm5kamtaX1ZDem15VGE2VWpsWm8zZGgta284YUlLUWM5RGxHemhhVllvIn0sIHsiLi4uIjogIjdDZjZKa1B1ZHJ5M2xjYndIZ2VaOGtoQXYxVTFPU2xlclAwVmtCSnJXWjAifV0sICJfc2RfYWxnIjogInNoYS0yNTYiLCAiY25mIjogeyJqd2siOiB7Imt0eSI6ICJFQyIsICJjcnYiOiAiUC0yNTYiLCAieCI6ICJUQ0FFUjE5WnZ1M09IRjRqNFc0dmZTVm9ISVAxSUxpbERsczd2Q2VHZW1jIiwgInkiOiAiWnhqaVdXYlpNUUdIVldLVlE0aGJTSWlyc1ZmdWVjQ0U2dDRqVDlGMkhaUSJ9fX0.Ds6Agd3LwQJuQW0V7EZLaX-ed2CpEvA7gFrVjuVJaPCzIAvLXhDzcmEzDzXJxx27iPs91uodf0wGVrpe529ZmQ~WyJlbHVWNU9nM2dTTklJOEVZbnN4QV9BIiwgImZhbWlseV9uYW1lIiwgIkRvZSJd~WyJBSngtMDk1VlBycFR0TjRRTU9xUk9BIiwgImFkZHJlc3MiLCB7InN0cmVldF9hZGRyZXNzIjogIjEyMyBNYWluIFN0IiwgImxvY2FsaXR5IjogIkFueXRvd24iLCAicmVnaW9uIjogIkFueXN0YXRlIiwgImNvdW50cnkiOiAiVVMifV0~WyIyR0xDNDJzS1F2ZUNmR2ZyeU5STjl3IiwgImdpdmVuX25hbWUiLCAiSm9obiJd~WyJsa2x4RjVqTVlsR1RQVW92TU5JdkNBIiwgIlVTIl0~"
        val kbJwt = "eyJhbGciOiAiRVMyNTYiLCAidHlwIjogImtiK2p3dCJ9.eyJub25jZSI6ICIxMjM0NTY3ODkwIiwgImF1ZCI6ICJodHRwczovL3ZlcmlmaWVyLmV4YW1wbGUub3JnIiwgImlhdCI6IDE3MTU3ODMyOTksICJzZF9oYXNoIjogIlFMbWYtQk1QUjhzY2RrckhVOGF5aTFqSk85WjdJcDEwVnJmV2VCTDd0MHMifQ._rLO1DtTo_OtJbLzNYSPGAYEwc11X67o-lAKBYux__oWRcLMV_o999VaJADaSc6UetudXNTkk_fAw65xz1F0Cw"
        val sdHash = KeyBindingJwt.parse(kbJwt).sdHash
        assertEquals(sdHash, SHA256.digest(ASCII.encode(issuerJwt)).base64Url)
    }
}
