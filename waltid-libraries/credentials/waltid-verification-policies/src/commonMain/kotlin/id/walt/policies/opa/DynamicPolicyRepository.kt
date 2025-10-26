package id.walt.policies.opa

import id.walt.policies.DynamicPolicyException
import id.walt.policies.policies.DynamicPolicyConfig
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*

class DynamicPolicyRepository(
    private val client: HttpClient,
    private val regoCodeExtractor: DynamicPolicyRegoCodeExtractor,
) {
    private val logger = KotlinLogging.logger {}
    suspend fun upload(config: DynamicPolicyConfig): Result<Unit> {
        return try {
            logger.info { "Uploading policy to OPA server: ${config.policyName}" }
            val regoCode = regoCodeExtractor.extract(config)
            val response = client.put("${config.opaServer}/v1/policies/${config.policyName}") {
                contentType(ContentType.Text.Plain)
                setBody(regoCode)
            }
            if (!response.status.isSuccess()) {
                logger.error { "Failed to upload policy: ${response.status}" }
                Result.failure(DynamicPolicyException("Failed to upload policy: ${response.status}"))
            } else {
                Result.success(Unit)
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to upload policy" }
            Result.failure(DynamicPolicyException("Failed to upload policy: ${e.message}"))
        }
    }

    suspend fun delete(config: DynamicPolicyConfig) {
        try {
            logger.info { "Deleting policy from OPA server: ${config.policyName}" }
            client.delete("${config.opaServer}/v1/policies/${config.policyName}")
        } catch (e: Exception) {
            logger.error(e) { "Failed to delete policy" }
        }
    }
}