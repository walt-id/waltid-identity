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
    val skippableModules: Set<String> = emptySet()
)
