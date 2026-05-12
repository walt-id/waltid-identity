package id.walt.test.integration.tests

import id.walt.test.integration.expectSuccess
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests for OpenID Credential Issuer metadata endpoints.
 * Tests the .well-known/openid-credential-issuer endpoint for different OID4VCI versions.
 */
class IssuerMetadataIntegrationTest : AbstractIntegrationTest() {

    @Test
    fun shouldReturnValidDraft13IssuerMetadata() = runTest {
        val response = issuerApi.getProviderMetaDataRaw()
        response.expectSuccess()
        
        val metadata = response.body<JsonObject>()
        
        assertNotNull(metadata["credential_issuer"]?.jsonPrimitive?.content, 
            "Metadata should contain credential_issuer")
        assertNotNull(metadata["credential_endpoint"]?.jsonPrimitive?.content, 
            "Metadata should contain credential_endpoint")
        assertNotNull(metadata["credential_configurations_supported"]?.jsonObject, 
            "Metadata should contain credential_configurations_supported")
    }

    @Test
    fun shouldContainJwtCredentialConfiguration() = runTest {
        val metadata = issuerApi.getProviderMetaData()
        
        val credentialConfigs = metadata.draft13?.credentialConfigurationsSupported 
            ?: metadata.draft11?.credentialSupported
        assertNotNull(credentialConfigs, "Credential configurations should not be null")
        assertTrue(credentialConfigs.isNotEmpty(), "Should have at least one credential configuration")
        
        val hasJwtConfig = credentialConfigs.any { (_, config) ->
            val formatValue = config.format?.value
            formatValue == "jwt_vc_json" || formatValue == "jwt_vc"
        }
        assertTrue(hasJwtConfig, "Should have at least one JWT credential configuration")
    }

    @Test
    fun shouldContainSdJwtCredentialConfiguration() = runTest {
        val metadata = issuerApi.getProviderMetaData()
        
        val credentialConfigs = metadata.draft13?.credentialConfigurationsSupported 
            ?: metadata.draft11?.credentialSupported
        assertNotNull(credentialConfigs, "Credential configurations should not be null")
        
        val hasSdJwtConfig = credentialConfigs.any { (_, config) ->
            val formatValue = config.format?.value
            formatValue == "vc+sd-jwt" || formatValue == "sd_jwt_vc"
        }
        assertTrue(hasSdJwtConfig, "Should have at least one SD-JWT credential configuration")
    }

    @Test
    fun shouldContainMdocCredentialConfiguration() = runTest {
        val metadata = issuerApi.getProviderMetaData()
        
        val credentialConfigs = metadata.draft13?.credentialConfigurationsSupported 
            ?: metadata.draft11?.credentialSupported
        assertNotNull(credentialConfigs, "Credential configurations should not be null")
        
        val hasMdocConfig = credentialConfigs.any { (_, config) ->
            config.format?.value == "mso_mdoc"
        }
        assertTrue(hasMdocConfig, "Should have at least one mDoc credential configuration")
    }

    @Test
    fun shouldReturnValidDraft11IssuerMetadata() = runTest {
        val client = environment.testHttpClient()
        val response = client.get("/draft11/.well-known/openid-credential-issuer")
        response.expectSuccess()
        
        val metadata = response.body<JsonObject>()
        
        assertNotNull(metadata["credential_issuer"]?.jsonPrimitive?.content, 
            "Draft 11 metadata should contain credential_issuer")
        assertNotNull(metadata["credential_endpoint"]?.jsonPrimitive?.content, 
            "Draft 11 metadata should contain credential_endpoint")
        
        val hasCredentialsSupported = metadata["credentials_supported"] != null || 
            metadata["credential_configurations_supported"] != null
        assertTrue(hasCredentialsSupported, 
            "Draft 11 metadata should contain credentials_supported or credential_configurations_supported")
    }

    @Test
    fun shouldContainAuthorizationServer() = runTest {
        val metadata = issuerApi.getProviderMetaData()
        
        val hasAuthServer = metadata.draft13?.authorizationServers != null || 
            metadata.draft11?.authorizationServer != null
        assertTrue(hasAuthServer, "Metadata should contain authorization server information")
    }

    @Test
    fun shouldContainTokenEndpoint() = runTest {
        val metadata = issuerApi.getProviderMetaData()
        
        assertNotNull(metadata.tokenEndpoint, "Metadata should contain token_endpoint")
    }
}
