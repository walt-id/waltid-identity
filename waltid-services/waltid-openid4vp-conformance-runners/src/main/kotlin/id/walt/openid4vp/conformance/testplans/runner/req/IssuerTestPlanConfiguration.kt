package id.walt.openid4vp.conformance.testplans.runner.req

import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Configuration for OpenID4VCI Issuer test plans.
 * 
 * Used to configure the conformance suite to test an Issuer implementation.
 * The conformance suite acts as a wallet testing the issuer.
 */
@Serializable
data class IssuerTestPlanConfiguration(
    /**
     * Parameters to append to the test plan creation URL.
     * Should include planName and variant.
     */
    val testPlanCreationUrl: ParametersBuilder.() -> Unit,
    
    /**
     * JSON configuration for the test plan.
     * Includes issuer URL, client credentials, etc.
     */
    val testPlanCreationConfiguration: JsonObject,

    /**
     * Variant JSON string shared by all test modules in this plan.
     */
    val moduleVariant: String = "",

    /**
     * The URL of the issuer being tested.
     */
    val issuerUrl: String,

    /**
     * Modules that may legitimately return SKIPPED instead of PASSED.
     * Used for optional suite checks like signed metadata.
     */
    val skippableModules: Set<String> = emptySet(),

    /**
     * Whether this test plan requires a pre-authorized credential offer.
     */
    val requiresPreAuthorizedOffer: Boolean = false,

    /**
     * Profile ID to use when creating credential offers.
     */
    val credentialProfileId: String = ""
) {
    /**
     * Create a new configuration with a credential offer URI added.
     */
    fun withCredentialOffer(credentialOfferUri: String): JsonObject {
        val mutableConfig = testPlanCreationConfiguration.toMutableMap()
        
        // Add credential_offer_uri to the vci configuration
        val vciConfig = (mutableConfig["vci"] as? JsonObject)?.toMutableMap() ?: mutableMapOf()
        vciConfig["credential_offer_uri"] = kotlinx.serialization.json.JsonPrimitive(credentialOfferUri)
        mutableConfig["vci"] = kotlinx.serialization.json.JsonObject(vciConfig)
        
        return kotlinx.serialization.json.JsonObject(mutableConfig)
    }
}
