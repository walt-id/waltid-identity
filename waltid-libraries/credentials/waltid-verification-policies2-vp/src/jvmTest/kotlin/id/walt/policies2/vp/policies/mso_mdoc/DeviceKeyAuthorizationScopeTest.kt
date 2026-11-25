package id.walt.policies2.vp.policies

import id.walt.cose.Cose
import id.walt.cose.CoseKey
import id.walt.cose.CoseHeaders
import id.walt.cose.CoseSign1
import id.walt.credentials.examples.MdocsExamples
import id.walt.mdoc.objects.DeviceSigned
import id.walt.mdoc.objects.elements.DeviceSignedItem
import id.walt.mdoc.objects.mso.DeviceKeyInfo
import id.walt.mdoc.objects.mso.ValidityInfo
import id.walt.mdoc.objects.mso.MobileSecurityObject
import id.walt.mdoc.objects.mso.KeyAuthorization
import id.walt.mdoc.parser.MdocParser
import kotlinx.coroutines.test.runTest
import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeviceKeyAuthorizationScopeTest {
    private val document = MdocParser.parseToDocument(MdocsExamples.mdocExampleHex)
    private val time = Instant.parse("2026-01-01T00:00:00Z")
    private val mso = MobileSecurityObject(
        version = "1.0", digestAlgorithm = "SHA-256", valueDigests = emptyMap(),
        deviceKeyInfo = DeviceKeyInfo(CoseKey(kty = Cose.KeyTypes.OKP)),
        docType = document.docType, validityInfo = ValidityInfo(time, time, time),
    )
    private val namespace = "org.iso.18013.5.1"

    private suspend fun verify(authorization: KeyAuthorization?, deviceElements: List<DeviceSignedItem>?) =
        DeviceKeyAuthMdocVpPolicy().runPolicy(
            document.copy(deviceSigned = deviceElements?.let {
                DeviceSigned.fromDeviceSignedItems(
                    mapOf(namespace to it),
                    CoseSign1(byteArrayOf(), CoseHeaders(), null, byteArrayOf()),
                )
            }),
            mso.copy(deviceKeyInfo = mso.deviceKeyInfo.copy(keyAuthorizations = authorization)),
            null,
        ).success

    @Test
    fun `device authorizations do not restrict issuer signed data`() = runTest {
        assertTrue(verify(KeyAuthorization(namespaces = listOf("another.namespace")), null))
    }

    @Test
    fun `unauthorized device signed data is rejected`() = runTest {
        assertFalse(verify(KeyAuthorization(namespaces = listOf("another.namespace")), listOf(DeviceSignedItem("age_over_18", true))))
    }

    @Test
    fun `whole namespace authorization permits device signed data`() = runTest {
        assertTrue(verify(KeyAuthorization(namespaces = listOf(namespace)), listOf(DeviceSignedItem("age_over_18", true))))
    }

    @Test
    fun `element authorization permits only listed device signed elements`() = runTest {
        val authorization = KeyAuthorization(dataElements = mapOf(namespace to listOf("age_over_18")))
        assertTrue(verify(authorization, listOf(DeviceSignedItem("age_over_18", true))))
        assertFalse(verify(authorization, listOf(DeviceSignedItem("family_name", "Example"))))
    }

    @Test
    fun `device signed data requires authorization`() = runTest {
        assertFalse(verify(null, listOf(DeviceSignedItem("age_over_18", true))))
    }
}
