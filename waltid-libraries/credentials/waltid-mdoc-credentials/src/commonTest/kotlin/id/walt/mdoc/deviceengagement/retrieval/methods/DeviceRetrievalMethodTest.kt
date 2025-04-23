@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.mdoc.deviceengagement.retrieval.methods

import cbor.Cbor
import id.walt.mdoc.deviceengagement.retrieval.methods.device.BleDeviceRetrieval
import id.walt.mdoc.deviceengagement.retrieval.methods.device.DeviceRetrievalMethod
import id.walt.mdoc.deviceengagement.retrieval.methods.device.NfcDeviceRetrieval
import id.walt.mdoc.deviceengagement.retrieval.methods.device.WifiDeviceRetrieval
import id.walt.mdoc.deviceengagement.retrieval.options.BleOptions
import id.walt.mdoc.deviceengagement.retrieval.options.NfcOptions
import id.walt.mdoc.deviceengagement.retrieval.options.WifiOptions
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.decodeFromHexString
import kotlin.test.Test
import kotlin.test.assertEquals

class DeviceRetrievalMethodTest {

    @Test
    fun testHappyPathSerialize() {
        val payloads = listOf(
            WifiDeviceRetrieval(
                retrievalOptions = WifiOptions(
                    bandInfoSupportedBands = ByteArray(5),
                ),
            ),
            NfcDeviceRetrieval(
                retrievalOptions = NfcOptions(
                    commandDataFieldMaxLength = 5u,
                    responseDataFieldMaxLength = 15u,
                )
            ),
            BleDeviceRetrieval(
                retrievalOptions = BleOptions(
                    supportMDocPeripheralServerMode = true,
                    supportMDocCentralClientMode = false,
                ),
            ),
        )

        payloads.forEach { payload ->

            assertEquals(
                expected = payload,
                actual = DeviceRetrievalMethod.fromCBOR(payload.toCBOR()),
            )

            assertEquals(
                expected = payload,
                actual = DeviceRetrievalMethod.fromCBORHex(payload.toCBORHex()),
            )

            assertEquals(
                expected = payload,
                actual = DeviceRetrievalMethod.fromListElement(payload.toListElement())
            )

            assertEquals(
                expected = payload,
                actual = Cbor.decodeFromByteArray<DeviceRetrievalMethod>(payload.toCBOR()),
            )

            assertEquals(
                expected = payload,
                actual = Cbor.decodeFromHexString<DeviceRetrievalMethod>(payload.toCBORHex()),
            )

        }
    }

    @Test
    fun testWifiDeviceRetrievalHappyPathSerialize() {
        val payloads = listOf(
            WifiDeviceRetrieval(
                retrievalOptions = WifiOptions(
                    bandInfoSupportedBands = ByteArray(5),
                ),
            ),

            WifiDeviceRetrieval(
                version = 5U,
                retrievalOptions = WifiOptions(
                    bandInfoSupportedBands = ByteArray(11),
                ),
            ),
        )

        payloads.forEach { payload ->

            assertEquals(
                expected = payload,
                actual = WifiDeviceRetrieval.fromCBOR(payload.toCBOR()),
            )

            assertEquals(
                expected = payload,
                actual = WifiDeviceRetrieval.fromCBORHex(payload.toCBORHex()),
            )

            assertEquals(
                expected = payload,
                actual = WifiDeviceRetrieval.fromListElement(payload.toListElement())
            )

            assertEquals(
                expected = payload,
                actual = Cbor.decodeFromByteArray<WifiDeviceRetrieval>(payload.toCBOR()),
            )

            assertEquals(
                expected = payload,
                actual = Cbor.decodeFromHexString<WifiDeviceRetrieval>(payload.toCBORHex()),
            )

        }
    }

    @Test
    fun testBleDeviceRetrievalHappyPathSerialize() {
        val payloads = listOf(
            BleDeviceRetrieval(
                retrievalOptions = BleOptions(
                    supportMDocPeripheralServerMode = true,
                    supportMDocCentralClientMode = false,
                ),
            ),
            BleDeviceRetrieval(
                retrievalOptions = BleOptions(
                    supportMDocPeripheralServerMode = true,
                    supportMDocCentralClientMode = false,
                    mdocPeripheralServerModeUUID = ByteArray(5),
                    mdocPeripheralServerModeL2CAPPSM = 6U,
                ),
            ),
            BleDeviceRetrieval(
                retrievalOptions = BleOptions(
                    supportMDocPeripheralServerMode = false,
                    supportMDocCentralClientMode = true,
                    mdocPeripheralServerModeL2CAPPSM = 9U,
                    mdocPeripheralServerModeUUID = ByteArray(11),
                    mdocPeripheralServerModeDeviceAddress = ByteArray(50),
                ),
            ),
        )

        payloads.forEach { payload ->

            assertEquals(
                expected = payload,
                actual = BleDeviceRetrieval.fromCBOR(payload.toCBOR()),
            )

            assertEquals(
                expected = payload,
                actual = BleDeviceRetrieval.fromCBORHex(payload.toCBORHex()),
            )

            assertEquals(
                expected = payload,
                actual = BleDeviceRetrieval.fromListElement(payload.toListElement())
            )

            assertEquals(
                expected = payload,
                actual = Cbor.decodeFromByteArray<BleDeviceRetrieval>(payload.toCBOR()),
            )

            assertEquals(
                expected = payload,
                actual = Cbor.decodeFromHexString<BleDeviceRetrieval>(payload.toCBORHex()),
            )

        }
    }

    @Test
    fun testNfcDeviceRetrievalHappyPathSerialize() {
        val payloads = listOf(
            NfcDeviceRetrieval(
                retrievalOptions = NfcOptions(
                    commandDataFieldMaxLength = 5u,
                    responseDataFieldMaxLength = 15u,
                )
            ),
        )

        payloads.forEach { payload ->

            assertEquals(
                expected = payload,
                actual = NfcDeviceRetrieval.fromCBOR(payload.toCBOR()),
            )

            assertEquals(
                expected = payload,
                actual = NfcDeviceRetrieval.fromCBORHex(payload.toCBORHex()),
            )

            assertEquals(
                expected = payload,
                actual = NfcDeviceRetrieval.fromListElement(payload.toListElement())
            )

            assertEquals(
                expected = payload,
                actual = Cbor.decodeFromByteArray<NfcDeviceRetrieval>(payload.toCBOR()),
            )

            assertEquals(
                expected = payload,
                actual = Cbor.decodeFromHexString<NfcDeviceRetrieval>(payload.toCBORHex()),
            )

        }
    }
}