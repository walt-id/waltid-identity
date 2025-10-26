package id.walt.policies.opa

import id.walt.policies.policies.DynamicPolicyConfig
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class DynamicPolicyConfigParser {
    fun parse(args: Any?): DynamicPolicyConfig {
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
}