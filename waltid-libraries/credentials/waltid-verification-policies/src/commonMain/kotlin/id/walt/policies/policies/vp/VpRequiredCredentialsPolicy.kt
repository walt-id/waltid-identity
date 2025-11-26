package id.walt.policies.policies.vp

import id.walt.policies.CredentialWrapperValidatorPolicy
import id.walt.sdjwt.SDJwt
import id.walt.w3c.utils.VCFormat
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import love.forte.plugin.suspendtrans.annotation.JsPromise
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
@Serializable
class VpRequiredCredentialsPolicy : CredentialWrapperValidatorPolicy() {

    override val name: String = "vp_required_credentials"
    override val description: String =
        "Verifies that a Verifiable Presentation includes a set of required credential types (e.g., bankId and any of a group of diplomas)"
    override val supportedVCFormats = setOf(VCFormat.jwt_vp, VCFormat.jwt_vp_json)

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun verify(data: JsonObject, args: Any?, context: Map<String, Any>): Result<Any> {
        require(args is JsonObject) { "vp_required_credentials: missing or invalid args (expected JSON object)" }

        val requirements = args["required"]?.jsonArray
        require(requirements is JsonArray) { "vp_required_credentials: 'required' array missing in args" }

        val presentedTypes: List<String> = collectPresentedTypes(data)

        val missingMessages = mutableListOf<String>()

        requirements.forEach { reqEl ->
            val req = reqEl.jsonObject

            when {
                req.containsKey("credential_type") -> {
                    val credentialType = req["credential_type"]?.jsonPrimitive?.content
                    if (credentialType.isNullOrBlank()) {
                        return Result.failure(IllegalArgumentException("vp_required_credentials: 'credential_type' must be non-empty string"))
                    }
                    val found = presentedTypes.any { it.equals(credentialType, ignoreCase = false) }
                    if (!found) missingMessages.add("Missing required credential: $credentialType")
                }

                req.containsKey("any_of") -> {
                    val anyOf = req["any_of"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }
                        ?.filter { it.isNotBlank() }
                        ?: emptyList()
                    if (anyOf.isEmpty()) {
                        return@forEach
                    }
                    val foundAny = anyOf.any { t -> presentedTypes.any { it == t } }
                    if (!foundAny) missingMessages.add("Missing required credential: ${anyOf.joinToString(" OR ")}")
                }

                else -> {
                    return Result.failure(IllegalArgumentException("vp_required_credentials: unsupported requirement entry, expected 'credential_type' or 'any_of'"))
                }
            }
        }

        return if (missingMessages.isEmpty()) {
            Result.success(
                JsonObject(
                    mapOf(
                        "presented_types" to JsonArray(presentedTypes.map { JsonPrimitive(it) })
                    )
                )
            )
        } else {
            Result.failure(IllegalArgumentException(missingMessages.joinToString("; ")))
        }
    }

    private fun collectPresentedTypes(data: JsonObject): List<String> {
        val vcArray = data["vp"]?.jsonObject?.get("verifiableCredential")?.jsonArray
        if (vcArray != null) {
            return vcArray.mapNotNull { vcEl ->
                val sdJwtStr = vcEl.jsonPrimitive.contentOrNull ?: return@mapNotNull null
                runCatching { SDJwt.parse(sdJwtStr) }.getOrNull()?.fullPayload?.let { payload ->
                    payload["vct"]?.jsonPrimitive?.contentOrNull
                        ?: payload["vc"]?.jsonObject?.get("type")?.jsonArray?.lastOrNull()?.jsonPrimitive?.contentOrNull
                }
            }
        }

        data["vct"]?.jsonPrimitive?.contentOrNull?.let { return listOf(it) }
        data["vc"]?.jsonObject?.get("type")?.jsonArray?.lastOrNull()?.jsonPrimitive?.contentOrNull?.let {
            return listOf(
                it
            )
        }

        return emptyList()
    }
}
