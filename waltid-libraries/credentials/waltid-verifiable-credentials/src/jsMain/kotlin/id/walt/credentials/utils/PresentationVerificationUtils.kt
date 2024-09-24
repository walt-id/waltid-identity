package id.walt.credentials.utils

import id.walt.credentials.verification.models.PolicyRequest
import id.walt.credentials.verification.models.PolicyRequest.Companion.parsePolicyRequests
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

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
