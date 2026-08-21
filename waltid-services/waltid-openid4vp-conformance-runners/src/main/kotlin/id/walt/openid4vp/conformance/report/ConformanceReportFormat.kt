package id.walt.openid4vp.conformance.report

/**
 * Compact labels for GitHub Actions job-summary rows.
 *
 * Wallet runners record `producerId/module`, and [producerId] is the suite plan plus every
 * variant axis as `k=v`. That string is unique (needed when merging reports) but too wide for a
 * Markdown table. Display uses the module suffix and the variant values only.
 */
data class ConformanceDisplayName(
    val title: String,
    val variant: String? = null,
    val plan: String? = null,
    val full: String,
)

object ConformanceReportFormat {

    private val modulePrefixes = listOf(
        "oid4vp-1final-wallet-",
        "oid4vp-1final-verifier-",
        "oid4vci-1_0-wallet-test-",
        "oid4vci-1_0-wallet-",
        "oid4vci-1_0-issuer-",
        "fapi2-security-profile-final-client-test-",
        "oid4vp-1final-",
        "oid4vci-1_0-",
    )

    private val planPrefixes = listOf("oid4vp-1final-", "oid4vci-1_0-")

    private val variantKeyOrder = listOf(
        "credential_format",
        "client_id_prefix",
        "request_method",
        "response_mode",
        "vp_profile",
        "vci_grant_type",
        "client_auth_type",
        "sender_constrain",
        "fapi_profile",
        "authorization_request_type",
        "vci_authorization_code_flow_variant",
        "vci_credential_encryption",
        "vci_credential_issuance_mode",
        "vci_credential_offer_variant",
        "fapi_request_method",
    )

    fun displayName(raw: String): ConformanceDisplayName {
        val parts = raw.split('/').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) return ConformanceDisplayName(title = raw, full = raw)

        val moduleRaw = parts.last()
        val prefixParts = parts.dropLast(1)
        val variantRaw = prefixParts.lastOrNull { it.contains('=') }
        val planRaw = prefixParts.firstOrNull { it != variantRaw }

        val variant = variantRaw?.let(::compactVariant)

        return ConformanceDisplayName(
            title = shortenModule(moduleRaw),
            variant = variant,
            plan = planRaw?.let(::shortenPlan),
            full = raw,
        )
    }

    /**
     * How to act on a failed or skipped row. Passed rows have nothing to fix.
     *
     * Guidance is keyed off harness skip reasons and common setup failures. Suite assertion
     * failures still need the log: inventing a wallet patch from `FAILED` alone would be wrong.
     */
    fun remediation(status: String, error: String?, name: String = ""): String? {
        if (status == "passed") return null
        val detail = error.orEmpty()
        val blob = "$status $detail $name".lowercase()
        val skippedDetail = detail.lowercase()

        return when {
            status == "skipped" && skippedDetail.contains("not applicable to this variant") ->
                "Expected skip. The suite published a module its own applicability rules exclude " +
                    "for this variant. No wallet change unless you need that module to apply here."

            status == "skipped" && skippedDetail.contains("url_query") &&
                skippedDetail.contains("continueafterrequesturicalled") ->
                "These negative modules only collect error-page evidence after a request_uri fetch. " +
                    "Drive them with request_uri_signed or request_uri_unsigned, or leave skipped for url_query."

            status == "skipped" && skippedDetail.contains("wallet-initiated issuance") ->
                "Add a harness entry point that starts wallet-initiated OpenID4VCI, or stop " +
                    "registering the FAPI 2.0 client modules on the HAIP wallet plan."

            status == "skipped" && skippedDetail.contains("suite skipped this module") ->
                "The suite skipped an optional module, usually because the wallet did not advertise " +
                    "the feature. Implement that option or keep the skip."

            status == "skipped" && skippedDetail.contains("not available") ->
                "Start the OpenID conformance suite and confirm CI can reach it " +
                    "(`CONFORMANCE_HOST` / `CONFORMANCE_PORT`) plus the adapter tunnels on 7006 and 7007."

            status == "skipped" ->
                "This module was not run: ${detail.ifBlank { "no skip reason recorded" }}. " +
                    "Address that precondition, or drop the module from this plan."

            status == "timeout" || detail.contains("did not complete within") ->
                "The suite never finished. Confirm the wallet adapter stayed reachable " +
                    "(CI: Cloudflare tunnels on 7006/7007 and `CONFORMANCE_VP_WALLET_ADAPTER_URL` / " +
                    "`CONFORMANCE_VCI_WALLET_ADAPTER_BASE_URL`) and that the wallet did not hang " +
                    "waiting for a user or redirect."

            blob.contains("tokenendpoint") || blob.contains("token endpoint") ->
                "Authorization-code VCI still fails while resolving the token endpoint. Complete " +
                    "adapter/wallet token-endpoint discovery for this grant, or use the " +
                    "pre-authorized-code plan until that works."

            blob.contains("audience") ->
                "The presented token audience did not match what the suite expected. Align `aud` " +
                    "with the verifier client identifier (or origin) for this client_id_prefix."

            blob.contains("request_object_json") || blob.contains("couldn't find required object") ->
                "The suite expected a request object that this variant never produces. Treat as an " +
                    "applicability skip, or only run this module with request_uri_*."

            status == "error" && (
                blob.contains("connection") ||
                    blob.contains("failed to connect") ||
                    blob.contains("unknown host") ||
                    blob.contains("cloudflare") ||
                    blob.contains("tunnel")
                ) ->
                "The suite could not reach the adapter. Check `CONFORMANCE_HOST`, the adapter URL " +
                    "overrides, and that cloudflared is still running."

            status == "error" ->
                "The harness aborted before the suite returned a result" +
                    (detail.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ".") +
                    " Fix that setup error, then re-run this module."

            else ->
                "Open the suite log, inspect the first failed assertion, and change the " +
                    "wallet/verifier behavior it names. Soft-fail is on unless " +
                    "`CONFORMANCE_ALLOW_FAILURE=false`."
        }
    }

    private fun compactVariant(variantRaw: String): String? {
        val pairs = variantRaw.split(',').mapNotNull { piece ->
            val key = piece.substringBefore('=', missingDelimiterValue = "").trim()
            val value = piece.substringAfter('=', missingDelimiterValue = piece).trim()
            if (value.isEmpty()) null else key to value
        }
        if (pairs.isEmpty()) return null
        return pairs
            .sortedWith(
                compareBy<Pair<String, String>> { (key, _) ->
                    val index = variantKeyOrder.indexOf(key)
                    if (index < 0) variantKeyOrder.size else index
                }.thenBy { it.first }
            )
            .joinToString(" · ") { it.second }
            .ifBlank { null }
    }

    private fun shortenModule(module: String): String {
        val shortened = modulePrefixes.firstOrNull { module.startsWith(it) }
            ?.let { module.removePrefix(it) }
            ?: module
        return shortened.ifBlank { module }
    }

    private fun shortenPlan(plan: String): String {
        var shortened = plan
        planPrefixes.forEach { prefix ->
            if (shortened.startsWith(prefix)) shortened = shortened.removePrefix(prefix)
        }
        shortened = shortened.removeSuffix("-test-plan")
        return shortened.ifBlank { plan }
    }
}
