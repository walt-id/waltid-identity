package id.walt.did.serialize.service

import id.walt.crypto.utils.JsonUtils.toJsonElement
import id.walt.did.dids.document.models.service.ServiceBlock
import id.walt.did.dids.document.models.service.ServiceEndpointObject
import id.walt.did.dids.document.models.service.ServiceEndpointURL
import id.walt.did.dids.document.models.service.RegisteredServiceType
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
            type = RegisteredServiceType.DIDCommMessaging,
            serviceEndpoint = setOf(ServiceEndpointURL("some-url"))
        )
        val svcBlockWithURLEndpointNoCustomJsonEncodedString =
            """{"id":"some-id","type":"DIDCommMessaging","serviceEndpoint":"some-url"}"""
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
        val svcBlockWithURLEndpointNoCustomListJsonEncodedString =
            """[${svcBlockWithURLEndpointNoCustomJsonEncodedString}]"""
        //encode single element with URL endpoint and no custom properties as list
        assertEquals(
            expected = svcBlockWithURLEndpointNoCustomListJsonEncodedString,
            actual = Json.encodeToString(setOf(svcBlockWithURLEndpointNoCustom))
        )
        //decode single element with URL endpoint and no custom properties as list
        assertEquals(
            expected = setOf(svcBlockWithURLEndpointNoCustom),
            actual = Json.decodeFromString(svcBlockWithURLEndpointNoCustomListJsonEncodedString)
        )
        //with URL endpoint block and custom properties
        val svcBlockWithURLEndpointCustom = ServiceBlock(
            id = "some-id",
            type = RegisteredServiceType.DIDCommMessaging,
            serviceEndpoint = setOf(ServiceEndpointURL("some-url")),
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
        val svcBlockWithURLEndpointCustomListJsonEncodedString =
            """[${svcBlockWithURLEndpointCustomJsonEncodedString}]"""
        //encode single element with URL endpoint and custom properties as list
        assertEquals(
            expected = svcBlockWithURLEndpointCustomListJsonEncodedString,
            actual = Json.encodeToString(setOf(svcBlockWithURLEndpointCustom))
        )
        //decode single element with URL endpoint and custom properties as list
        assertEquals(
            expected = setOf(svcBlockWithURLEndpointCustom),
            actual = Json.decodeFromString(svcBlockWithURLEndpointCustomListJsonEncodedString)
        )
    }

    @Test
    fun testServiceBlockEndpointObjectSerialization() = runTest {
        //with object endpoint block and no custom properties
        val svcBlockWithObjectEndpointNoCustom = ServiceBlock(
            id = "some-id",
            type = RegisteredServiceType.DIDCommMessaging,
            serviceEndpoint = setOf(
                ServiceEndpointObject(
                    buildJsonObject {
                        put("some-url-property", "url-value".toJsonElement())
                        put("some-additional-property", "some-value".toJsonElement())
                    }
                ),
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
        val svcBlockWithObjectEndpointNoCustomListJsonEncodedString =
            """[${svcBlockWithObjectEndpointNoCustomJsonEncodedString}]"""
        //encode single element with object endpoint and no custom properties as list
        assertEquals(
            expected = svcBlockWithObjectEndpointNoCustomListJsonEncodedString,
            actual = Json.encodeToString(setOf(svcBlockWithObjectEndpointNoCustom)),
        )
        //decode single element with object endpoint and no custom properties as list
        assertEquals(
            expected = setOf(svcBlockWithObjectEndpointNoCustom),
            actual = Json.decodeFromString(svcBlockWithObjectEndpointNoCustomListJsonEncodedString),
        )
        //with object endpoint block and custom properties
        val svcBlockWithObjectEndpointCustom = ServiceBlock(
            id = "some-id",
            type = RegisteredServiceType.DIDCommMessaging,
            serviceEndpoint = setOf(
                ServiceEndpointObject(
                    buildJsonObject {
                        put("some-url-property", "url-value".toJsonElement())
                        put("some-additional-property", "some-value".toJsonElement())
                    }
                ),
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
            actual = Json.encodeToString(setOf(svcBlockWithObjectEndpointCustom)),
        )
        //decode single element with object endpoint and custom properties as list
        assertEquals(
            expected = setOf(svcBlockWithObjectEndpointCustom),
            actual = Json.decodeFromString(svcBlockWithObjectEndpointCustomListJsonEncodedString),
        )
    }

    @Test
    fun testServiceBlockWithSameEndpoints() = runTest {
        val svcBlockWithURLEndpoints = ServiceBlock(
            id = "some-id",
            type = RegisteredServiceType.DIDCommMessaging,
            serviceEndpoint = setOf(
                ServiceEndpointURL("some-url"),
                ServiceEndpointURL("some-url"),
            )
        )
        assertTrue(svcBlockWithURLEndpoints.serviceEndpoint.size == 1)
        val svcBlockWithURLEndpointsJsonEncodedString =
            """{"id":"some-id","type":"DIDCommMessaging","serviceEndpoint":"some-url"}"""
        //encode
        assertEquals(
            expected = svcBlockWithURLEndpointsJsonEncodedString,
            actual = Json.encodeToString(svcBlockWithURLEndpoints)
        )
        //decode
        assertEquals(
            expected = svcBlockWithURLEndpoints,
            actual = Json.decodeFromString(svcBlockWithURLEndpointsJsonEncodedString)
        )
        val svcBlockWithObjectEndpoints = ServiceBlock(
            id = "some-id",
            type = RegisteredServiceType.DIDCommMessaging,
            serviceEndpoint = setOf(
                ServiceEndpointObject(
                    buildJsonObject {
                        put("some-url-property", "url-value".toJsonElement())
                        put("some-additional-property", "some-value".toJsonElement())
                    }
                ),
                ServiceEndpointObject(
                    buildJsonObject {
                        put("some-url-property", "url-value".toJsonElement())
                        put("some-additional-property", "some-value".toJsonElement())
                    }
                ),
            ),
        )
        assertTrue(svcBlockWithObjectEndpoints.serviceEndpoint.size == 1)
        val svcBlockWithObjectEndpointsJsonEncodedString =
            """{"id":"some-id","type":"DIDCommMessaging","serviceEndpoint":{"some-url-property":"url-value","some-additional-property":"some-value"}}"""
        //encode
        assertEquals(
            expected = svcBlockWithObjectEndpointsJsonEncodedString,
            actual = Json.encodeToString(svcBlockWithObjectEndpoints)
        )
        //decode
        assertEquals(
            expected = svcBlockWithObjectEndpoints,
            actual = Json.decodeFromString(svcBlockWithObjectEndpointsJsonEncodedString)
        )
    }

    @Test
    fun testServiceBlockWithMixEndpoints() = runTest {
        val svcBlock = ServiceBlock(
            id = "some-id",
            type = RegisteredServiceType.DIDCommMessaging,
            serviceEndpoint = setOf(
                ServiceEndpointURL("some-url"),
                ServiceEndpointObject(
                    buildJsonObject {
                        put("some-url-property", "url-value".toJsonElement())
                        put("some-additional-property", "some-value".toJsonElement())
                    }
                ),
            )
        )
        assertTrue(svcBlock.serviceEndpoint.size == 2)
        val svcBlockJsonEncodedString =
            """{"id":"some-id","type":"DIDCommMessaging","serviceEndpoint":["some-url",{"some-url-property":"url-value","some-additional-property":"some-value"}]}"""
        //encode
        assertEquals(
            expected = svcBlockJsonEncodedString,
            actual = Json.encodeToString(svcBlock)
        )
        //decode
        assertEquals(
            expected = svcBlock,
            actual = Json.decodeFromString(svcBlockJsonEncodedString)
        )
    }
}