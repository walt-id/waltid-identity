package id.walt.did.utils

import id.walt.crypto.utils.JsonUtils.toJsonElement
import kotlinx.serialization.json.*
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@Suppress("NON_EXPORTABLE_TYPE")
@OptIn(ExperimentalJsExport::class)
@JsExport
object VerificationMaterial {
    private val verificationMethods = arrayOf(
        "verificationMethod",
        "assertionMethod",
        "capabilityInvocation",
        "capabilityDelegation",
        "keyAgreement",
        "authentication",
    )

    fun get(document: JsonObject): JsonElement? = verificationMethods.firstOrNull {
        document.jsonObject.keys.contains(it)
    }?.let {
        val element = document.jsonObject[it]
        val verificationMethod = extractVerificationMethod(element)
        extractVerificationMaterial(verificationMethod)
    }

    private fun extractVerificationMethod(element: JsonElement?): JsonElement = when (element) {
        is JsonArray -> element.jsonArray.first()
        is JsonObject -> element
        else -> throw IllegalArgumentException("Illegal verification method type")
    }

    private fun extractVerificationMaterial(element: JsonElement): JsonElement = when (element) {
        is JsonObject -> element.jsonObject
        is JsonPrimitive -> element.jsonPrimitive.toJsonElement()
        else -> throw IllegalArgumentException("Illegal verification material type")
    }
}
