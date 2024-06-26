package id.walt.oid4vc.errors

import id.walt.oid4vc.requests.BatchCredentialRequest
import id.walt.oid4vc.requests.CredentialRequest
import id.walt.oid4vc.responses.BatchCredentialResponse
import id.walt.oid4vc.responses.CredentialErrorCode
import id.walt.oid4vc.responses.CredentialResponse
import kotlin.time.Duration

class CredentialError(
    credentialRequest: CredentialRequest?,
    val errorCode: CredentialErrorCode,
    val errorUri: String? = null, val cNonce: String? = null, val cNonceExpiresIn: Duration? = null,
    override val message: String? = null
) : Exception() {
    fun toCredentialErrorResponse() = CredentialResponse.error(errorCode, message, errorUri, cNonce, cNonceExpiresIn)
}

class DeferredCredentialError(
    val errorCode: CredentialErrorCode,
    val errorUri: String? = null,
    val cNonce: String? = null,
    val cNonceExpiresIn: Duration? = null,
    override val message: String? = null
) : Exception() {
    fun toCredentialErrorResponse() = CredentialResponse.error(errorCode, message, errorUri, cNonce, cNonceExpiresIn)
}

class BatchCredentialError(
    batchCredentialRequest: BatchCredentialRequest,
    val errorCode: CredentialErrorCode,
    val errorUri: String? = null,
    val cNonce: String? = null,
    val cNonceExpiresIn: Duration? = null,
    override val message: String? = null
) : Exception() {
    fun toBatchCredentialErrorResponse() =
        BatchCredentialResponse.error(errorCode, message, errorUri, cNonce, cNonceExpiresIn)
}
