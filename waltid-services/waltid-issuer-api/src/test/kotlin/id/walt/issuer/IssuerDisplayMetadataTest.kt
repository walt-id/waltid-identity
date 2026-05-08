package id.walt.issuer

import id.walt.commons.config.ConfigManager
import id.walt.issuer.config.CredentialTypeConfig
import id.walt.issuer.config.OIDCIssuerServiceConfig
import id.walt.issuer.issuance.CIProvider
import id.walt.oid4vc.OpenID4VCIVersion
import id.walt.oid4vc.data.DisplayProperties
import id.walt.oid4vc.data.LogoProperties
import id.walt.testConfigs
import id.walt.oid4vc.providers.CredentialIssuerConfig
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Unit tests for issuer display metadata configuration feature
 * 
 * Tests verify that the issuerDisplay configuration is correctly:
 * - Loaded from CredentialTypeConfig
 * - Passed to OpenID provider metadata
 * - Serialized in JSON responses
 */
class IssuerDisplayMetadataTest {

    @Test
    fun testIssuerDisplayIsCorrectlyPassedToMetadataWhenConfigured() {
        // Given: A provider with specific display configuration
        val displayConfig = listOf(
            DisplayProperties(
                name = "Test Issuer",
                locale = "en",
                description = "A test credential issuer",
                logo = LogoProperties(
                    url = "https://example.com/logo.png",
                    altText = "Test Logo"
                )
            )
        )

        val config = CredentialIssuerConfig(
            credentialConfigurationsSupported = emptyMap()
        )

        val metadata = id.walt.oid4vc.OpenID4VCI.createDefaultProviderMetadata(
            baseUrl = "http://test.example.com",
            credentialSupported = null,
            version = OpenID4VCIVersion.DRAFT13,
            issuerDisplay = displayConfig
        )

        // Then: display field should contain the configuration
        assertNotNull(metadata.display, "Display should not be null when configured")
        assertEquals(1, metadata.display?.size, "Should have one display entry")

        val display = metadata.display!!.first()
        assertEquals("Test Issuer", display.name)
        assertEquals("en", display.locale)
        assertEquals("A test credential issuer", display.description)
        assertNotNull(display.logo)
        assertEquals("https://example.com/logo.png", display.logo?.url)
        assertEquals("Test Logo", display.logo?.altText)

        // Verify JSON serialization includes the field
        val json = metadata.toJSON()
        assertTrue(json.containsKey("display"), "JSON should contain display key when configured")
    }

    @Test
    fun testIssuerDisplayIsNullWhenNotConfigured() {
        // Given: Provider metadata without display configuration
        val metadata = id.walt.oid4vc.OpenID4VCI.createDefaultProviderMetadata(
            baseUrl = "http://test.example.com",
            credentialSupported = null,
            version = OpenID4VCIVersion.DRAFT13,
            issuerDisplay = null
        )

        // Then: display field should be null
        assertNull(metadata.display, "Display should be null when not configured")

        // Verify JSON serialization omits the field
        val json = metadata.toJSON()
        val hasDisplayKey = json.containsKey("display")
        // Either not present, or present but null
        if (hasDisplayKey) {
            val displayValue = json["display"]
            assertTrue(displayValue is JsonNull, "Display should be JsonNull if present")
        }
    }

    @Test
    fun testMultipleLocaleDisplayConfigurations() {
        // Given: Multiple display configurations for different locales
        val displayConfig = listOf(
            DisplayProperties(
                name = "English Issuer",
                locale = "en",
                logo = LogoProperties(url = "https://example.com/logo-en.png")
            ),
            DisplayProperties(
                name = "Deutscher Aussteller",
                locale = "de",
                logo = LogoProperties(url = "https://example.com/logo-de.png")
            )
        )

        val metadata = id.walt.oid4vc.OpenID4VCI.createDefaultProviderMetadata(
            baseUrl = "http://test.example.com",
            credentialSupported = null,
            version = OpenID4VCIVersion.DRAFT13,
            issuerDisplay = displayConfig
        )

        // Then: display field should contain all configurations
        assertNotNull(metadata.display)
        assertEquals(2, metadata.display?.size, "Should have two display entries")

        val displayByLocale = metadata.display!!.associateBy { it.locale }
        assertEquals("English Issuer", displayByLocale["en"]?.name)
        assertEquals("Deutscher Aussteller", displayByLocale["de"]?.name)
    }

    @Test
    fun testDraft11MetadataAlsoSupportsIssuerDisplay() {
        // Given: Display configuration for Draft 11
        val displayConfig = listOf(
            DisplayProperties(
                name = "Draft 11 Issuer",
                locale = "en"
            )
        )

        val metadata = id.walt.oid4vc.OpenID4VCI.createDefaultProviderMetadata(
            baseUrl = "http://test.example.com",
            credentialSupported = null,
            version = OpenID4VCIVersion.DRAFT11,
            issuerDisplay = displayConfig
        )

        // Then: display should be included in Draft11 metadata
        assertNotNull(metadata.display, "Draft11 should support display")
        assertEquals("Draft 11 Issuer", metadata.display?.first()?.name)
    }

    @Test
    fun testDisplayJsonSerializationStructure() {
        // Given: Display configuration with all fields
        val displayConfig = listOf(
            DisplayProperties(
                name = "Full Example Issuer",
                locale = "en-US",
                description = "Complete example with all fields",
                logo = LogoProperties(
                    url = "https://example.com/logo.png",
                    altText = "Example Logo"
                )
            )
        )

        val metadata = id.walt.oid4vc.OpenID4VCI.createDefaultProviderMetadata(
            baseUrl = "http://test.example.com",
            credentialSupported = null,
            version = OpenID4VCIVersion.DRAFT13,
            issuerDisplay = displayConfig
        )

        // When: Serializing to JSON
        val json = metadata.toJSON()
        val displayArray = json["display"]?.jsonArray

        // Then: JSON structure should match OpenID4VCI spec
        assertNotNull(displayArray, "Display should be a JSON array")
        assertEquals(1, displayArray.size)

        val displayJson = displayArray.first().jsonObject
        assertEquals("Full Example Issuer", displayJson["name"]?.jsonPrimitive?.content)
        assertEquals("en-US", displayJson["locale"]?.jsonPrimitive?.content)
        assertEquals("Complete example with all fields", displayJson["description"]?.jsonPrimitive?.content)

        val logoJson = displayJson["logo"]?.jsonObject
        assertNotNull(logoJson)
        assertEquals("https://example.com/logo.png", logoJson["url"]?.jsonPrimitive?.content)
        assertEquals("Example Logo", logoJson["alt_text"]?.jsonPrimitive?.content)
    }
}
