@file:Suppress("PackageDirectoryMismatch")
@file:OptIn(ExperimentalUnsignedTypes::class, ExperimentalSerializationApi::class)

package id.walt.policies2.vp.policies

import id.walt.cose.Cose
import id.walt.cose.CoseHeaders
import id.walt.cose.CoseKey
import id.walt.cose.CoseSign1
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.mdoc.objects.DeviceSigned
import id.walt.mdoc.objects.document.Document
import id.walt.mdoc.objects.document.IssuerSigned
import id.walt.mdoc.objects.elements.DeviceSignedItem
import id.walt.mdoc.objects.mso.DeviceKeyInfo
import id.walt.mdoc.objects.mso.KeyAuthorization
import id.walt.mdoc.objects.mso.MobileSecurityObject
import id.walt.mdoc.objects.mso.ValidityInfo
import id.walt.verifier.openid.models.openid.OpenID4VPResponseMode
import id.walt.verifier.openid.transactiondata.MDOC_DEVICE_SIGNED_NAMESPACE

import id.walt.verifier.openid.transactiondata.deviceSignedItemKey
import id.walt.verifier.openid.transactiondata.deviceSignedItemKeys
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

class TransactionDataMdocVpPolicyTest {

    private val policy = TransactionDataMdocVpPolicy()
    private val transactionData = listOf(
        """{"type":"org.waltid.transaction-data.payment-authorization","credential_ids":["payment_credential"],"amount":"42.00","currency":"EUR"}"""
        .encodeToByteArray()
        .encodeToBase64Url(),
    )

    @Test
    fun `succeeds when embedded transaction data matches the verifier request`() = runTest {
        val result = policy.runPolicy(
            document = documentWithEmbeddedTransactionData(transactionData),
            mso = dummyMso(),
            verificationContext = dummyVerificationContext(transactionData),
        )

        assertTrue(result.success)
    }

    @Test
    fun `fails when embedded transaction data does not match the verifier request`() = runTest {
        val result = policy.runPolicy(
            document = documentWithEmbeddedTransactionData(
                listOf(
                    """{"type":"org.waltid.transaction-data.payment-authorization","credential_ids":["payment_credential"],"amount":"99.00","currency":"EUR"}"""
                    .encodeToByteArray()
                    .encodeToBase64Url(),
                )
            ),
            mso = dummyMso(),
            verificationContext = dummyVerificationContext(transactionData),
        )

        assertFalse(result.success)
    }

    @Test
    fun `fails when unexpected embedded transaction data is present`() = runTest {
        val result = policy.runPolicy(
            document = documentWithEmbeddedTransactionData(transactionData),
            mso = dummyMso(),
            verificationContext = dummyVerificationContext(null),
        )

        assertFalse(result.success)
    }

    private fun documentWithEmbeddedTransactionData(transactionData: List<String>): Document {
        val embeddedTransactionData = transactionData.mapIndexed { index, encoded ->
            deviceSignedItemKey(index) to encoded
        }.toMap()
        val deviceSigned = DeviceSigned.fromDeviceSignedItems(
            namespacedItems = mapOf(
                MDOC_DEVICE_SIGNED_NAMESPACE to embeddedTransactionData.map { (key, value) ->
                    DeviceSignedItem(key, value)
                }
            ),
            deviceAuth = dummyCoseSign1(),
        )

        return Document(
            docType = "org.iso.18013.5.1.mDL",
            issuerSigned = IssuerSigned.fromIssuerSignedItems(
                namespacedItems = emptyMap(),
                issuerAuth = dummyCoseSign1(),
            ),
            deviceSigned = deviceSigned,
        )
    }

    private fun dummyMso(): MobileSecurityObject {
        val now = Clock.System.now()
        val authorizedElements = deviceSignedItemKeys(transactionData.size).toList()

        return MobileSecurityObject(
            version = "1.0",
            digestAlgorithm = "SHA-256",
            valueDigests = emptyMap(),
            deviceKeyInfo = DeviceKeyInfo(
                deviceKey = CoseKey(
                    kty = Cose.KeyTypes.OKP,
                    crv = Cose.EllipticCurves.Ed25519,
                    x = ByteArray(32) { 0x02 },
                ),
                keyAuthorizations = KeyAuthorization(
                    dataElements = mapOf(
                        MDOC_DEVICE_SIGNED_NAMESPACE to authorizedElements
                    )
                ),
            ),
            docType = "org.iso.18013.5.1.mDL",
            validityInfo = ValidityInfo(
                signed = now,
                validFrom = now,
                validUntil = now + 1.days,
            ),
        )
    }

    private fun dummyVerificationContext(expectedTransactionData: List<String>?) = VerificationSessionContext(
        vpToken = "vp_token",
        expectedNonce = "nonce",
        expectedAudience = null,
        expectedOrigins = null,
        expectedTransactionData = expectedTransactionData,
        responseUri = null,
        responseMode = OpenID4VPResponseMode.DIRECT_POST,
        isSigned = true,
        isEncrypted = false,
        jwkThumbprint = null,
        isAnnexC = false,
        customData = null,
    )

    private fun dummyCoseSign1(): CoseSign1 = CoseSign1(
        protected = byteArrayOf(),
        unprotected = CoseHeaders(),
        payload = byteArrayOf(),
        signature = byteArrayOf(),
    )
}
