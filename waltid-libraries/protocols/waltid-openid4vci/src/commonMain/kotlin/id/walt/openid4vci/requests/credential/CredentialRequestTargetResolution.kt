package id.walt.openid4vci.requests.credential

import id.walt.openid4vci.errors.CredentialError
import id.walt.openid4vci.errors.CredentialErrorCodes

sealed class CredentialRequestTargetResolution {
    data class Success(val credentialConfigurationId: String) : CredentialRequestTargetResolution()
    data class Failure(val error: CredentialError) : CredentialRequestTargetResolution()
}

fun CredentialRequest.resolveCredentialConfigurationId(
    credentialConfigurationExists: (String) -> Boolean,
    resolveCredentialIdentifier: (String) -> String?,
): CredentialRequestTargetResolution {
    if (!credentialConfigurationId.isNullOrBlank() && !credentialIdentifier.isNullOrBlank()) {
        return CredentialRequestTargetResolution.Failure(
            CredentialError(
                CredentialErrorCodes.INVALID_CREDENTIAL_REQUEST,
                "credential_identifier and credential_configuration_id are mutually exclusive",
            )
        )
    }

    credentialConfigurationId?.takeIf { it.isNotBlank() }?.let { requestedConfigurationId ->
        return if (credentialConfigurationExists(requestedConfigurationId)) {
            CredentialRequestTargetResolution.Success(requestedConfigurationId)
        } else {
            CredentialRequestTargetResolution.Failure(
                CredentialError(
                    CredentialErrorCodes.UNKNOWN_CREDENTIAL_CONFIGURATION,
                    "Unknown credential_configuration_id: $requestedConfigurationId",
                )
            )
        }
    }

    credentialIdentifier?.takeIf { it.isNotBlank() }?.let { requestedIdentifier ->
        val resolvedConfigurationId = resolveCredentialIdentifier(requestedIdentifier)
            ?: return CredentialRequestTargetResolution.Failure(
                CredentialError(
                    CredentialErrorCodes.UNKNOWN_CREDENTIAL_IDENTIFIER,
                    "Unknown credential_identifier: $requestedIdentifier",
                )
            )

        return if (credentialConfigurationExists(resolvedConfigurationId)) {
            CredentialRequestTargetResolution.Success(resolvedConfigurationId)
        } else {
            CredentialRequestTargetResolution.Failure(
                CredentialError(
                    CredentialErrorCodes.UNKNOWN_CREDENTIAL_CONFIGURATION,
                    "Unknown credential_configuration_id for credential_identifier: $requestedIdentifier",
                )
            )
        }
    }

    return CredentialRequestTargetResolution.Failure(
        CredentialError(
            CredentialErrorCodes.INVALID_CREDENTIAL_REQUEST,
            "credential_identifier or credential_configuration_id is required",
        )
    )
}
