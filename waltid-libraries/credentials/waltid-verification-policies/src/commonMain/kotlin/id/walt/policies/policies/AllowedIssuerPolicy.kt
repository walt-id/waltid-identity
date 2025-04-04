package id.walt.policies.policies

import id.walt.w3c.schemes.JwsSignatureScheme
import id.walt.w3c.utils.VCFormat
import id.walt.policies.CredentialWrapperValidatorPolicy
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import love.forte.plugin.suspendtrans.annotation.JsPromise
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
@Serializable
class AllowedIssuerPolicy : CredentialWrapperValidatorPolicy() {

    override val name = "allowed-issuer"
    override val description = "Checks that the issuer of the credential is present in the supplied list."
    override val supportedVCFormats = setOf(VCFormat.jwt_vc, VCFormat.jwt_vc_json, VCFormat.ldp_vc, VCFormat.sd_jwt_vc)

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun verify(data: JsonObject, args: Any?, context: Map<String, Any>): Result<Any> {
        val allowedIssuers = when (args) {
            is JsonPrimitive -> listOf(args.content)
            is JsonArray -> args.map { it.jsonPrimitive.content }
            else -> throw IllegalArgumentException("Invalid argument, please provide a single allowed issuer, or an list of allowed issuers.")
        }

        val issuer =
            data[JwsSignatureScheme.JwsOption.ISSUER]?.jsonPrimitive?.content
                ?: data["issuer"]?.jsonPrimitive?.content
                ?: throw IllegalArgumentException("No issuer found in credential: $data")

        return when (issuer) {
            in allowedIssuers -> Result.success(issuer)
            else -> Result.failure(
              id.walt.policies.NotAllowedIssuerException(
                issuer = issuer,
                allowedIssuers = allowedIssuers
              )
            )
        }
    }
}
