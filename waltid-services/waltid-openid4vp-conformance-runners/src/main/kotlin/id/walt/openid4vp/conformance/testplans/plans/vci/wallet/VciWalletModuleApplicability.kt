package id.walt.openid4vp.conformance.testplans.plans.vci.wallet

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Harness-side skips for OpenID4VCI wallet modules the suite publishes but this wallet cannot run.
 *
 * Kept next to [id.walt.openid4vp.conformance.testplans.plans.vp.wallet.WalletModuleApplicability]
 * rather than inlined in the runner so the reasons stay testable and the skip table stays honest.
 */
object VciWalletModuleApplicability {

    /**
     * Module-name prefix of the FAPI 2.0 client tests the HAIP plan bundles.
     *
     * Those modules exercise the wallet as an OAuth client rather than as a credential recipient:
     * the suite is only an authorization server, publishes no credential offer, and waits for the
     * client to start an authorization request by itself. Driving them needs wallet-initiated
     * issuance, which this harness has no entry point for.
     */
    const val FAPI2_CLIENT_MODULE_PREFIX = "fapi2-security-profile-final-client-test-"

    const val FAPI2_SKIP_REASON =
        "FAPI 2.0 client test: needs wallet-initiated issuance, which the harness cannot yet trigger"

    const val DEFERRED_SKIP_REASON = "Deferred credential issuance is not supported"

    /**
     * Why [testModule] should not be run for [variant], or `null` if it should.
     */
    fun skipReason(testModule: String, variant: JsonObject): String? {
        if (testModule.startsWith(FAPI2_CLIENT_MODULE_PREFIX)) return FAPI2_SKIP_REASON
        val issuanceMode = (variant["vci_credential_issuance_mode"] as? JsonPrimitive)?.contentOrNull
        if (issuanceMode == "deferred") return DEFERRED_SKIP_REASON
        return null
    }
}
