package id.walt.credentials.verification.models

import id.walt.credentials.verification.PolicyManager
import id.walt.credentials.verification.VerificationPolicy
import kotlinx.serialization.json.*
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
data class PolicyRequest(
    val policy: VerificationPolicy,
    val args: JsonElement? = null,
) {

    companion object {
        fun createFromJsonElement(jsonElement: JsonElement, errorMessage: (() -> String)? = null) =
            when (jsonElement) {
                is JsonPrimitive -> PolicyRequest(PolicyManager.getPolicy(jsonElement.content), null)
                is JsonObject ->
                    PolicyRequest(
                        policy = PolicyManager.getPolicy(
                            jsonElement["policy"]?.jsonPrimitive?.contentOrNull
                                ?: throw IllegalArgumentException(
                                    "No policy found in policy" +
                                            " definition${errorMessage?.let { " (${errorMessage.invoke()})" }}"
                                )
                        ),
                        args = jsonElement["args"],
                    )

                else -> throw IllegalArgumentException(
                    "Unknown policy definition type, please provide a policy" +
                            " name or policy definition object${errorMessage?.let { " (${errorMessage.invoke()})" }}"
                )
            }


        fun JsonArray.parsePolicyRequests(): List<PolicyRequest> {
            val policies = ArrayList<PolicyRequest>()

            forEachIndexed { idx, element ->
                policies.add(
                    createFromJsonElement(
                        jsonElement = element,
                    ) { "at index $idx, after policies: " + policies.joinToString { p -> p.policy.name } }
                )
            }

            return policies
        }
    }
}
