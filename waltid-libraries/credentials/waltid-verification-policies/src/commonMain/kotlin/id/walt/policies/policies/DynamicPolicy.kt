package id.walt.policies.policies

import id.walt.credentials.utils.VCFormat
import id.walt.crypto.utils.JsonUtils.toJsonObject
import id.walt.policies.CredentialDataValidatorPolicy
import id.walt.policies.DynamicPolicyException
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


@Serializable
data class PolicyArgs(
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
        private val http = HttpClient {
            install(ContentNegotiation) {
                json()
            }
        }
    }

    fun cleanCode(input: String): String {
        // Replace \r\n with \n to normalize line endings
        val normalized = input.replace("\r\n", "\n")

        // Split the string into lines
        val lines = normalized.split("\n")

        // Remove any leading or trailing whitespace from each line
        val cleanedLines = lines.map { it.trim() }

        // Join the lines back together with proper line endings
        return cleanedLines.joinToString("\n")
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

        val rules = (args as JsonObject)["rules"]?.jsonObject
            ?: throw IllegalArgumentException("The 'rules' field is required.")
        val argument = (args)["argument"]?.jsonObject
            ?: throw IllegalArgumentException("The 'argument' field is required.")
        val policyName = (args)["policy_name"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("The 'policy_name' field is required.")
        val regoCode = rules["rego"]
            ?: return Result.failure(Exception("The 'rego' code is required in the 'rules' field."))


        println("regoCode: $regoCode")
        println("argument: $argument")


        val cleanedRegoCode = """
            ${
            cleanCode(regoCode.jsonPrimitive.content)
        }
        """.trimIndent()

        // upload the policy to OPA
        val upload: HttpResponse = http.put("http://localhost:8181/v1/policies/$policyName") {
            contentType(ContentType.Text.Plain)
            setBody(cleanedRegoCode)
        }

        println("upload: ${upload.bodyAsText()}")
        val input = mapOf(
            "parameter" to argument,
            "credentialData" to data.toMap()
        ).toJsonObject()


        println("input: $input")

        // verify the policy
        val response: HttpResponse = http.post("http://localhost:8181/v1/data/vc/verification/$policyName") {
            contentType(ContentType.Application.Json)
            setBody(mapOf("input" to input))
        }

        println("response: ${response.bodyAsText()}")



        val result = response.body<JsonObject>()["result"]?.jsonObject
            ?: throw IllegalArgumentException("The response does not contain a 'result' field.")

        println("result: $result")


        // check if the policy is passed
        val allow = result["allow"]
        println("allow: $allow")


        // delete the policy from OPA
        http.delete("http://localhost:8181/v1/policies/$policyName")
        return if (allow is JsonPrimitive && allow.booleanOrNull == true) {
            Result.success(result)
        } else {
            Result.failure(
                DynamicPolicyException(
                    message = "The policy condition was not met for policy ${policyName}."
                )
            )
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