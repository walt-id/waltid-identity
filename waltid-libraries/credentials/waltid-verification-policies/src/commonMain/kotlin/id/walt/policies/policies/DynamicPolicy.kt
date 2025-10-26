package id.walt.policies.policies

import id.walt.crypto.utils.JsonUtils.toJsonObject
import id.walt.policies.CredentialDataValidatorPolicy
import id.walt.policies.DynamicPolicyException
import id.walt.policies.opa.DynamicPolicyConfigParser
import id.walt.policies.opa.DynamicPolicyRegoCodeExtractor
import id.walt.policies.opa.DynamicPolicyRepository
import id.walt.policies.opa.DynamicPolicyValidator
import id.walt.sdjwt.SDJwt
import id.walt.w3c.utils.VCFormat
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
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
    override val supportedVCFormats = setOf(VCFormat.jwt_vc, VCFormat.jwt_vc_json, VCFormat.ldp_vc) +
            setOf(VCFormat.jwt_vp, VCFormat.jwt_vp_json, VCFormat.ldp_vp) // todo: ha, this makes it a hybrid policy 🤣

    companion object {
        private val http = HttpClient {
            install(ContentNegotiation) {
                json()
            }
        }
        private val configParser = DynamicPolicyConfigParser()
        private val regoCodeExtractor = DynamicPolicyRegoCodeExtractor(client = http)
        private val validator = DynamicPolicyValidator(regoCodeExtractor = regoCodeExtractor)
        private val repository = DynamicPolicyRepository(client = http, regoCodeExtractor = regoCodeExtractor)
    }

    private suspend fun verifyPolicy(
        config: DynamicPolicyConfig,
        input: JsonElement,
    ): Result<JsonObject> {
        return try {
            logger.info { "Verifying policy: ${config.policyName}" }

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

    private fun getCredentials(presentation: JsonObject): List<JsonObject> {
        // todo: does this cover the regular JWTs as well??
        val tokenParserFunction: (String) -> SDJwt = { SDJwt.parse(it) }
        val credentials = presentation["vp"]?.jsonObject?.get("verifiableCredential")?.jsonArray?.mapNotNull { vc ->
            vc.jsonPrimitive.contentOrNull?.let { tokenParserFunction(it) }?.fullPayload
        } ?: emptyList()
        return credentials
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
            val config = configParser.parse(args)
            validator.validate(config)
            repository.upload(config).getOrThrow()
            val input = computeInput(config, data)
            val result = verifyPolicy(config = config, input = input)
            processResult(result, config)
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
                val config = configParser.parse(args)
                repository.delete(config)
            }
        }
    }

    private fun computeInput(
        config: DynamicPolicyConfig,
        data: JsonObject,
    ) = // 🙈
        let {
            data.takeIf { isVerifiablePresentation(it) }?.toMap() ?: let {
                getCredentials(data).map { it.toMap() }
            }
        }.let {
            mapOf(
                "parameter" to config.argument,
                "credentialData" to it // todo: avoid passing [Any] (here [it] is [Any])
            ).toJsonObject()
        }


    // 🙈
    private fun isVerifiablePresentation(data: JsonObject) = data.containsKey("vp")

    private fun processResult(
        result: Result<JsonObject>,
        config: DynamicPolicyConfig
    ): Result<JsonObject> = result.map { result ->
        val decision = result.values.firstOrNull {
            it is JsonPrimitive && it.booleanOrNull == true
        }
        if (decision != null) {
            result
        } else {
            throw DynamicPolicyException("The policy condition was not met for policy ${config.policyName}")
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
