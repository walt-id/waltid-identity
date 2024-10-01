package id.walt.policies.utils

import id.walt.policies.models.PolicyRequest
import id.walt.policies.models.PolicyRequest.Companion.parsePolicyRequests
import kotlinx.serialization.json.*

@JsExport
object PresentationVerificationUtils {
    fun generatePolicyRequest(jsonString: String): List<PolicyRequest> {
        return Json.parseToJsonElement(jsonString).jsonArray.parsePolicyRequests()
    }

    fun generateSpecificPolicyRequest(jsonString: String): Map<String, Any> {
        return Json.parseToJsonElement(jsonString).jsonObject.mapValues {
            it.value.jsonArray.parsePolicyRequests()
        }
    }
}
