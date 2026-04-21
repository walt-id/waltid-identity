package id.walt.issuer2

import id.walt.issuer2.config.CredentialProfileConfig
import id.walt.issuer2.config.CredentialProfilesConfig
import id.walt.issuer2.config.OSSIssuer2ServiceConfig
import id.walt.issuer2.models.AuthenticationMethod
import id.walt.issuer2.models.CredentialOfferValueMode
import id.walt.openid4vci.CredentialFormat
import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class Issuer2ConfigTest {

    @Test
    fun testServiceConfigParsing() {
        val config = OSSIssuer2ServiceConfig(
            baseUrl = "http://localhost:7004",
            tokenKey = Json.parseToJsonElement("""
                {
                    "type": "jwk",
                    "jwk": {
                        "kty": "EC",
                        "crv": "P-256",
                        "kid": "test-key"
                    }
                }
            """.trimIndent()) as JsonObject
        )
        
        assertEquals("http://localhost:7004", config.baseUrl)
        assertNotNull(config.tokenKey)
    }

    @Test
    fun testProfileConfigParsing() {
        val profile = CredentialProfileConfig(
            profileId = "test-profile",
            name = "Test Profile",
            credentialConfigurationId = "TestCredential",
            issuerKey = Json.parseToJsonElement("""{"type": "jwk"}""") as JsonObject,
            credentialData = Json.parseToJsonElement("""{"type": ["VerifiableCredential"]}""") as JsonObject,
        )
        
        assertEquals("test-profile", profile.profileId)
        assertEquals("Test Profile", profile.name)
        assertEquals("TestCredential", profile.credentialConfigurationId)
    }

    @Test
    fun testCredentialConfigurationParsing() {
        val credConfig = CredentialConfiguration(
            format = CredentialFormat.SD_JWT_VC,
            scope = "identity_credential",
            vct = "https://example.com/identity_credential",
        )
        
        assertEquals(CredentialFormat.SD_JWT_VC, credConfig.format)
        assertEquals("identity_credential", credConfig.scope)
        assertEquals("https://example.com/identity_credential", credConfig.vct)
    }

    @Test
    fun testProfilesConfigParsing() {
        val profilesConfig = CredentialProfilesConfig(
            profiles = listOf(
                CredentialProfileConfig(
                    profileId = "test",
                    name = "Test",
                    credentialConfigurationId = "TestCred",
                    issuerKey = Json.parseToJsonElement("""{"type": "jwk"}""") as JsonObject,
                    credentialData = Json.parseToJsonElement("""{}""") as JsonObject,
                )
            ),
            credentialConfigurations = mapOf(
                "TestCred" to CredentialConfiguration(
                    format = CredentialFormat.SD_JWT_VC,
                )
            )
        )
        
        assertEquals(1, profilesConfig.profiles.size)
        assertEquals(1, profilesConfig.credentialConfigurations.size)
    }

    @Test
    fun testAuthenticationMethodEnum() {
        assertEquals(AuthenticationMethod.PRE_AUTHORIZED, AuthenticationMethod.valueOf("PRE_AUTHORIZED"))
        assertEquals(AuthenticationMethod.AUTHORIZED, AuthenticationMethod.valueOf("AUTHORIZED"))
    }

    @Test
    fun testCredentialOfferValueModeEnum() {
        assertEquals(CredentialOfferValueMode.BY_REFERENCE, CredentialOfferValueMode.valueOf("BY_REFERENCE"))
        assertEquals(CredentialOfferValueMode.BY_VALUE, CredentialOfferValueMode.valueOf("BY_VALUE"))
    }
}
