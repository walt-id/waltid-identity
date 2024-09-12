package id.walt.did.serialize.service

import id.walt.crypto.utils.JsonUtils.toJsonElement
import id.walt.did.dids.document.models.service.ServiceBlock
import id.walt.did.dids.document.models.service.ServiceEndpointObject
import id.walt.did.dids.document.models.service.ServiceEndpointURL
import id.walt.did.dids.document.models.service.ServiceType
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

class ServiceBlockTest {

    private val customProperties = mapOf(
        "ckey1" to "cvalue1".toJsonElement(),
        "ckey2" to "cvalue2".toJsonElement(),
    )

    @Test
    fun testServiceBlockEndpointURLSerialization() = runTest {
        //with URL endpoint block and no custom properties
        val svcBlockWithURLEndpointNoCustom = ServiceBlock(
            id = "some-id",
            type = ServiceType.DIDCommMessaging,
            serviceEndpoint = ServiceEndpointURL("some-url")
        )
        val svcBlockWithURLEndpointNoCustomJsonEncodedString = """{"id":"some-id","type":"DIDCommMessaging","serviceEndpoint":"some-url"}"""
        //encode single element with URL endpoint and no custom properties
        assertEquals(
            expected = svcBlockWithURLEndpointNoCustomJsonEncodedString,
            actual = Json.encodeToString(svcBlockWithURLEndpointNoCustom),
        )
        //decode single element with URL endpoint and no custom properties
        assertEquals(
            expected = svcBlockWithURLEndpointNoCustom,
            actual = Json.decodeFromString(svcBlockWithURLEndpointNoCustomJsonEncodedString),
        )
        val svcBlockWithURLEndpointNoCustomListJsonEncodedString = """[${svcBlockWithURLEndpointNoCustomJsonEncodedString}]"""
        //encode single element with URL endpoint and no custom properties as list
        assertEquals(
            expected = svcBlockWithURLEndpointNoCustomListJsonEncodedString,
            actual = Json.encodeToString(listOf(svcBlockWithURLEndpointNoCustom))
        )
        //decode single element with URL endpoint and no custom properties as list
        assertEquals(
            expected = listOf(svcBlockWithURLEndpointNoCustom),
            actual = Json.decodeFromString(svcBlockWithURLEndpointNoCustomListJsonEncodedString)
        )
        //with URL endpoint block and custom properties
        val svcBlockWithURLEndpointCustom = ServiceBlock(
            id = "some-id",
            type = ServiceType.DIDCommMessaging,
            serviceEndpoint = ServiceEndpointURL("some-url"),
            customProperties = customProperties,
        )
        val svcBlockWithURLEndpointCustomJsonEncodedString =
            """{"id":"some-id","type":"DIDCommMessaging","serviceEndpoint":"some-url","ckey1":"cvalue1","ckey2":"cvalue2"}"""
        //encode single element with URL endpoint and custom properties
        assertEquals(
            expected = svcBlockWithURLEndpointCustomJsonEncodedString,
            actual = Json.encodeToString(svcBlockWithURLEndpointCustom),
        )
        //decode single element with URL endpoint and custom properties
        assertEquals(
            expected = svcBlockWithURLEndpointCustom,
            actual = Json.decodeFromString(svcBlockWithURLEndpointCustomJsonEncodedString),
        )
        val svcBlockWithURLEndpointCustomListJsonEncodedString = """[${svcBlockWithURLEndpointCustomJsonEncodedString}]"""
        //encode single element with URL endpoint and custom properties as list
        assertEquals(
            expected = svcBlockWithURLEndpointCustomListJsonEncodedString,
            actual = Json.encodeToString(listOf(svcBlockWithURLEndpointCustom))
        )
        //decode single element with URL endpoint and custom properties as list
        assertEquals(
            expected = listOf(svcBlockWithURLEndpointCustom),
            actual = Json.decodeFromString(svcBlockWithURLEndpointCustomListJsonEncodedString)
        )
    }
    @Test
    fun testServiceBlockEndpointObjectSerialization() = runTest {
        //with object endpoint block and no custom properties
        val svcBlockWithObjectEndpointNoCustom = ServiceBlock(
            id = "some-id",
            type = ServiceType.DIDCommMessaging,
            serviceEndpoint = ServiceEndpointObject(
                buildJsonObject {
                    put("some-url-property", "url-value".toJsonElement())
                    put("some-additional-property", "some-value".toJsonElement())
                }
            ),
        )
        val svcBlockWithObjectEndpointNoCustomJsonEncodedString =
            """{"id":"some-id","type":"DIDCommMessaging","serviceEndpoint":{"some-url-property":"url-value","some-additional-property":"some-value"}}"""
        //encode single element with object endpoint and no custom properties
        assertEquals(
            expected = svcBlockWithObjectEndpointNoCustomJsonEncodedString,
            actual = Json.encodeToString(svcBlockWithObjectEndpointNoCustom),
        )
        //decode single element with object endpoint and no custom properties
        assertEquals(
            expected = svcBlockWithObjectEndpointNoCustom,
            actual = Json.decodeFromString(svcBlockWithObjectEndpointNoCustomJsonEncodedString),
        )
        val svcBlockWithObjectEndpointNoCustomListJsonEncodedString = """[${svcBlockWithObjectEndpointNoCustomJsonEncodedString}]"""
        //encode single element with object endpoint and no custom properties as list
        assertEquals(
            expected = svcBlockWithObjectEndpointNoCustomListJsonEncodedString,
            actual = Json.encodeToString(listOf(svcBlockWithObjectEndpointNoCustom)),
        )
        //decode single element with object endpoint and no custom properties as list
        assertEquals(
            expected = listOf(svcBlockWithObjectEndpointNoCustom),
            actual = Json.decodeFromString(svcBlockWithObjectEndpointNoCustomListJsonEncodedString),
        )
        //with object endpoint block and custom properties
        val svcBlockWithObjectEndpointCustom = ServiceBlock(
            id = "some-id",
            type = ServiceType.DIDCommMessaging,
            serviceEndpoint = ServiceEndpointObject(
                buildJsonObject {
                    put("some-url-property", "url-value".toJsonElement())
                    put("some-additional-property", "some-value".toJsonElement())
                }
            ),
            customProperties = customProperties,
        )
        val svcBlockWithObjectEndpointCustomJsonEncodedString =
            """{"id":"some-id","type":"DIDCommMessaging","serviceEndpoint":{"some-url-property":"url-value","some-additional-property":"some-value"},"ckey1":"cvalue1","ckey2":"cvalue2"}"""
        //encode single element with object endpoint and custom properties
        assertEquals(
            expected = svcBlockWithObjectEndpointCustomJsonEncodedString,
            actual = Json.encodeToString(svcBlockWithObjectEndpointCustom),
        )
        //decode single element with object endpoint and custom properties
        assertEquals(
            expected = svcBlockWithObjectEndpointCustom,
            actual = Json.decodeFromString(svcBlockWithObjectEndpointCustomJsonEncodedString),
        )
        val svcBlockWithObjectEndpointCustomListJsonEncodedString =
            """[${svcBlockWithObjectEndpointCustomJsonEncodedString}]"""
        //encode single element with object endpoint and custom properties as list
        assertEquals(
            expected = svcBlockWithObjectEndpointCustomListJsonEncodedString,
            actual = Json.encodeToString(listOf(svcBlockWithObjectEndpointCustom)),
        )
        //decode single element with object endpoint and custom properties as list
        assertEquals(
            expected = listOf(svcBlockWithObjectEndpointCustom),
            actual = Json.decodeFromString(svcBlockWithObjectEndpointCustomListJsonEncodedString),
        )
    }
}