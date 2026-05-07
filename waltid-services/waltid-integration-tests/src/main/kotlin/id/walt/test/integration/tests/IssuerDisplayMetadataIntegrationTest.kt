package id.walt.test.integration.tests

import id.walt.test.integration.expectSuccess
import io.ktor.client.call.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration test for issuer display metadata feature in OpenID4VCI metadata endpoint
 */
class IssuerDisplayMetadataIntegrationTest : AbstractIntegrationTest() {

    @Test
    fun testWellKnownOpenidCredentialIssuerEndpointReturnsMetadata() = runTest {
        // When: Fetching OpenID credential issuer metadata
        val response = issuerApi.getProviderMetaDataRaw()

        // Then: Should return 200 OK
        response.expectSuccess()

        // Parse response
        val metadata: JsonObject = response.body()

        // Verify required OpenID4VCI fields
        assertNotNull(metadata["credential_issuer"], "Should have credential_issuer field")
        assertNotNull(metadata["credential_endpoint"], "Should have credential_endpoint field")
        assertNotNull(metadata["credential_configurations_supported"], "Should have credential_configurations_supported field")
    }

    @Test
    fun testMetadataDisplayFieldHasCorrectStructure() = runTest {
        // When: Fetching metadata
        val response = issuerApi.getProviderMetaDataRaw()
        val metadata: JsonObject = response.body()

        // Then: If display field is present, it should be a valid JSON array
        val displayValue = metadata["display"]
        if (displayValue != null && displayValue !is JsonNull) {
            assertTrue(
                displayValue is JsonArray,
                "Display should be a JSON array when present, but was: ${displayValue::class.simpleName}"
            )
        }
    }

    @Test
    fun testMetadataIncludesAllStandardOpenId4VciDraft13Fields() = runTest {
        // When: Fetching Draft 13 metadata
        val response = issuerApi.getProviderMetaDataRaw()
        val metadata: JsonObject = response.body()

        // Then: Should include all required and common optional fields
        val requiredFields = listOf(
            "credential_issuer",
            "credential_endpoint"
        )

        val commonOptionalFields = listOf(
            "authorization_endpoint",
            "token_endpoint",
            "jwks_uri",
            "grant_types_supported",
            "credential_configurations_supported",
            "authorization_servers"
        )

        // Verify required fields
        requiredFields.forEach { field ->
            assertTrue(metadata.containsKey(field), "Missing required field: $field")
        }

        // Verify common optional fields (should be present in walt.id implementation)
        commonOptionalFields.forEach { field ->
            assertTrue(metadata.containsKey(field), "Missing expected field: $field")
        }
    }

    @Test
    fun testMetadataIsValidJsonAndProperlyFormatted() = runTest {
        // When: Fetching metadata
        val response = issuerApi.getProviderMetaDataRaw()

        // Then: Should have correct content type
        val contentType = response.contentType()
        assertEquals(ContentType.Application.Json.contentType, contentType?.contentType, "Should return JSON content type")

        // Verify it's valid JSON
        val metadata: JsonObject = response.body()
        assertTrue(metadata.isNotEmpty(), "Metadata should not be empty")

        // Verify JSON structure quality
        assertNotNull(metadata["credential_issuer"]?.jsonPrimitive?.contentOrNull, "credential_issuer should be a string")
    }

    @Test
    fun testMetadataIncludesIssuerDisplayWhenConfigured() = runTest {
        // Given: Integration test config has issuerDisplay configured
        // When: Fetching metadata
        val response = issuerApi.getProviderMetaDataRaw()
        response.expectSuccess()
        
        val metadata: JsonObject = response.body()

        // Then: Should include display field
        assertTrue(metadata.containsKey("display"), "Metadata should contain display field when configured")
        
        val display = metadata["display"]
        assertNotNull(display, "Display should not be null")
        assertTrue(display is JsonArray, "Display should be a JSON array")
        
        val displayArray = display.jsonArray
        assertTrue(displayArray.isNotEmpty(), "Display array should not be empty")
        
        // Verify the first display entry
        val firstDisplay = displayArray[0].jsonObject
        
        // Verify required display properties
        assertEquals(
            "walt.id Integration Test Issuer",
            firstDisplay["name"]?.jsonPrimitive?.contentOrNull,
            "Display name should match configured value"
        )
        
        assertEquals(
            "en",
            firstDisplay["locale"]?.jsonPrimitive?.contentOrNull,
            "Display locale should match configured value"
        )
        
        assertEquals(
            "walt.id test issuer for integration testing",
            firstDisplay["description"]?.jsonPrimitive?.contentOrNull,
            "Display description should match configured value"
        )
        
        // Verify logo object
        val logo = firstDisplay["logo"]?.jsonObject
        assertNotNull(logo, "Logo should be present")
        
        assertEquals(
            "https://wallet.walt-test.cloud/logo.png",
            logo["url"]?.jsonPrimitive?.contentOrNull,
            "Logo URL should match configured value"
        )
        
        assertEquals(
            "walt.id logo",
            logo["alt_text"]?.jsonPrimitive?.contentOrNull,
            "Logo alt_text should match configured value"
        )
    }
}
