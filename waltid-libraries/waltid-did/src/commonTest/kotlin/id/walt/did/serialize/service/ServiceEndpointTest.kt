package id.walt.did.serialize.service

import id.walt.crypto.utils.JsonUtils.toJsonElement
import id.walt.did.dids.document.models.service.ServiceEndpoint
import id.walt.did.dids.document.models.service.ServiceEndpointObject
import id.walt.did.dids.document.models.service.ServiceEndpointURL
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

class ServiceEndpointTest {

    @Test
    fun testServiceEndpointURLSerialization() {
        val svcEndpoint = ServiceEndpointURL("something")
        val svcJsonEncodedString = "\"${svcEndpoint.url}\""
        //encoding single element
        assertEquals(
            expected = svcJsonEncodedString,
            actual = Json.encodeToString(svcEndpoint.url),
        )
        //decoding single element
        assertEquals(
            expected = svcEndpoint,
            actual = Json.decodeFromString<ServiceEndpoint>(svcJsonEncodedString)
        )
        val svcEndpointList = listOf(
            ServiceEndpointURL("something"),
            ServiceEndpointURL("else"),
        )
        val svcListJsonEncodedString = "[\"${svcEndpointList[0].url}\",\"${svcEndpointList[1].url}\"]"
        //encoding list of elements
        assertEquals(
            expected = svcListJsonEncodedString,
            actual = Json.encodeToString(svcEndpointList),
        )
        //decoding list of elements
        assertEquals(
            expected = svcEndpointList,
            actual = Json.decodeFromString<List<ServiceEndpoint>>(svcListJsonEncodedString),
        )
    }

    @Test
    fun testServiceEndpointObjectSerialization() {
        val svcEndpointJsonObject = ServiceEndpointObject(
            buildJsonObject {
                put("some-url-property", "url-value".toJsonElement())
                put("some-additional-property", "some-value".toJsonElement())
            }
        )
        val svcJsonEncodedString = """{"some-url-property":"url-value","some-additional-property":"some-value"}"""
        //encoding single element
        assertEquals(
            expected = svcJsonEncodedString,
            actual = Json.encodeToString(svcEndpointJsonObject),
        )
        //decoding single element
        assertEquals(
            expected = svcEndpointJsonObject,
            actual = Json.decodeFromString<ServiceEndpoint>(svcJsonEncodedString),
        )
        val svcEndpointList = listOf(
            ServiceEndpointObject(
                buildJsonObject {
                    put("some-url-property1", "url-value1".toJsonElement())
                    put("some-additional-property1", "some-value1".toJsonElement())
                },
            ),
            ServiceEndpointObject(
                buildJsonObject {
                    put("some-url-property2", "url-value2".toJsonElement())
                    put("some-additional-property2", "some-value2".toJsonElement())
                },
            )

        )
        val svcListJsonEncodedString =
            """[{"some-url-property1":"url-value1","some-additional-property1":"some-value1"},{"some-url-property2":"url-value2","some-additional-property2":"some-value2"}]"""
        //encoding list of elements
        assertEquals(
            expected = svcListJsonEncodedString,
            actual = Json.encodeToString(svcEndpointList)
        )
        //decoding list of elements
        assertEquals(
            expected = svcEndpointList,
            actual = Json.decodeFromString<List<ServiceEndpoint>>(svcListJsonEncodedString),
        )
    }

    @Test
    fun testServiceEndpointMixedSerialization() {
        val mixSvcEndpointList = listOf(
            ServiceEndpointObject(
                    buildJsonObject {
                        put("some-url-property1", "url-value1".toJsonElement())
                        put("some-additional-property1", "some-value1".toJsonElement())
                    },
                ),
            ServiceEndpointURL("something"),
            ServiceEndpointURL("else"),
            ServiceEndpointObject(
                    buildJsonObject {
                        put("some-url-property2", "url-value2".toJsonElement())
                        put("some-additional-property2", "some-value2".toJsonElement())
                    },
                )

        )
        val mixSvcJsonEncodedString =
            """[{"some-url-property1":"url-value1","some-additional-property1":"some-value1"},"something","else",{"some-url-property2":"url-value2","some-additional-property2":"some-value2"}]"""
        //encoding
        assertEquals(
            expected = mixSvcJsonEncodedString,
            actual = Json.encodeToString(mixSvcEndpointList),
        )
        //decoding
        assertEquals(
            expected = mixSvcEndpointList,
            actual = Json.decodeFromString<List<ServiceEndpoint>>(mixSvcJsonEncodedString),
        )
    }
}
