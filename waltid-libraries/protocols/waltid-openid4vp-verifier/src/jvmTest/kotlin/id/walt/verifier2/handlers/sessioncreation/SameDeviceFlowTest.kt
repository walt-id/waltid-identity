package id.walt.verifier2.handlers.sessioncreation

import id.walt.verifier.openid.models.openid.OpenID4VPResponseMode
import id.walt.verifier2.data.CrossDeviceFlowSetup
import id.walt.verifier2.data.GeneralFlowConfig
import id.walt.verifier2.data.SameDeviceFlowSetup
import id.walt.verifier2.data.UrlConfig
import id.walt.verifier2.data.Verification2Session
import id.walt.verifier2.data.Verification2Session.VerificationSessionRedirects
import id.walt.verifier2.data.VerificationSessionSetup
import io.ktor.http.Url
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.serialization.json.jsonObject
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Same-device uses the shared direct-post verifier while preserving its required return configuration. */
class SameDeviceFlowTest {
    private val redirects = VerificationSessionRedirects(
        successRedirectUri = Url("https://example.com/success"),
        errorRedirectUri = Url("https://example.com/error"),
    )

    @Test
    fun `same device creates a direct post session with redirects`() = runTest {
        val setup = SameDeviceFlowSetup(GeneralFlowConfig(), UrlConfig(), redirects = redirects)
        val session = VerificationSessionCreator.createVerificationSession(
            setup, clientId = null,
            urlPrefix = "https://verifier.example/verification-session", urlHost = "openid4vp://authorize",
        )
        assertEquals(OpenID4VPResponseMode.DIRECT_POST, session.authorizationRequest.responseMode)
        assertEquals(redirects, session.redirects)
        assertEquals("https://verifier.example/verification-session/${session.id}/response", session.authorizationRequest.responseUri)
        assertNull(session.authorizationRequest.redirectUri)
        val restored = Json.decodeFromString<Verification2Session>(Json.encodeToString(session))
        assertIs<SameDeviceFlowSetup>(restored.setup)
        assertEquals(session.redirects, restored.redirects)
    }

    @Test
    fun `cross device retains browser redirects and uses direct post`() = runTest {
        val session = VerificationSessionCreator.createVerificationSession(
            CrossDeviceFlowSetup(GeneralFlowConfig(), redirects = redirects), clientId = null,
            urlPrefix = "https://verifier.example/verification-session", urlHost = "openid4vp://authorize",
        )
        assertEquals(redirects, session.redirects)
        assertEquals(OpenID4VPResponseMode.DIRECT_POST, session.authorizationRequest.responseMode)
        assertNotNull(session.authorizationRequest.responseUri)
        assertNull(session.authorizationRequest.redirectUri)
    }

    @Test
    fun `documented JSON names deserialize and URL configuration defaults`() {
        val setup = Json.decodeFromString<VerificationSessionSetup>("""{
            "flow_type":"same_device", "core_flow":{},
            "redirects":{"success_redirect_uri":"https://example.com/success"}
        }""")
        assertIs<SameDeviceFlowSetup>(setup)
        assertEquals(UrlConfig(), setup.urlConfig)
        assertEquals(redirects.successRedirectUri, setup.redirects.successRedirectUri)
        val serialized = Json.encodeToJsonElement(VerificationSessionSetup.serializer(), setup).jsonObject
        assertTrue("core_flow" in serialized)
        assertTrue("core" !in serialized && "urlConfig" !in serialized)
    }

    @Test
    fun `same device requires redirects`() {
        assertFailsWith<SerializationException> {
            Json.decodeFromString<VerificationSessionSetup>("""{"flow_type":"same_device","core_flow":{}}""")
        }
    }

    @Test
    fun `same device requires a response URL prefix`() = runTest {
        val error = assertFailsWith<IllegalArgumentException> {
            VerificationSessionCreator.createVerificationSession(
                SameDeviceFlowSetup(GeneralFlowConfig(), redirects = redirects), clientId = null,
                urlPrefix = null, urlHost = "openid4vp://authorize",
            )
        }
        assertEquals("urlPrefix is required for same-device and cross-device flows", error.message)
    }

    @Test
    fun `encrypted same device responses use direct post JWT`() = runTest {
        val session = VerificationSessionCreator.createVerificationSession(
            SameDeviceFlowSetup(GeneralFlowConfig(encryptedResponse = true), redirects = redirects), clientId = null,
            urlPrefix = "https://verifier.example/verification-session", urlHost = "openid4vp://authorize",
        )
        assertEquals(OpenID4VPResponseMode.DIRECT_POST_JWT, session.authorizationRequest.responseMode)
        assertEquals(redirects, session.redirects)
    }
}
