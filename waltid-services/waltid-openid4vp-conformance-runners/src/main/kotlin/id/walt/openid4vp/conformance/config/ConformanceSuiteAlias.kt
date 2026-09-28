package id.walt.openid4vp.conformance.config

import java.util.UUID

/**
 * OpenID conformance-suite `alias` values that stay unique across concurrent jobs.
 *
 * The hosted suite allows only one running test per alias. Wallet plans previously used a
 * fixed alias per variant, so two CI jobs sharing conformance.waltid.cloud interrupted each
 * other's modules (`INTERRUPTED` before WAITING). That is what turned a green PR matrix into
 * a red post-merge job when the last PR push and the `main` push overlapped.
 *
 * [GITHUB_RUN_ID](https://docs.github.com/en/actions/reference/workflows-and-actions/variables)
 * namespaces GitHub jobs; local runs fall back to a process-wide random suffix. Override with
 * `OPENID_CONFORMANCE_ALIAS_SUFFIX` when two local processes must share a known namespace.
 */
object ConformanceSuiteAlias {
    internal val runSuffix: String by lazy { resolveSuffix() }

    fun unique(stableName: String, suffix: String = runSuffix): String = "$stableName-$suffix"

    private fun resolveSuffix(): String {
        System.getenv("GITHUB_RUN_ID")?.trim()?.takeIf { it.isNotEmpty() }?.let { return sanitize(it) }
        System.getenv("OPENID_CONFORMANCE_ALIAS_SUFFIX")?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { return sanitize(it) }
        return sanitize(UUID.randomUUID().toString()).take(12)
    }

    private fun sanitize(value: String): String =
        value.replace(Regex("[^A-Za-z0-9_]"), "")
            .ifEmpty { "run" }
}
