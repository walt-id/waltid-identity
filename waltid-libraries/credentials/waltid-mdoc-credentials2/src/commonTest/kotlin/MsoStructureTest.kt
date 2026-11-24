@file:OptIn(ExperimentalSerializationApi::class)

import id.walt.cose.Cose
import kotlin.test.assertEquals
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.builtins.ByteArraySerializer
import id.walt.mdoc.objects.wrapInCborTag
import id.walt.mdoc.objects.document.IssuerSigned
import id.walt.cose.coseCompliantCbor
import id.walt.cose.CoseSign1
import id.walt.cose.CoseHeaders
import id.walt.cose.CoseKey
import id.walt.mdoc.objects.digest.ValueDigest
import id.walt.mdoc.objects.digest.ValueDigestList
import id.walt.mdoc.objects.mso.DeviceKeyInfo
import id.walt.mdoc.objects.mso.MobileSecurityObject
import id.walt.mdoc.objects.mso.ValidityInfo
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.time.Instant

class MsoStructureTest {
    private val start = Instant.parse("2026-01-01T00:00:00Z")
    private val mso = MobileSecurityObject(
        version = "1.0",
        digestAlgorithm = "SHA-256",
        valueDigests = mapOf("example" to ValueDigestList(listOf(ValueDigest(1u, ByteArray(32))))),
        deviceKeyInfo = DeviceKeyInfo(CoseKey(kty = Cose.KeyTypes.OKP)),
        docType = "example.document",
        validityInfo = ValidityInfo(start, start, Instant.parse("2027-01-01T00:00:00Z")),
    )

    @Test
    fun supportedMsoPassesStructuralPrecheck() = mso.precheck()

    @Test
    fun compatibleMinorMsoVersionsPass() {
        for (version in listOf("1.1", "1.23")) mso.copy(version = version).precheck()
        for (version in listOf("1", "1.", "1.x", "01.0")) {
            assertFailsWith<IllegalArgumentException> { mso.copy(version = version).precheck() }
        }
    }

    @Test
    fun msoRequiresTag24ByteStringAndUnambiguousInnerMap() {
        val encoded = coseCompliantCbor.encodeToByteArray(mso)
        fun issuer(payload: ByteArray) = IssuerSigned.fromIssuerSignedItems(emptyMap(), CoseSign1(byteArrayOf(), CoseHeaders(), payload, byteArrayOf()))
        fun wrapped(bytes: ByteArray) = coseCompliantCbor.encodeToByteArray(ByteArraySerializer(), bytes).wrapInCborTag(24)
        assertEquals(mso, issuer(wrapped(encoded)).decodeMobileSecurityObject())
        for (invalid in listOf(encoded, coseCompliantCbor.encodeToByteArray(ByteArraySerializer(), encoded), wrapped(byteArrayOf(0x80.toByte())))) {
            assertFailsWith<IllegalArgumentException> { issuer(invalid).decodeMobileSecurityObject() }
        }
        val end = if (encoded[0] == 0xbf.toByte()) encoded.lastIndex else encoded.size
        val duplicate = byteArrayOf(0xbf.toByte()) + encoded.copyOfRange(1, end) +
            "6776657273696f6e63312e30".hexToByteArray() + byteArrayOf(0xff.toByte())
        assertFailsWith<IllegalArgumentException> { issuer(wrapped(duplicate)).decodeMobileSecurityObject() }
    }

    @Test
    fun unknownMsoVersionIsRejected() {
        assertFailsWith<IllegalArgumentException> { mso.copy(version = "9.0").precheck() }
    }

    @Test
    fun duplicateDigestIdsInAnyNamespaceAreRejected() {
        val duplicate = ValueDigest(1u, ByteArray(32))
        assertFailsWith<IllegalArgumentException> {
            mso.copy(valueDigests = mso.valueDigests +
                ("not.disclosed" to ValueDigestList(listOf(duplicate, duplicate)))).precheck()
        }
    }

    @Test
    fun digestIdsCanBeReusedAcrossNamespaces() {
        mso.copy(valueDigests = mso.valueDigests + ("another" to mso.valueDigests.getValue("example"))).precheck()
    }
}
