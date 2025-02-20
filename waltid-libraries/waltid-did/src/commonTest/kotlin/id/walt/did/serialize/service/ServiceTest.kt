package id.walt.did.serialize.service

import id.walt.crypto.utils.JsonUtils.toJsonElement
import id.walt.did.dids.document.models.service.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

class ServiceTest {

    private val customProperties = mapOf(
        "this" to "that".toJsonElement(),
        "tit" to "tat".toJsonElement(),
    )

    private val svcMapSingleURLNoCustom = ServiceMap(
        id = "some-id",
        type = setOf(RegisteredServiceType.DIDCommMessaging.toString()),
        serviceEndpoint = setOf(ServiceEndpointURL("some-url")),
    )

    private val svcMapSingleURLCustom = ServiceMap(
        id = "some-id",
        type = setOf(RegisteredServiceType.DIDCommMessaging.toString()),
        serviceEndpoint = setOf(ServiceEndpointURL("some-url")),
        customProperties = customProperties,
    )

    private val svcMapMultipleURLNoCustom = ServiceMap(
        id = "some-id",
        type = setOf(RegisteredServiceType.DIDCommMessaging.toString()),
        serviceEndpoint = setOf(
            ServiceEndpointURL("some-url1"),
            ServiceEndpointURL("some-url2"),
        ),
    )

    private val svcMapMultipleURLCustom = ServiceMap(
        id = "some-id",
        type = setOf(RegisteredServiceType.DIDCommMessaging.toString()),
        serviceEndpoint = setOf(
            ServiceEndpointURL("some-url1"),
            ServiceEndpointURL("some-url2"),
        ),
        customProperties = customProperties,
    )

    private val svcMapSingleObjectNoCustom = ServiceMap(
        id = "some-id",
        type = setOf(RegisteredServiceType.DIDCommMessaging.toString()),
        serviceEndpoint = setOf(
            ServiceEndpointObject(
                buildJsonObject {
                    put("some-url-property1", "url-value1".toJsonElement())
                    put("some-additional-property1", "some-value1".toJsonElement())
                }
            ),
        ),
    )

    private val svcMapSingleObjectCustom = ServiceMap(
        id = "some-id",
        type = setOf(RegisteredServiceType.DIDCommMessaging.toString()),
        serviceEndpoint = setOf(
            ServiceEndpointObject(
                buildJsonObject {
                    put("some-url-property1", "url-value1".toJsonElement())
                    put("some-additional-property1", "some-value1".toJsonElement())
                }
            ),
        ),
        customProperties = customProperties,
    )

    private val svcMapMultipleObjectNoCustom = ServiceMap(
        id = "some-id",
        type = setOf(RegisteredServiceType.DIDCommMessaging.toString()),
        serviceEndpoint = setOf(
            ServiceEndpointObject(
                buildJsonObject {
                    put("some-url-property1", "url-value1".toJsonElement())
                    put("some-additional-property1", "some-value1".toJsonElement())
                }
            ),
            ServiceEndpointObject(
                buildJsonObject {
                    put("some-url-property2", "url-value2".toJsonElement())
                    put("some-additional-property2", "some-value2".toJsonElement())
                }
            ),
        ),
    )

    private val svcMapMultipleObjectCustom = ServiceMap(
        id = "some-id",
        type = setOf(RegisteredServiceType.DIDCommMessaging.toString()),
        serviceEndpoint = setOf(
            ServiceEndpointObject(
                buildJsonObject {
                    put("some-url-property1", "url-value1".toJsonElement())
                    put("some-additional-property1", "some-value1".toJsonElement())
                }
            ),
            ServiceEndpointObject(
                buildJsonObject {
                    put("some-url-property2", "url-value2".toJsonElement())
                    put("some-additional-property2", "some-value2".toJsonElement())
                }
            ),
        ),
        customProperties = customProperties,
    )

    private val svcMapSet = setOf(
        svcMapSingleURLNoCustom,
        svcMapSingleURLCustom,
        svcMapMultipleURLNoCustom,
        svcMapMultipleURLCustom,
        svcMapSingleObjectNoCustom,
        svcMapSingleObjectCustom,
        svcMapMultipleObjectNoCustom,
        svcMapMultipleObjectCustom,
    )

    @Test
    fun testSingleServiceSerialization() {
        svcMapSet.forEach {
            val svc = Service(setOf(it))
            val svcJsonString = """[${Json.encodeToString(it)}]"""
            assertEquals(
                expected = svcJsonString,
                actual = Json.encodeToString(svc)
            )
            assertEquals(
                expected = svc,
                actual = Json.decodeFromString(svcJsonString)
            )
        }
    }

    @Test
    fun testMultiServiceSerialization() {
        val svc = Service(svcMapSet)
        val svcJsonString =
            """[{"id":"some-id","type":"DIDCommMessaging","serviceEndpoint":"some-url"},{"id":"some-id","type":"DIDCommMessaging","serviceEndpoint":"some-url","this":"that","tit":"tat"},{"id":"some-id","type":"DIDCommMessaging","serviceEndpoint":["some-url1","some-url2"]},{"id":"some-id","type":"DIDCommMessaging","serviceEndpoint":["some-url1","some-url2"],"this":"that","tit":"tat"},{"id":"some-id","type":"DIDCommMessaging","serviceEndpoint":{"some-url-property1":"url-value1","some-additional-property1":"some-value1"}},{"id":"some-id","type":"DIDCommMessaging","serviceEndpoint":{"some-url-property1":"url-value1","some-additional-property1":"some-value1"},"this":"that","tit":"tat"},{"id":"some-id","type":"DIDCommMessaging","serviceEndpoint":[{"some-url-property1":"url-value1","some-additional-property1":"some-value1"},{"some-url-property2":"url-value2","some-additional-property2":"some-value2"}]},{"id":"some-id","type":"DIDCommMessaging","serviceEndpoint":[{"some-url-property1":"url-value1","some-additional-property1":"some-value1"},{"some-url-property2":"url-value2","some-additional-property2":"some-value2"}],"this":"that","tit":"tat"}]"""
        assertEquals(
            expected = svcJsonString,
            actual = Json.encodeToString(svc),
        )
        assertEquals(
            expected = svc,
            actual = Json.decodeFromString(svcJsonString)
        )
    }
}
