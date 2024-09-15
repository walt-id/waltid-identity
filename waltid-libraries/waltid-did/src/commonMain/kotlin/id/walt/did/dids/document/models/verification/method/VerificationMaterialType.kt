package id.walt.did.dids.document.models.verification.method

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
@Serializable
enum class VerificationMaterialType {
    @SerialName("publicKeyJwk")
    PublicKeyJwk,

    @SerialName("publicKeyMultibase")
    PublicKeyMultibase;

    override fun toString(): String {
        return when (this) {
            PublicKeyJwk -> "publicKeyJwk"
            PublicKeyMultibase -> "publicKeyMultibase"
        }
    }
}
