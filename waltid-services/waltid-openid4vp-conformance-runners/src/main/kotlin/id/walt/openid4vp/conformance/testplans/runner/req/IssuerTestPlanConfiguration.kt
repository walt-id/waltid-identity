package id.walt.openid4vp.conformance.testplans.runner.req

import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@Serializable
enum class CredentialOfferAuthMethod {
    PRE_AUTHORIZED,
    AUTHORIZED,
}

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
     * The URL of the issuer being tested.
     */
    val issuerUrl: String,

    /**
     * Modules that may legitimately return SKIPPED instead of PASSED.
     * Used for optional suite checks like signed metadata.
     */
    val skippableModules: Set<String> = emptySet(),

    /**
     * Authentication method for issuer-initiated credential offers, or null when
     * the wallet starts without an offer.
     */
    val credentialOfferAuthMethod: CredentialOfferAuthMethod? = null,

    /**
     * Profile ID to use when creating credential offers.
     */
    val credentialProfileId: String = "",

    /**
     * Static transaction code to give to the conformance suite and issuer2 for pre-authorized tests.
     */
    val staticTxCode: String? = null,
) {
    /** Create the suite configuration with the configured static tx_code. */
    fun withStaticTxCode(): JsonObject {
        val txCode = staticTxCode?.takeIf { it.isNotBlank() } ?: return testPlanCreationConfiguration
        val mutableConfig = testPlanCreationConfiguration.toMutableMap()
        val vciConfig = (mutableConfig["vci"] as? JsonObject)?.toMutableMap() ?: mutableMapOf()
        vciConfig["static_tx_code"] = JsonPrimitive(txCode)
        mutableConfig["vci"] = kotlinx.serialization.json.JsonObject(vciConfig)

        return kotlinx.serialization.json.JsonObject(mutableConfig)
    }
}
