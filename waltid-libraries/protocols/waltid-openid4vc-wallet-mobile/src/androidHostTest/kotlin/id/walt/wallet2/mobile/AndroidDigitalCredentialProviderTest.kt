package id.walt.wallet2.mobile

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidDigitalCredentialProviderTest {
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
    fun canonicalizesOnlyOriginShapedHttpsValuesAndBindsNativeCallerCertificates() {
        assertEquals(
            "https://verifier.example",
            with(AndroidDigitalCredentialProvider) { "https://VERIFIER.example:443/".canonicalWebOrigin() },
        )
        assertFailsWith<IllegalArgumentException> {
            with(AndroidDigitalCredentialProvider) { "https://verifier.example/path".canonicalWebOrigin() }
        }
        assertTrue(AndroidDigitalCredentialProvider.nativeAppOrigin(byteArrayOf(1, 2, 3)).startsWith("android:apk-key-hash:"))
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
