package id.walt.issuer2.status

import id.walt.issuer2.config.CredentialProfileConfig
import id.walt.issuer2.domain.CredentialProfile
import id.walt.issuer2.domain.IssuanceSession
import id.walt.openid4vci.offers.AuthenticationMethod
import io.ktor.http.Url
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

/**
 * Unit tests for credential status configuration and session creation
 */
class CredentialStatusUnitTest {

    private val testIssuerKey = buildJsonObject {
        put("type", "jwk")
        putJsonObject("jwk") {
            put("kty", "EC")
            put("crv", "P-256")
            put("x", "test")
            put("y", "test")
        }
    }

    private val testCredentialData = buildJsonObject {
        put("@context", "https://www.w3.org/2018/credentials/v1")
        put("type", "VerifiableCredential")
        putJsonObject("credentialSubject") {
            put("id", "did:example:123")
        }
    }

    @Test
    fun `CredentialProfile should support credentialStatus field`() {
        val status = buildJsonObject {
            put("id", "https://issuer.example.com/status/1#123")
            put("type", "BitstringStatusListEntry")
            put("statusPurpose", "revocation")
            put("statusListIndex", "123")
            put("statusListCredential", "https://issuer.example.com/status/1")
        }

        val profile = CredentialProfile(
            profileId = "test-profile",
            name = "Test Profile",
            credentialConfigurationId = "TestCredential_jwt_vc_json",
            issuerKey = testIssuerKey,
            issuerDid = "did:key:test",
            credentialData = testCredentialData,
            credentialStatus = status
        )

        assertNotNull(profile.credentialStatus)
        assertEquals("BitstringStatusListEntry", profile.credentialStatus?.jsonObject?.get("type")?.jsonPrimitive?.content)
    }

    @Test
    fun `CredentialProfile should support null credentialStatus`() {
        val profile = CredentialProfile(
            profileId = "test-profile",
            name = "Test Profile",
            credentialConfigurationId = "TestCredential_jwt_vc_json",
            issuerKey = testIssuerKey,
            issuerDid = "did:key:test",
            credentialData = testCredentialData,
            credentialStatus = null
        )

        assertNull(profile.credentialStatus)
    }

    @Test
    fun `IssuanceSession should support credentialStatus field`() {
        val status = buildJsonObject {
            putJsonObject("status_list") {
                put("idx", 12345)
                put("uri", "https://issuer.example.com/status/1")
            }
        }

        val session = IssuanceSession(
            sessionId = UUID.randomUUID().toString(),
            profileId = "test-profile",
            authenticationMethod = AuthenticationMethod.PRE_AUTHORIZED,
            credentialConfigurationId = "TestCredential_jwt_vc_json",
            issuerKey = testIssuerKey,
            credentialData = testCredentialData,
            expiresAt = Clock.System.now().plus(5.minutes),
            credentialStatus = status
        )

        assertNotNull(session.credentialStatus)
        val statusObj = session.credentialStatus?.jsonObject
        assertNotNull(statusObj?.get("status_list"))
    }

    @Test
    fun `IssuanceSession should support null credentialStatus`() {
        val session = IssuanceSession(
            sessionId = UUID.randomUUID().toString(),
            profileId = "test-profile",
            authenticationMethod = AuthenticationMethod.PRE_AUTHORIZED,
            credentialConfigurationId = "TestCredential_jwt_vc_json",
            issuerKey = testIssuerKey,
            credentialData = testCredentialData,
            expiresAt = Clock.System.now().plus(5.minutes),
            credentialStatus = null
        )

        assertNull(session.credentialStatus)
    }

    @Test
    fun `CredentialProfileConfig should support credentialStatus field`() {
        val status = buildJsonObject {
            put("id", "https://issuer.example.com/status/1#456")
            put("type", "BitstringStatusListEntry")
            put("statusPurpose", "suspension")
            put("statusListIndex", "456")
            put("statusListCredential", "https://issuer.example.com/status/1")
        }

        val config = CredentialProfileConfig(
            name = "Test Config",
            credentialConfigurationId = "TestCredential_jwt_vc_json",
            issuerKey = testIssuerKey,
            credentialData = testCredentialData,
            credentialStatus = status
        )

        assertNotNull(config.credentialStatus)
        
        // Serialize and deserialize to ensure it works correctly
        val json = Json { ignoreUnknownKeys = true }
        val serialized = json.encodeToString(CredentialProfileConfig.serializer(), config)
        val deserialized = json.decodeFromString(CredentialProfileConfig.serializer(), serialized)
        
        assertNotNull(deserialized.credentialStatus)
        assertEquals("suspension", deserialized.credentialStatus?.jsonObject?.get("statusPurpose")?.jsonPrimitive?.content)
    }

    @Test
    fun `TokenStatusList format should be supported`() {
        // IETF Token Status List format
        val status = buildJsonObject {
            putJsonObject("status_list") {
                put("idx", 94567)
                put("uri", "https://issuer.example.com/status/1")
            }
        }

        val profile = CredentialProfile(
            profileId = "test-profile",
            name = "Test Profile",
            credentialConfigurationId = "TestCredential_dc_sd_jwt",
            issuerKey = testIssuerKey,
            issuerDid = "did:key:test",
            credentialData = testCredentialData,
            credentialStatus = status
        )

        assertNotNull(profile.credentialStatus)
        val statusList = profile.credentialStatus?.jsonObject?.get("status_list")?.jsonObject
        assertNotNull(statusList)
        assertEquals(94567, statusList["idx"]?.jsonPrimitive?.longOrNull)
        assertEquals("https://issuer.example.com/status/1", statusList["uri"]?.jsonPrimitive?.content)
    }

    @Test
    fun `W3C BitstringStatusList format should be supported`() {
        val status = buildJsonObject {
            put("id", "https://issuer.example.com/status/1#94567")
            put("type", "BitstringStatusListEntry")
            put("statusPurpose", "revocation")
            put("statusListIndex", "94567")
            put("statusListCredential", "https://issuer.example.com/status/1")
        }

        val profile = CredentialProfile(
            profileId = "test-profile",
            name = "Test Profile",
            credentialConfigurationId = "TestCredential_jwt_vc_json",
            issuerKey = testIssuerKey,
            issuerDid = "did:key:test",
            credentialData = testCredentialData,
            credentialStatus = status
        )

        assertNotNull(profile.credentialStatus)
        val statusObj = profile.credentialStatus?.jsonObject
        assertEquals("BitstringStatusListEntry", statusObj?.get("type")?.jsonPrimitive?.content)
        assertEquals("revocation", statusObj?.get("statusPurpose")?.jsonPrimitive?.content)
        assertEquals("94567", statusObj?.get("statusListIndex")?.jsonPrimitive?.content)
    }

    @Test
    fun `StatusList2021 format should be supported`() {
        val status = buildJsonObject {
            put("id", "https://issuer.example.com/status/1#500")
            put("type", "StatusList2021Entry")
            put("statusPurpose", "revocation")
            put("statusListIndex", "500")
            put("statusListCredential", "https://issuer.example.com/status/1")
        }

        val profile = CredentialProfile(
            profileId = "test-profile",
            name = "Test Profile",
            credentialConfigurationId = "TestCredential_jwt_vc_json",
            issuerKey = testIssuerKey,
            issuerDid = "did:key:test",
            credentialData = testCredentialData,
            credentialStatus = status
        )

        assertNotNull(profile.credentialStatus)
        assertEquals("StatusList2021Entry", profile.credentialStatus?.jsonObject?.get("type")?.jsonPrimitive?.content)
    }
}
