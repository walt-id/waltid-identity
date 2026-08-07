package id.walt.wallet2.mobile

import android.content.Intent
import android.content.pm.Signature
import android.content.pm.SigningInfo
import android.service.credentials.CredentialProviderService
import androidx.credentials.DigitalCredential
import androidx.credentials.ExperimentalDigitalCredentialApi
import androidx.credentials.GetDigitalCredentialOption
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialUnknownException
import androidx.credentials.provider.PendingIntentHandler
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.runner.RunWith
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowSigningInfo
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidDigitalCredentialProviderTest {
    @OptIn(ExperimentalDigitalCredentialApi::class)
    @Config(sdk = [35])
    @Test
    fun extractsTheOfficialProviderRequestAndUsesTheAuthenticatedNativeCallerOrigin() {
        val signature = Signature(byteArrayOf(1, 2, 3, 4))
        val intent = providerIntent(
            requestJson = """{"protocol":"openid4vp-v1-unsigned","data":{"nonce":"n"}}""",
            packageName = "id.walt.caller",
            signingInfo = signingInfo(signature),
        )

        val input = AndroidDigitalCredentialProvider.extract(intent, """{"apps":[]}""")

        assertEquals("id.walt.caller", input.providerRequest.callingAppInfo.packageName)
        assertEquals(
            AndroidDigitalCredentialProvider.nativeAppOrigin(signature.toByteArray()),
            input.request.verifiedOrigin,
        )
        assertEquals(MobileWalletDigitalCredentialProtocols.OPENID4VP_UNSIGNED, input.request.protocol)
    }

    @OptIn(ExperimentalDigitalCredentialApi::class)
    @Config(sdk = [35])
    @Test
    fun rejectsAClaimedBrowserOriginWhenTheCallerIsNotAllowlisted() {
        val intent = providerIntent(
            requestJson = """{"protocol":"openid4vp-v1-unsigned","data":{"nonce":"n"}}""",
            packageName = "id.walt.untrusted-browser",
            signingInfo = signingInfo(Signature(byteArrayOf(5, 6, 7, 8))),
            origin = "https://verifier.example",
        )

        assertFailsWith<IllegalStateException> {
            AndroidDigitalCredentialProvider.extract(intent, """{"apps":[]}""")
        }
    }

    @OptIn(ExperimentalDigitalCredentialApi::class)
    @Config(sdk = [35])
    @Test
    fun writesOfficialCredentialManagerResponsesCancellationAndFailures() {
        // Credential Manager needs the answered request to return a response too large for an
        // intent extra, so the response is written against a genuinely extracted one.
        val providerRequest = AndroidDigitalCredentialProvider.extract(
            providerIntent(
                requestJson = """{"protocol":"openid4vp-v1-unsigned","data":{"nonce":"n"}}""",
                packageName = "id.walt.caller",
                signingInfo = signingInfo(Signature(byteArrayOf(1, 2, 3, 4))),
            ),
            """{"apps":[]}""",
        ).providerRequest
        val responseIntent = Intent()
        AndroidDigitalCredentialProvider.setResponse(
            responseIntent,
            MobileWalletDigitalCredentialResponse(
                protocol = MobileWalletDigitalCredentialProtocols.OPENID4VP_UNSIGNED,
                dataJson = """{"vp_token":{"pid":["presentation"]}}""",
            ),
            providerRequest,
        )

        val response = assertNotNull(PendingIntentHandler.retrieveGetCredentialResponse(responseIntent))
        val credential = assertIs<DigitalCredential>(response.credential)
        val responseJson = Json.parseToJsonElement(credential.credentialJson).jsonObject
        assertEquals(
            MobileWalletDigitalCredentialProtocols.OPENID4VP_UNSIGNED,
            responseJson["protocol"]?.jsonPrimitive?.content,
        )
        assertEquals(
            "presentation",
            responseJson["data"]?.jsonObject
                ?.get("vp_token")?.jsonObject
                ?.get("pid")?.jsonArray?.single()?.jsonPrimitive?.content,
        )

        val cancellationIntent = Intent()
        AndroidDigitalCredentialProvider.setCancellation(cancellationIntent)
        assertIs<GetCredentialCancellationException>(
            PendingIntentHandler.retrieveGetCredentialException(cancellationIntent),
        )

        val failureIntent = Intent()
        AndroidDigitalCredentialProvider.setFailure(failureIntent, "safe failure")
        val failure = assertIs<GetCredentialUnknownException>(
            PendingIntentHandler.retrieveGetCredentialException(failureIntent),
        )
        assertEquals("safe failure", failure.errorMessage)
    }

    @OptIn(ExperimentalDigitalCredentialApi::class)
    private fun providerIntent(
        requestJson: String,
        packageName: String,
        signingInfo: SigningInfo,
        origin: String? = null,
    ): Intent {
        val option = GetDigitalCredentialOption(requestJson)
        val frameworkOption = android.credentials.CredentialOption.Builder(
            option.type,
            option.requestData,
            option.candidateQueryData,
        ).build()
        val callingApp = if (origin == null) {
            android.service.credentials.CallingAppInfo(packageName, signingInfo)
        } else {
            android.service.credentials.CallingAppInfo(packageName, signingInfo, origin)
        }
        return Intent().putExtra(
            CredentialProviderService.EXTRA_GET_CREDENTIAL_REQUEST,
            android.service.credentials.GetCredentialRequest(callingApp, listOf(frameworkOption)),
        )
    }

    private fun signingInfo(signature: Signature): SigningInfo = SigningInfo().also { signingInfo ->
        Shadow.extract<ShadowSigningInfo>(signingInfo).apply {
            setSignatures(arrayOf(signature))
            setPastSigningCertificates(arrayOf(signature))
        }
    }

    @Test
    fun parsesOfficialRequestEnvelopeAndPreservesSelectedOpaqueEntries() {
        val request = AndroidDigitalCredentialProvider.parseProtocolRequest(
            requestJson = """{"requests":[{"protocol":"openid4vp-v1-unsigned","data":{"nonce":"n"}}]}""",
            verifiedOrigin = "https://verifier.example",
            selectedRegistryEntryIds = listOf("opaque-entry"),
        )

        assertEquals(MobileWalletDigitalCredentialProtocols.OPENID4VP_UNSIGNED, request.protocol)
        assertEquals("n", Json.parseToJsonElement(request.dataJson).jsonObject["nonce"].toString().trim('"'))
        assertEquals(listOf("opaque-entry"), request.selectedRegistryEntryIds)
    }

    @Test
    fun rejectsAmbiguousOrMalformedProtocolRequests() {
        assertFailsWith<IllegalArgumentException> {
            AndroidDigitalCredentialProvider.parseProtocolRequest(
                requestJson = """{"requests":[]}""",
                verifiedOrigin = "https://verifier.example",
            )
        }
        assertFailsWith<IllegalArgumentException> {
            AndroidDigitalCredentialProvider.parseProtocolRequest(
                requestJson = """{"protocol":"openid4vp-v1-unsigned","data":"secret"}""",
                verifiedOrigin = "https://verifier.example",
            )
        }
    }

    @Test
    fun bindsTheNativeCallerSigningCertificateIntoTheOrigin() {
        assertTrue(AndroidDigitalCredentialProvider.nativeAppOrigin(byteArrayOf(1, 2, 3)).startsWith("android:apk-key-hash:"))
    }

    /**
     * The adapter must not canonicalize; it delegates to `DcApiWallet.canonicalizePlatformOrigin`,
     * which also feeds the mdoc session transcript. Asserting through `extract` rather than on a
     * helper is deliberate: a reintroduced private normalizer would still satisfy a helper-level
     * test while producing a transcript the verifier cannot reproduce.
     */
    @OptIn(ExperimentalDigitalCredentialApi::class)
    @Config(sdk = [35])
    @Test
    fun canonicalizesAnAllowlistedBrowserOriginThroughTheSharedProtocolRule() {
        val signature = Signature(byteArrayOf(9, 9, 9, 9))
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
        val fingerprint = digest.joinToString(":") { byte -> "%02X".format(byte) }
        val intent = providerIntent(
            requestJson = """{"protocol":"openid4vp-v1-unsigned","data":{"nonce":"n"}}""",
            packageName = "id.walt.browser",
            signingInfo = signingInfo(signature),
            // Uppercase host and an explicit default port: the two cases where the deleted adapter
            // copy and the protocol rule used to disagree.
            origin = "https://VERIFIER.example:443",
        )

        val input = AndroidDigitalCredentialProvider.extract(
            intent,
            """{"apps":[{"type":"android","info":{"package_name":"id.walt.browser","signatures":[{"build":"release","cert_fingerprint_sha256":"$fingerprint"}]}}]}""",
        )

        assertEquals("https://verifier.example", input.request.verifiedOrigin)
    }

    @Test
    fun normalizesMatcherCredentialIdsWithoutRewritingOpaqueAndroidXIds() {
        assertEquals(
            "dc-opaque",
            AndroidDigitalCredentialProvider.normalizeMatcherCredentialId("0 org-iso-mdoc dc-opaque"),
        )
        assertEquals(
            "opaque-entry",
            AndroidDigitalCredentialProvider.normalizeMatcherCredentialId("opaque-entry"),
        )
        assertEquals(
            "not a recognized-envelope",
            AndroidDigitalCredentialProvider.normalizeMatcherCredentialId("not a recognized-envelope"),
        )
    }
}
