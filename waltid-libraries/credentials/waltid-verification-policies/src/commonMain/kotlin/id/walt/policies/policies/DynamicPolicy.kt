package id.walt.policies.policies

import id.walt.crypto.utils.JsonUtils.toJsonObject
import id.walt.policies.CredentialDataValidatorPolicy
import id.walt.policies.DynamicPolicyException
import id.walt.w3c.utils.VCFormat
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import love.forte.plugin.suspendtrans.annotation.JsPromise
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport


private val logger = KotlinLogging.logger {}
@Serializable
data class DynamicPolicyConfig(
    val opaServer: String = "http://localhost:8181",
    val policyQuery: String = "vc/verification",
    val policyName: String,
    val rules: Map<String, String>,
    val argument: Map<String, String>
)


@OptIn(ExperimentalJsExport::class)
@JsExport
@Serializable
class DynamicPolicy : CredentialDataValidatorPolicy() {

    override val name = "dynamic"
    override val description =
        "A dynamic policy that can be used to implement custom verification logic."
    override val supportedVCFormats = setOf(VCFormat.jwt_vc, VCFormat.jwt_vc_json, VCFormat.ldp_vc)

    companion object {
        private const val MAX_REGO_CODE_SIZE = 1_000_000 // 1MB limit
        private const val MAX_POLICY_NAME_LENGTH = 64

        private val http = HttpClient {
            install(ContentNegotiation) {
                json()
            }
        }
    }

    private fun cleanCode(input: String): String {
        return input.replace("\r\n", "\n")
            .split("\n").joinToString("\n") { it.trim() }
    }

    private fun validatePolicyName(policyName: String) {
        require(policyName.matches(Regex("^[a-zA-Z]+$"))) {
            "Policy name contains invalid characters."
        }
        require(policyName.length <= MAX_POLICY_NAME_LENGTH) {
            "Policy name exceeds maximum length of $MAX_POLICY_NAME_LENGTH characters"
        }
    }

    private fun validateRegoCode(regoCode: String) {
        require(regoCode.isNotEmpty()) {
            "Rego code cannot be empty"
        }
        require(regoCode.length <= MAX_REGO_CODE_SIZE) {
            "Rego code exceeds maximum allowed size of $MAX_REGO_CODE_SIZE bytes"
        }
    }


    private fun parseConfig(args: Any?): DynamicPolicyConfig {
        require(args is JsonObject) { "Args must be a JsonObject" }

        val rules = args["rules"]?.jsonObject
            ?: throw IllegalArgumentException("The 'rules' field is required.")
        val policyName = args["policy_name"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("The 'policy_name' field is required.")
        val argument = args["argument"]?.jsonObject
            ?: throw IllegalArgumentException("The 'argument' field is required.")

        return DynamicPolicyConfig(
            opaServer = args["opa_server"]?.jsonPrimitive?.content ?: "http://localhost:8181",
            policyQuery = args["policy_query"]?.jsonPrimitive?.content ?: "vc/verification",
            policyName = policyName,
            rules = rules.mapValues { it.value.jsonPrimitive.content },
            argument = argument.mapValues { it.value.jsonPrimitive.content }
        )
    }


    private suspend fun getRegoCode(config: DynamicPolicyConfig): String {
        val regoCode = config.rules["rego"]
        val policyUrl = config.rules["policy_url"]

        return when {
            policyUrl != null -> {
                logger.info { "Fetching rego code from URL: $policyUrl" }
                try {
                    val response = http.get(policyUrl)
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


    private suspend fun uploadPolicy(opaServer: String, policyName: String, regoCode: String): Result<Unit> {
        return try {
            logger.info { "Uploading policy to OPA server: $policyName" }
            val response = http.put("$opaServer/v1/policies/$policyName") {
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

    private suspend fun deletePolicy(opaServer: String, policyName: String) {
        try {
            logger.info { "Deleting policy from OPA server: $policyName" }
            http.delete("$opaServer/v1/policies/$policyName")
        } catch (e: Exception) {
            logger.error(e) { "Failed to delete policy" }
        }
    }


    private suspend fun verifyPolicy(
        config: DynamicPolicyConfig,
        data: JsonObject
    ): Result<JsonObject> {
        return try {
            logger.info { "Verifying policy: ${config.policyName}" }
            val input = mapOf(
                "parameter" to config.argument,
                "credentialData" to data.toMap()
            ).toJsonObject()

            val response = http.post("${config.opaServer}/v1/data/${config.policyQuery}/${config.policyName}") {
                contentType(ContentType.Application.Json)
                setBody(mapOf("input" to input))
            }

            val result = response.body<JsonObject>()["result"]?.jsonObject
                ?: throw DynamicPolicyException("Invalid response from OPA server")

            Result.success(result)
        } catch (e: Exception) {
            logger.error(e) { "Policy verification failed" }
            Result.failure(DynamicPolicyException("Policy verification failed: ${e.message}"))
        }
    }

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun verify(
        data: JsonObject,
        args: Any?,
        context: Map<String, Any>
    ): Result<Any> {

        return try {
            logger.info { "Starting policy verification process" }
            val config = parseConfig(args)
            validatePolicyName(config.policyName)

            val regoCode = getRegoCode(config)
            validateRegoCode(regoCode)

            uploadPolicy(config.opaServer, config.policyName, regoCode).getOrThrow()

            verifyPolicy(config, data).map { result ->

                val decision = result.values.firstOrNull {
                    it is JsonPrimitive && it.booleanOrNull == true
                }
                if (decision != null) {
                    result
                } else {
                    throw DynamicPolicyException("The policy condition was not met for policy ${config.policyName}")
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Policy verification failed" }
            Result.failure(
                when (e) {
                    is DynamicPolicyException -> e
                    else -> DynamicPolicyException("Policy verification failed: ${e.message}")
                }
            )
        } finally {
            runCatching {
                val config = parseConfig(args)
                deletePolicy(config.opaServer, config.policyName)
            }
        }
    }


    // Helper function to convert JsonObject to Map
    private fun JsonObject.toMap(): Map<String, Any> {
        return this.mapValues { (_, value) ->
            when (value) {
                is JsonPrimitive -> value.content
                is JsonObject -> value.toMap()
                else -> value
            }
        }
    }


}
