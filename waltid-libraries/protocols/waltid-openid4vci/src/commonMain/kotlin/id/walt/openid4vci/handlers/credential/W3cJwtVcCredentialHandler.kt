package id.walt.openid4vci.handlers.credential

import id.walt.crypto.keys.Key
import id.walt.openid4vci.CredentialFormat
import id.walt.openid4vci.errors.OAuthError
import id.walt.openid4vci.handlers.endpoints.credential.CredentialEndpointHandler
import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import id.walt.openid4vci.metadata.issuer.CredentialDisplay
import id.walt.openid4vci.requests.credential.CredentialRequest
import id.walt.openid4vci.responses.credential.CredentialResponse
import id.walt.openid4vci.responses.credential.CredentialResponseResult
import id.walt.openid4vci.responses.credential.IssuedCredential
import id.walt.sdjwt.SDMap
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * W3C JWT VC credential response handler.
 * Supports JWT VC formats (jwt_vc_json, jwt_vc).
 */
class W3cJwtVcCredentialHandler : CredentialEndpointHandler {
    private companion object {
        val supportedFormats = setOf(
            CredentialFormat.JWT_VC_JSON,
            CredentialFormat.JWT_VC,
        )
    }

    override suspend fun sign(
        request: CredentialRequest,
        configuration: CredentialConfiguration,
        issuerKey: Key,
        issuerId: String,
        credentialData: JsonObject,
        dataMapping: JsonObject?,
        selectiveDisclosure: SDMap?,
        x5Chain: List<String>?,
        display: List<CredentialDisplay>?,
        w3cVersion: String?,
    ): CredentialResponseResult {
        return try {
            if (configuration.format !in supportedFormats) {
                return CredentialResponseResult.Failure(
                    OAuthError(
                        "unsupported_credential_configuration",
                        "Unsupported format ${configuration.format.value}"
                    )
                )
            }

            val jwtVc = W3cJwtVcCredentialSigner.generateW3CJwtVC(
                credentialRequest = request,
                credentialData = credentialData,
                issuerId = issuerId,
                issuerKey = issuerKey,
                selectiveDisclosure = selectiveDisclosure,
                dataMapping = dataMapping,
                x5Chain = x5Chain,
                display = display,
                w3cVersion = w3cVersion,
            )

            CredentialResponseResult.Success(
                CredentialResponse(
                    credentials = listOf(
                        IssuedCredential(credential = JsonPrimitive(jwtVc)),
                    ),
                )
            )
        } catch (e: Exception) {
            CredentialResponseResult.Failure(OAuthError("invalid_request", e.message))
        }
    }
}
