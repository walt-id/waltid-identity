package id.walt.openid4vci.handlers.credential

import id.walt.crypto.keys.Key
import id.walt.openid4vci.errors.OAuthError
import id.walt.openid4vci.handlers.endpoints.credential.CredentialEndpointHandler
import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import id.walt.openid4vci.responses.credential.CredentialResponse
import id.walt.openid4vci.responses.credential.IssuedCredential
import id.walt.openid4vci.responses.credential.CredentialResponseResult
import id.walt.openid4vci.CredentialFormat
import id.walt.openid4vci.metadata.issuer.CredentialDisplay
import id.walt.openid4vci.requests.credential.CredentialRequest
import id.walt.sdjwt.SDMap
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * SD-JWT VC credential response handler using OpenID4VCI.generateSdJwtVC.
 * The issuer application must provide issuer DID, signing key, and credential data (payload).
 */
class SdJwtVcCredentialHandler : CredentialEndpointHandler {
    private companion object {
        val supportedFormats = setOf(CredentialFormat.SD_JWT_VC, CredentialFormat.JWT_VC, CredentialFormat.JWT_VC_JSON)
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

            val vct = configuration.vct
                ?: return CredentialResponseResult.Failure(
                    OAuthError("invalid_request", "Missing vct for SD-JWT VC credential configuration"),
                )
            val sdJwt = SdJwtVcCredentialSigner.generateSdJwtVC(
                credentialRequest = request,
                credentialData = credentialData,
                issuerId = issuerId,
                issuerKey = issuerKey,
                vct = vct,
                selectiveDisclosure = selectiveDisclosure,
                dataMapping = dataMapping,
                x5Chain = x5Chain,
                display = display,
                sdJwtTypeHeader = configuration.format.value,
            )

            CredentialResponseResult.Success(
                CredentialResponse(
                    credentials = listOf(
                        IssuedCredential(credential = JsonPrimitive(sdJwt)),
                    ),
                )
            )
        } catch (e: Exception) {
            CredentialResponseResult.Failure(OAuthError("invalid_request", e.message))
        }
    }
}
