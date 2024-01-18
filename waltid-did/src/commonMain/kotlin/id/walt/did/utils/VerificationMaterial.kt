package id.walt.did.utils

import id.walt.crypto.utils.JsonUtils.toJsonElement
import kotlinx.serialization.json.*

object VerificationMaterial {
    private val verificationMethods = arrayOf(
        "verificationMethod",
        "assertionMethod",
        "capabilityInvocation",
        "capabilityDelegation",
        "keyAgreement",
        "authentication",
    )

    fun get(document: JsonObject): JsonElement {
        val verificationMethod = verificationMethods.first {
            document.jsonObject.keys.contains(it)
        }
        val element = document.jsonObject[verificationMethod]
        val verificationMaterial = extractVerificationMethod(element)
        val content = extractVerificationMaterial(verificationMaterial)
        return content
    }

    private fun extractVerificationMaterial(element: JsonElement): JsonElement = when (element) {
        is JsonObject -> element.jsonObject
        is JsonPrimitive -> element.jsonPrimitive.toJsonElement()
        else -> throw IllegalArgumentException("Illegal verification material type")
    }

    private fun extractVerificationMethod(element: JsonElement?): JsonElement = when (element) {
        is JsonArray -> element.jsonArray.first()
        is JsonObject -> element
        else -> throw IllegalArgumentException("Illegal verification method type")
    }
}