package id.walt.oid4vc.interfaces

import id.walt.oid4vc.data.CredentialFormat
import id.walt.oid4vc.errors.CredentialError
import id.walt.oid4vc.errors.DeferredCredentialError
import id.walt.oid4vc.requests.CredentialRequest
import kotlinx.serialization.json.JsonElement

interface ICredentialProvider {
    /**
     * Generates the credential according to the given [credentialRequest], defining _format_, _type_ and requested _subject claims_,
     * signed with the appropriate _issuer key_ and bound to the _holder key_ given in the _proof of possession_ object.
     * If the credential cannot be generated due to an error or missing/invalid input data, a [CredentialError] exception is thrown.
     * If credential generation is deferred (asynchronous issuance), [CredentialResult] has `credential` set to `null`,
     * and contains `credentialId`, to identify the requested credential using [getDeferredCredential].
     * @param credentialRequest Request object, containing requested credential parameters: _format_, _type_, _subject claims_, ...
     * @return Result object, containing the generated credential. Credential is set to `null` if issuance was deferred.
     * @throws CredentialError
     * @see getDeferredCredential
     */
    fun generateCredential(credentialRequest: CredentialRequest): CredentialResult

    /**
     * Gets the credential for a previously made credential request, for which issuance was deferred. (see [generateCredential]).
     * If credential issuance resulted in an error, a [DeferredCredentialError] exception is thrown.
     * If the requested credential is not yet ready, [CredentialResult] has `credential` set to `null`, to indicate further deferral.
     * @param credentialID Unique ID of the credential, previously requested through [generateCredential].
     * @return Result object, containing the generated credential. Credential is set to `null` if issuance was deferred.
     * @throws DeferredCredentialError
     * @see generateCredential
     */
    fun getDeferredCredential(credentialID: String): CredentialResult
}

/**
 * Result of credential generation through [ICredentialProvider].
 * @param format Format of the generated credential
 * @param credential the generated and signed credential as `JsonObject` or `JsonPrimitive` (`string`), depending on the credential [format], or `null` if credential issuance is deferred.
 * @param credentialId Unique ID of the requested credential, used to identify the request if issuance is deferred. (see [ICredentialProvider.getDeferredCredential])
 */
data class CredentialResult(
    val format: CredentialFormat,
    val credential: JsonElement?,
    val credentialId: String? = null,
    val customParameters: Map<String, JsonElement> = mapOf()
)
