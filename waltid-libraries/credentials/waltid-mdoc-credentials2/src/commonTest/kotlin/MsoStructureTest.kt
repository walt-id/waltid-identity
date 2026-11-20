import id.walt.cose.Cose
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
