package id.walt.policies.policies.vp

import id.walt.credentials.schemes.JwsSignatureScheme.JwsOption
import id.walt.credentials.utils.VCFormat
import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.policies.CredentialWrapperValidatorPolicy
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import love.forte.plugin.suspendtrans.annotation.JsPromise
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
@Serializable
class HolderBindingPolicy : CredentialWrapperValidatorPolicy(
) {

    override val name = "holder-binding"
    override val description =
        "Verifies that issuer of the Verifiable Presentation (presenter) is also the subject of all Verifiable Credentials contained within."
    override val supportedVCFormats = setOf(VCFormat.jwt_vp, VCFormat.jwt_vp_json)

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun verify(data: JsonObject, args: Any?, context: Map<String, Any>): Result<Any> {
        val presenterDid = data[JwsOption.ISSUER]!!.jsonPrimitive.content

        val vp = data["vp"]?.jsonObject ?: throw IllegalArgumentException("No \"vp\" field in VP!")

        val credentials =
            vp["verifiableCredential"]?.jsonArray ?: throw IllegalArgumentException("No \"verifiableCredential\" field in \"vp\"!")

        val credentialSubjects = credentials.map {
            it.jsonPrimitive.content.decodeJws().payload["sub"]!!.jsonPrimitive.content.split("#").first()
        }

        return when {
            credentialSubjects.all { it == presenterDid } -> Result.success(presenterDid)
            else -> Result.failure(
              id.walt.policies.HolderBindingException(
                presenterDid = presenterDid,
                credentialDids = credentialSubjects
              )
            )
        }
    }
}
