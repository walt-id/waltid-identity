@file:OptIn(ExperimentalSerializationApi::class, ExperimentalUnsignedTypes::class)

import id.walt.cose.CoseHeaders
import id.walt.cose.CoseSign1
import id.walt.cose.coseCompliantCbor
import id.walt.crypto.utils.JsonUtils.toSerializedJsonElement
import id.walt.mdoc.credsdata.CredentialManager
import id.walt.mdoc.objects.MdocsCborSerializer
import id.walt.mdoc.objects.document.IssuerSigned
import id.walt.mdoc.objects.elements.DeviceNameSpaces
import id.walt.mdoc.objects.elements.DeviceSignedItem
import id.walt.mdoc.objects.elements.DeviceSignedItemList
import id.walt.mdoc.objects.elements.IssuerSignedItem
import kotlinx.datetime.LocalDate
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.cbor.CborArray
import kotlinx.serialization.cbor.CborBoolean
import kotlinx.serialization.cbor.CborByteString
import kotlinx.serialization.cbor.CborElement
import kotlinx.serialization.cbor.CborInteger
import kotlinx.serialization.cbor.CborMap
import kotlinx.serialization.cbor.CborNull
import kotlinx.serialization.cbor.CborString
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class NamespaceJsonConversionTest {
    private val namespace = "org.iso.18013.5.1"

    @Test
    fun `wire decoded issuer and device booleans retain JSON types`() {
        CredentialManager.init()
        val values = mapOf("age_over_18" to CborBoolean(true), "age_over_21" to CborBoolean(false))
        val decodedIssuer = coseCompliantCbor.decodeFromByteArray<IssuerSigned>(
            coseCompliantCbor.encodeToByteArray(issuer(values)),
        )
        val decodedDevice = coseCompliantCbor.decodeFromByteArray<DeviceNameSpaces>(
            coseCompliantCbor.encodeToByteArray(device(values)),
        )
        val expected = values.mapValues { it.value.toSerializedJsonElement() }
        assertEquals(expected, decodedIssuer.namespacesToJson().getValue(namespace).jsonObject)
        assertEquals(expected, decodedDevice.namespacesToJson().getValue(namespace).jsonObject)
    }

    @Test
    fun `issuer and device CBOR values convert without serializer exceptions on stdout`() {
        CredentialManager.init()
        val values = mapOf(
            "age_over_18" to CborBoolean(true),
            "age_over_21" to CborBoolean(false),
            "family_name" to CborString("Doe"),
            "height" to CborInteger(180),
            "portrait" to CborByteString(byteArrayOf(1, 2, 3)),
            "unknown_nested" to CborMap(mapOf(CborString("flags") to CborArray(listOf(CborBoolean(true), CborInteger(1))))),
            "missing" to CborNull(),
        )
        val output = ByteArrayOutputStream()
        val previous = System.out
        try {
            System.setOut(PrintStream(output))
            val issuer = issuer(values).namespacesToJson().getValue(namespace).jsonObject
            val device = device(values).namespacesToJson().getValue(namespace).jsonObject
            for ((key, value) in values.filterKeys { it != "missing" }) {
                assertEquals(value.toSerializedJsonElement(), issuer[key], key)
                assertEquals(value.toSerializedJsonElement(), device[key], key)
            }
            assertFalse("missing" in issuer)
            assertEquals(JsonNull, device["missing"])
        } finally { System.setOut(previous) }
        assertEquals("", output.toString())
    }

    @Test
    fun `device Kotlin values retain typed serializers and generic fallback`() {
        val customNamespace = "test.namespace-json-conversion"
        MdocsCborSerializer.register(mapOf("date" to LocalDate.serializer(), "mismatch" to Boolean.serializer()), customNamespace)
        val values = mapOf("date" to LocalDate(2026, 11, 12), "mismatch" to "fallback", "unregistered" to true)
        val converted = device(values, customNamespace).namespacesToJson().getValue(customNamespace).jsonObject
        assertEquals(JsonPrimitive("2026-11-12"), converted["date"])
        assertEquals(JsonPrimitive("fallback"), converted["mismatch"])
        assertEquals(JsonPrimitive(true), converted["unregistered"])
    }

    private fun device(values: Map<String, Any>, namespace: String = this.namespace) = DeviceNameSpaces(
        mapOf(namespace to DeviceSignedItemList(values.map { (key, value) -> DeviceSignedItem(key, value) })),
    )

    private fun issuer(values: Map<String, CborElement>) = IssuerSigned.fromIssuerSignedItems(
        mapOf(namespace to values.entries.mapIndexed { index, (key, value) -> IssuerSignedItem.create(index.toUInt(), key, value) }),
        CoseSign1(byteArrayOf(), CoseHeaders(), null, byteArrayOf()),
    )
}
