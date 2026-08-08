@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.walletdemo.compose.android

import android.content.Intent
import android.content.pm.PackageManager
import android.util.Base64
import androidx.credentials.DigitalCredential
import androidx.credentials.ExperimentalDigitalCredentialApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import id.walt.cose.coseCompliantCbor
import id.walt.cose.toCoseKey
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.toPublicJwk
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.iso18013.annexc.AnnexCRequestBuilder
import id.walt.iso18013.annexc.AnnexCResponseVerifier
import id.walt.mdoc.objects.deviceretrieval.DeviceResponse
import id.walt.mobile.test.backend.DemoTestBackend
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.CREDENTIAL_OPERATION_TIMEOUT
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.UI_ELEMENT_TIMEOUT
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.clickByTag
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.launchAndUnlock
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.sendDeepLink
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.waitForStatus
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.security.MessageDigest

/**
 * OS-mediated ISO 18013-7 Annex C (`org-iso-mdoc`) sharing E2E through Android Credential Manager.
 *
 * The test is its own Annex C reader. That is not a shortcut around the wallet: Annex C has no
 * back-channel at all - the encrypted DeviceResponse travels back through the OS to whoever called
 * `getCredential`, so the reader and the caller are necessarily the same party. It builds the
 * DeviceRequest and EncryptionInfo with the shared verifier-side [AnnexCRequestBuilder], and
 * HPKE-decrypts the result with [AnnexCResponseVerifier], which is the same code the deployed
 * verifier runs.
 *
 * Being its own reader is also what makes the test possible. The hosted verifier's Annex C session
 * setup requires a secure-context (HTTPS or localhost) origin, and Credential Manager asserts
 * `android:apk-key-hash:...` for a native caller - so a hosted session cannot be bound to this
 * caller's origin the way [DigitalCredentialSharingE2ETest] binds the Annex D one.
 *
 * What this proves that the encoding unit tests cannot: Credential Manager actually routes an
 * `org-iso-mdoc` request to the wallet's registered Annex C registry, the matcher surfaces the mDL,
 * and the wallet's HPKE seal opens against a session transcript the reader derived independently.
 * A wrong transcript, origin, or encryption-info binding fails decryption instead of passing.
 *
 * Requires an emulator image with Google Play services, like the Annex D E2E.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalDigitalCredentialApi::class)
class AnnexCSharingE2ETest {
    @Test
    fun receivesMdlAndSharesItAsAnnexCThroughAndroidCredentialManager() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        assumeTrue(
            "Annex C E2E requires an Android emulator with Google Play services",
            hasGooglePlayServices(context),
        )

        val scenario = DemoTestBackend.presentationScenarios.first { it.id == "iso-mdl" }
        val offer = DemoTestBackend.createOffer(scenario)
        launchAndUnlock(context, device)
        sendDeepLink(context, offer.offerUrl)
        clickByTag(device, "wallet.receiveButton")
        assertTrue(
            "Offer preview did not appear",
            waitForStatus(
                device = device,
                timeoutMs = CREDENTIAL_OPERATION_TIMEOUT,
                matcher = { it.startsWith("Review credential offer") },
                failurePrefixes = listOf("Receive failed", "Bootstrap failed"),
            ),
        )
        clickByTag(device, "wallet.offerAcceptButton")
        assertTrue(
            "mDL was not received",
            waitForStatus(
                device = device,
                timeoutMs = CREDENTIAL_OPERATION_TIMEOUT,
                matcher = { it.startsWith("Received") },
                failurePrefixes = listOf("Receive failed", "Bootstrap failed"),
            ),
        )

        // The reader's recipient key. Annex C fixes the suite at DHKEM(P-256)/HKDF-SHA256/AES-128-GCM,
        // so this is P-256 key agreement and nothing else is negotiable.
        val runtime = CryptoRuntime(defaultSoftwareKeyProviders())
        val recipientKey = runtime.generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId("annex-c-e2e-reader"),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.KEY_AGREEMENT),
            )
        )
        val recipientPublicCoseKey = requireNotNull(recipientKey.capabilities.publicKeyExporter) {
            "Reader recipient key does not export its public key"
        }.exportPublicKey().toPublicJwk(recipientKey.spec).toCoseKey()

        val annexCRequest = AnnexCRequestBuilder.build(
            docType = MDL_DOC_TYPE,
            requestedElements = mapOf(MDL_NAMESPACE to REQUESTED_ELEMENTS),
            nonce = ByteArray(16) { (it * 7 + 1).toByte() },
            recipientPublicKey = recipientPublicCoseKey,
        )

        DigitalCredentialTestVerifier.reset(annexCRequestJson(annexCRequest))
        context.startActivity(
            Intent(context, DigitalCredentialTestVerifierActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )

        val candidate = device.wait(Until.findObject(By.text(MDL_DOC_TYPE)), UI_ELEMENT_TIMEOUT)
        assertNotNull("Credential Manager did not surface the mDL Annex C candidate", candidate)
        val continueButton = device.wait(
            Until.findObject(By.res("continue_button")),
            UI_ELEMENT_TIMEOUT,
        ) ?: device.wait(Until.findObject(By.text("Continue")), UI_ELEMENT_TIMEOUT)
        assertNotNull("Credential Manager did not offer consent", continueButton)
        continueButton!!.click()

        val shareButton = device.wait(
            Until.findObject(By.res("android:id/button1")),
            UI_ELEMENT_TIMEOUT,
        ) ?: device.wait(Until.findObject(By.text("Share")), UI_ELEMENT_TIMEOUT)
            ?: device.wait(Until.findObject(By.text("SHARE")), UI_ELEMENT_TIMEOUT)
        assertNotNull("Wallet provider consent did not open", shareButton)
        shareButton!!.click()

        val response = withTimeout(CREDENTIAL_OPERATION_TIMEOUT) {
            DigitalCredentialTestVerifier.await().getOrThrow()
        }
        val credential = response.credential as? DigitalCredential
        assertNotNull("Annex C reader did not receive a digital credential", credential)
        val responseJson = Json.parseToJsonElement(credential!!.credentialJson).jsonObject
        assertEquals("org-iso-mdoc", responseJson["protocol"]?.jsonPrimitive?.content)
        val encryptedResponse = requireNotNull(
            responseJson["data"]?.jsonObject?.get("response")?.jsonPrimitive?.content,
        ) { "Annex C response carries no encrypted response: $responseJson" }

        // The single real control in this flow. `info` is CBOR(SessionTranscript) over
        // sha256(cbor([encryptionInfoB64, origin])), so decryption succeeds only if the wallet hashed
        // the origin Credential Manager asserted for this caller and the same encryptionInfo string
        // the reader sent. Any drift in either fails here rather than producing a wrong plaintext.
        val deviceResponseCbor = AnnexCResponseVerifier.decryptToDeviceResponse(
            encryptedResponseB64 = encryptedResponse,
            encryptionInfoB64 = annexCRequest.encryptionInfoB64,
            origin = nativeAppOrigin(context),
            recipientPrivateKey = recipientKey,
        )

        val deviceResponse = coseCompliantCbor.decodeFromByteArray(
            DeviceResponse.serializer(),
            deviceResponseCbor,
        )
        assertEquals("1.0", deviceResponse.version)
        assertEquals(0u, deviceResponse.status)
        val documents = requireNotNull(deviceResponse.documents) { "Annex C DeviceResponse has no documents" }
        assertEquals(1, documents.size)
        val document = documents.single()
        assertEquals(MDL_DOC_TYPE, document.docType)

        // Assert the requested elements actually came back. Without this the test would pass on an
        // empty but well-formed DeviceResponse, which is exactly what a broken matcher selection or a
        // dropped disclosure produces.
        val issuerNamespaces = requireNotNull(document.issuerSigned.namespaces) {
            "Annex C document carries no issuer-signed namespaces"
        }
        val disclosed = requireNotNull(issuerNamespaces[MDL_NAMESPACE]) {
            "Annex C document does not disclose $MDL_NAMESPACE: ${issuerNamespaces.keys}"
        }.entries.map { it.value.elementIdentifier }.toSet()
        REQUESTED_ELEMENTS.forEach { element ->
            assertTrue("Annex C response omitted $element. Disclosed: $disclosed", disclosed.contains(element))
        }
        assertNotNull("Annex C document carries no device signature", document.deviceSigned)
    }

    /** The DC API request envelope Credential Manager routes by `protocol`. */
    private fun annexCRequestJson(request: id.walt.iso18013.annexc.AnnexCRequest): String =
        Json.encodeToString(
            JsonObject.serializer(),
            buildJsonObject {
                put(
                    "requests",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put("protocol", JsonPrimitive("org-iso-mdoc"))
                                put(
                                    "data",
                                    buildJsonObject {
                                        put("deviceRequest", JsonPrimitive(request.deviceRequestB64))
                                        put("encryptionInfo", JsonPrimitive(request.encryptionInfoB64))
                                    },
                                )
                            },
                        )
                    },
                )
            },
        )

    /**
     * `android:apk-key-hash:<base64url-sha256(signing cert)>` - the origin Credential Manager
     * asserts for a native caller. Mirrors `AndroidDigitalCredentialProvider.nativeAppOrigin`,
     * which is internal to the wallet-mobile module.
     */
    private fun nativeAppOrigin(context: android.content.Context): String {
        val signatures = context.packageManager
            .getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            .signingInfo
            ?.signingCertificateHistory
            ?: error("Wallet package has no signing certificate")
        val digest = MessageDigest.getInstance("SHA-256").digest(signatures.first().toByteArray())
        val hash = Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        return "android:apk-key-hash:$hash"
    }

    private fun hasGooglePlayServices(context: android.content.Context): Boolean =
        runCatching { context.packageManager.getPackageInfo("com.google.android.gms", 0) }.isSuccess

    private companion object {
        private const val MDL_DOC_TYPE = "org.iso.18013.5.1.mDL"
        private const val MDL_NAMESPACE = "org.iso.18013.5.1"
        private val REQUESTED_ELEMENTS = listOf("family_name", "given_name")
    }
}
