package id.walt.mdoc.proximity.mobile

import id.walt.mdoc.proximity.ImmutableBytes
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WifiAwareProtocolTest {
    @Test
    fun `ISO service name and passphrase derivations match independent HKDF vectors`() {
        val eDeviceKeyBytes = ImmutableBytes.of("test EDeviceKeyBytes".encodeToByteArray())

        assertEquals("8EB78A0CF910EA14B6DBA7C3D6BBD75D", WifiAwareProtocol.deriveServiceName(eDeviceKeyBytes))
        assertEquals("RQDMjYgxvA0mRB85Jtk37IGeD6P_7cGUCvSVMMSdUEU", WifiAwareProtocol.derivePassphrase(eDeviceKeyBytes))
    }

    @Test
    fun `passphrase and supported-band values reject unusable states`() {
        assertEquals("12345678", WifiAwareProtocol.requireValidPassphrase("12345678"))
        assertFailsWith<IllegalArgumentException> { WifiAwareProtocol.requireValidPassphrase("short") }
        assertFailsWith<IllegalArgumentException> { WifiAwareProtocol.requireValidPassphrase("1234567\n") }
        assertFailsWith<IllegalArgumentException> { WifiAwareSupportedBands.fromBytes(ByteArray(0)) }
        assertFailsWith<IllegalArgumentException> { WifiAwareSupportedBands.fromBytes(byteArrayOf(0)) }
    }

    @Test
    fun `supported-band intersection keeps only mutually supported bands`() {
        val holder = WifiAwareSupportedBands.fromBytes(byteArrayOf(0x14))
        val reader = WifiAwareSupportedBands.fromBytes(byteArrayOf(0x04))

        assertContentEquals(byteArrayOf(0x04), holder.intersect(reader).encoded())
        assertFailsWith<IllegalArgumentException> {
            holder.intersect(WifiAwareSupportedBands.fromBytes(byteArrayOf(0x20)))
        }
    }
}
