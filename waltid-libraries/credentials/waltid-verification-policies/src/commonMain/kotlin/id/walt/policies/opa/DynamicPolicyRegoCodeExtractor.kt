package id.walt.policies.opa

import id.walt.policies.DynamicPolicyException
import id.walt.policies.policies.DynamicPolicyConfig
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*

class DynamicPolicyRegoCodeExtractor(
    private val client: HttpClient,
) {
    private val logger = KotlinLogging.logger {}

    suspend fun extract(config: DynamicPolicyConfig): String {
        val regoCode = config.rules["rego"]
        val policyUrl = config.rules["policy_url"]

        return when {
            policyUrl != null -> {
                logger.info { "Fetching rego code from URL: $policyUrl" }
                try {
                    val response = client.get(policyUrl)
                    cleanCode(response.bodyAsText())
                } catch (e: Exception) {
                    logger.error(e) { "Failed to fetch rego code from URL: $policyUrl" }
                    throw DynamicPolicyException("Failed to fetch rego code: ${e.message}")
                }
            }

            regoCode != null -> cleanCode(regoCode)
            else -> throw IllegalArgumentException("Either 'rego' or 'policy_url' must be provided in rules")
        }
    }

    private fun cleanCode(input: String): String {
        return input.replace("\r\n", "\n")
            .split("\n").joinToString("\n") { it.trim() }
    }
}