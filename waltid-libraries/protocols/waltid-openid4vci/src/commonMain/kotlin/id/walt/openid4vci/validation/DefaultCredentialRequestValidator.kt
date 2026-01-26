package id.walt.openid4vci.validation

import id.walt.openid4vci.DefaultClient
import id.walt.openid4vci.Session
import id.walt.openid4vci.errors.OAuthError
import id.walt.openid4vci.prooftypes.Proofs
import id.walt.openid4vci.requests.credential.DefaultCredentialRequest
import id.walt.openid4vci.requests.credential.CredentialRequestResult
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

class DefaultCredentialRequestValidator : CredentialRequestValidator {
    override fun validate(parameters: Map<String, List<String>>, session: Session?): CredentialRequestResult {
        return try {
            val credentialIdentifier = parameters.optionalSingle("credential_identifier")?.takeIf { it.isNotBlank() }
            val credentialConfigurationId =
                parameters.optionalSingle("credential_configuration_id")?.takeIf { it.isNotBlank() }

            // OpenID4VCI: credential_identifier and credential_configuration_id are mutually exclusive.
            if (!credentialIdentifier.isNullOrEmpty() && !credentialConfigurationId.isNullOrEmpty()) {
                throw IllegalArgumentException("credential_identifier and credential_configuration_id are mutually exclusive")
            }
            if (credentialIdentifier.isNullOrEmpty() && credentialConfigurationId.isNullOrEmpty()) {
                throw IllegalArgumentException("credential_identifier or credential_configuration_id is required")
            }

            // OpenID4VCI: proofs is a JSON object containing proof type -> array of proofs.
            // Accept the raw JSON object here; handlers decide which proof type to use.
            val proofs = parameters.optionalSingle("proofs")?.takeIf { it.isNotBlank() }?.let { proofsValue ->
                val trimmed = proofsValue.trim()
                if (!trimmed.startsWith("{")) {
                    throw IllegalArgumentException("proofs must be a JSON object")
                }
                Proofs.fromJsonObject(Json.parseToJsonElement(trimmed).jsonObject)
            }

            // OpenID4VCI: credential_response_encryption is an optional JSON object.
            val credentialResponseEncryption =
                parameters.optionalSingle("credential_response_encryption")?.takeIf { it.isNotBlank() }?.let { value ->
                    val trimmed = value.trim()
                    if (!trimmed.startsWith("{")) {
                        throw IllegalArgumentException("credential_response_encryption must be a JSON object")
                    }
                    Json.parseToJsonElement(trimmed).jsonObject
                }

            // OAuth2: client_id is not required at the credential endpoint (access token authenticates the client).
            val clientId = parameters.optionalSingle("client_id")?.takeIf { it.isNotBlank() } ?: ""

            val client = DefaultClient(
                id = clientId,
                redirectUris = emptyList(),
                grantTypes = emptySet(),
                responseTypes = emptySet(),
            )

            val request = DefaultCredentialRequest(
                client = client,
                credentialIdentifier = credentialIdentifier,
                credentialConfigurationId = credentialConfigurationId,
                proofs = proofs,
                credentialResponseEncryption = credentialResponseEncryption,
                requestForm = parameters,
                session = session,
            )

            CredentialRequestResult.Success(request)
        } catch (e: SerializationException) {
            CredentialRequestResult.Failure(OAuthError("invalid_request", e.message))
        } catch (e: IllegalArgumentException) {
            CredentialRequestResult.Failure(OAuthError("invalid_request", e.message))
        }
    }
}
