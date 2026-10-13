package id.walt.ktorauthnz.methods.storeddata

import id.walt.ktorauthnz.methods.RecoveryCode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Unused recovery codes of an account, as SHA-256 hex digests; each code works once. */
@Serializable
@SerialName("recovery-code")
data class RecoveryCodesStoredData(
    val codeDigests: List<String>,
) : AuthMethodStoredData {
    override fun authMethod() = RecoveryCode
}
