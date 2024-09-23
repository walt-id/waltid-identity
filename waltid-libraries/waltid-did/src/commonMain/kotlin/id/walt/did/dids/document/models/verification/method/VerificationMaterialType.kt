package id.walt.did.dids.document.models.verification.method

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * Enumerated type representing verification material types that are registered in the [Decentralized Identifier Extensions](https://www.w3.org/TR/2024/NOTE-did-spec-registries-20240830/#verification-method-properties) parameter registry.
 * Deprecated values, i.e., publicKeyHex, publicKeyBase58 and ethereumAddress are not supported.
 * Support for blockchainAccountId is not provided yet.
 */
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
