package id.walt.credentials.verification.policies

import id.walt.credentials.schemes.JwsSignatureScheme.JwsOption
import id.walt.credentials.verification.CredentialWrapperValidatorPolicy
import id.walt.credentials.verification.NotAllowedIssuerException
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
            data[JwsOption.ISSUER]?.jsonPrimitive?.content
                ?: data["issuer"]?.jsonPrimitive?.content
                ?: throw IllegalArgumentException("No issuer found in credential: $data")

        return when (issuer) {
            in allowedIssuers -> Result.success(issuer)
            else -> Result.failure(
                NotAllowedIssuerException(
                    issuer = issuer,
                    allowedIssuers = allowedIssuers
                )
            )
        }
    }
}
