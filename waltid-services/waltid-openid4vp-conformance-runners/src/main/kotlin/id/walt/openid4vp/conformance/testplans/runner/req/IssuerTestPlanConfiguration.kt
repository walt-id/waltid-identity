package id.walt.openid4vp.conformance.testplans.runner.req

import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

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
     * Whether this test plan requires issuer-initiated credential offers.
     */
    val requiresPreAuthorizedOffer: Boolean = false,

    /**
     * Profile ID to use when creating credential offers.
     */
    val credentialProfileId: String = "",

    /**
     * Static transaction code to give to the conformance suite and issuer2 for pre-authorized tests.
     */
    val staticTxCode: String? = null,
) {
    /**
     * Create a new configuration with the configured static tx_code added.
     */
    fun withStaticTxCode(): JsonObject = withVciConfiguration()

    /**
     * Create a new configuration with a credential offer URI added.
     */
    fun withCredentialOffer(credentialOfferUri: String): JsonObject =
        withVciConfiguration("credential_offer_uri" to credentialOfferUri)

    private fun withVciConfiguration(vararg values: Pair<String, String>): JsonObject {
        val mutableConfig = testPlanCreationConfiguration.toMutableMap()

        val vciConfig = (mutableConfig["vci"] as? JsonObject)?.toMutableMap() ?: mutableMapOf()
        values.forEach { (name, value) ->
            vciConfig[name] = JsonPrimitive(value)
        }
        staticTxCode?.takeIf { it.isNotBlank() }?.let {
            vciConfig["static_tx_code"] = JsonPrimitive(it)
        }
        mutableConfig["vci"] = kotlinx.serialization.json.JsonObject(vciConfig)

        return kotlinx.serialization.json.JsonObject(mutableConfig)
    }
}
