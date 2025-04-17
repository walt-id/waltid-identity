@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.mdoc.deviceengagement

import cbor.Cbor
import id.walt.mdoc.dataelement.DataElement
import id.walt.mdoc.dataelement.MapKey
import id.walt.mdoc.dataelement.toDataElement
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.decodeFromHexString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class CapabilitiesTest {

    @Test
    fun testHappyPathSerialize() {
        val payloads = listOf(
            Capabilities(),
            Capabilities(
                handoverSessionEstablishmentSupport = true,
            ),
            Capabilities(
                readerAuthAllSupport = true,
            ),
            Capabilities(
                handoverSessionEstablishmentSupport = true,
                readerAuthAllSupport = true,
                optional = mapOf(
                    4 to 5.toDataElement(),
                ),
            ),
            Capabilities(
                handoverSessionEstablishmentSupport = true,
                readerAuthAllSupport = true,
                optional = mapOf(
                    5 to "asdfasdf".toDataElement(),
                    4 to 5.toDataElement(),
                ),
            )
        )

        payloads.forEach { payload ->

            assertEquals(
                expected = payload,
                actual = Capabilities.fromCBOR(payload.toCBOR()),
            )

            assertEquals(
                expected = payload,
                actual = Capabilities.fromCBORHex(payload.toCBORHex()),
            )

            assertEquals(
                expected = payload,
                actual = Capabilities.fromMapElement(payload.toMapElement()),
            )

            assertEquals(
                expected = payload,
                actual = Cbor.decodeFromByteArray<Capabilities>(payload.toCBOR()),
            )

            assertEquals(
                expected = payload,
                actual = Cbor.decodeFromHexString<Capabilities>(payload.toCBORHex()),
            )
        }
    }

    @Test
    fun testInvalidPayloads() {
        assertFails {

            Capabilities(
                handoverSessionEstablishmentSupport = true,
                readerAuthAllSupport = true,
                optional = mapOf(
                    3 to "asdfasdf".toDataElement(),
                ),
            )

        }

        assertFails {

            Capabilities(
                handoverSessionEstablishmentSupport = true,
                readerAuthAllSupport = true,
                optional = mapOf(
                    2 to "asdfasdf".toDataElement(),
                ),
            )

        }

        assertFails {

            Capabilities(
                handoverSessionEstablishmentSupport = false,
            )

            Capabilities(
                readerAuthAllSupport = false,
            )

        }

        val payloads = listOf<DataElement>(
            mapOf(
                MapKey(2) to "asdfasdf".toDataElement()
            ).toDataElement(),
            mapOf(
                MapKey(3) to "asdfasdf".toDataElement()
            ).toDataElement(),
            mapOf(
                MapKey(3) to 4.toDataElement()
            ).toDataElement(),
            mapOf(
                MapKey(3) to 4u.toDataElement()
            ).toDataElement(),
            mapOf(
                MapKey(3) to 4f.toDataElement()
            ).toDataElement(),
            mapOf(
                MapKey(3) to ByteArray(5).toDataElement()
            ).toDataElement(),
        )

        payloads.forEach { payload ->

            assertFails {
                Capabilities.fromCBOR(payload.toCBOR())
            }

            assertFails {
                Capabilities.fromCBORHex(payload.toCBORHex())
            }

            assertFails {
                Cbor.decodeFromByteArray<Capabilities>(payload.toCBOR())
            }

            assertFails {
                Cbor.decodeFromHexString<Capabilities>(payload.toCBORHex())
            }
        }
    }
}