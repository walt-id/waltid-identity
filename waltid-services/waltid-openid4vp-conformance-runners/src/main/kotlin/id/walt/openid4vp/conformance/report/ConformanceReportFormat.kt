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
