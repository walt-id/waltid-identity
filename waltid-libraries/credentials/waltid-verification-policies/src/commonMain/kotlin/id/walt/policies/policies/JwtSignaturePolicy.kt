package id.walt.policies.policies

import id.walt.credentials.schemes.JwsSignatureScheme
import id.walt.credentials.utils.VCFormat
import id.walt.policies.JwtVerificationPolicy
import id.walt.sdjwt.SDJwtVC
import kotlinx.serialization.Serializable
import love.forte.plugin.suspendtrans.annotation.JsPromise
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
@Serializable
class JwtSignaturePolicy : JwtVerificationPolicy(
) {
    override val name = "signature"
    override val description =
        "Checks a JWT credential by verifying its cryptographic signature using the key referenced by the DID in `iss`."
    override val supportedVCFormats = setOf(VCFormat.jwt_vc, VCFormat.jwt_vc_json, VCFormat.ldp_vc)

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun verify(credential: String, args: Any?, context: Map<String, Any>): Result<Any> {
        return JwsSignatureScheme().verify(credential)
    }
}
