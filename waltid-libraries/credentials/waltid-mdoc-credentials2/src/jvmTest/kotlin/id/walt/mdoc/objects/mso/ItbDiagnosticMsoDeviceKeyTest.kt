@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.mdoc.objects.mso

import id.walt.cose.*
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.CborLabel
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ItbDiagnosticMsoDeviceKeyTest {
    @Serializable
    private data class TextKidKey(@CborLabel(1) val kty: Int, @CborLabel(2) val kid: String)

    @Serializable
    private data class WrongKidKey(@CborLabel(1) val kty: Int, @CborLabel(2) val kid: Int)

    @Serializable
    private data class TextKidInfo(@SerialName("deviceKey") val deviceKey: TextKidKey)

    @Serializable
    private data class WrongKidInfo(@SerialName("deviceKey") val deviceKey: WrongKidKey)

    @Serializable
    private data class BadCoordinateKey(
        @CborLabel(1) val kty: Int,
        @CborLabel(2) val kid: String,
        @CborLabel(-2) val x: String,
    )

    @Serializable
    private data class BadCoordinateInfo(@SerialName("deviceKey") val deviceKey: BadCoordinateKey)

    @Test
    fun diagnosticTextKidIsLimitedToMsoDeviceKeyAndKeepsSignedBytes() = runTest {
        val signer = CryptoRuntime(defaultSoftwareKeyProviders()).generateSoftwareKey(GenerateSoftwareKeyRequest(
            id = KeyId("diagnostic-synthetic-issuer"), spec = KeySpec.Ec(EcCurve.P256),
            usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
        ))
        val malformedKey = coseCompliantCbor.encodeToByteArray(TextKidKey(kty = 2, kid = "diagnostic"))
        assertFails { coseCompliantCbor.decodeFromByteArray<CoseKey>(malformedKey) }

        fun wrap(bytes: ByteArray) = byteArrayOf(0xd8.toByte(), 0x18) + coseCompliantCbor.encodeToByteArray(bytes)
        val original = CoseSign1.createAndSign(CoseHeaders(algorithm = Cose.Algorithm.ES256),
            payload = wrap(coseCompliantCbor.encodeToByteArray(TextKidInfo(TextKidKey(2, "diagnostic")))),
            key = signer)
        val received = coseCompliantCbor.decodeFromByteArray<CoseSign1>(coseCompliantCbor.encodeToByteArray(original))
        val interpreted = received.decodeIsoPayload<DeviceKeyInfo>()
        assertContentEquals("diagnostic".encodeToByteArray(), interpreted.deviceKey.kid)
        assertContentEquals(original.payload, received.payload)
        assertContentEquals(original.signature, received.signature)
        assertContentEquals(original.protected, received.protected)
        assertTrue(received.verify(signer, Cose.Algorithm.ES256))
        val rewritten = received.copy(payload = wrap(coseCompliantCbor.encodeToByteArray(interpreted)))
        assertFalse(rewritten.verify(signer, Cose.Algorithm.ES256))
    }

    @Test
    fun compliantByteKidStillWorksAndOtherKidTypesStillFail() {
        val compliant = DeviceKeyInfo(CoseKey(kty = 2, kid = byteArrayOf(1, 2, 3)))
        val decoded = coseCompliantCbor.decodeFromByteArray<DeviceKeyInfo>(coseCompliantCbor.encodeToByteArray(compliant))
        assertContentEquals(compliant.deviceKey.kid, decoded.deviceKey.kid)
        val malformed = coseCompliantCbor.encodeToByteArray(WrongKidInfo(WrongKidKey(2, 42)))
        assertFails { coseCompliantCbor.decodeFromByteArray<DeviceKeyInfo>(malformed) }
        val emptyText = coseCompliantCbor.encodeToByteArray(TextKidInfo(TextKidKey(2, "")))
        assertFails { coseCompliantCbor.decodeFromByteArray<DeviceKeyInfo>(emptyText) }
        val badCoordinate = coseCompliantCbor.encodeToByteArray(
            BadCoordinateInfo(BadCoordinateKey(2, "diagnostic", "not a byte string")))
        assertFails { coseCompliantCbor.decodeFromByteArray<DeviceKeyInfo>(badCoordinate) }
    }
}
