package id.walt.openid4vci.requests.credential

import id.walt.openid4vci.errors.CredentialError
import id.walt.openid4vci.errors.CredentialErrorCodes
import id.walt.openid4vci.requests.authorization.AuthorizationDetail
import id.walt.openid4vci.requests.authorization.OPENID_CREDENTIAL_AUTHORIZATION_DETAIL_TYPE

data class CredentialAuthorization(
    val credentialIdentifier: String,
    val credentialConfigurationId: String,
)

fun Iterable<CredentialAuthorization>.toAuthorizationDetails(): List<AuthorizationDetail> {
    val identifiersByConfiguration = linkedMapOf<String, MutableList<String>>()
    forEach { authorization ->
        identifiersByConfiguration
            .getOrPut(authorization.credentialConfigurationId) { mutableListOf() }
            .add(authorization.credentialIdentifier)
    }
    return identifiersByConfiguration.map { (credentialConfigurationId, credentialIdentifiers) ->
        AuthorizationDetail(
            type = OPENID_CREDENTIAL_AUTHORIZATION_DETAIL_TYPE,
            credentialConfigurationId = credentialConfigurationId,
            credentialIdentifiers = credentialIdentifiers,
        )
    }
}

sealed class CredentialAuthorizationResolution {
    data class Success(val authorization: CredentialAuthorization) : CredentialAuthorizationResolution()
    data class Failure(val error: CredentialError) : CredentialAuthorizationResolution()
}

fun CredentialRequest.resolveCredentialAuthorization(
    authorizations: Collection<CredentialAuthorization>,
): CredentialAuthorizationResolution {
    if (!credentialConfigurationId.isNullOrBlank() && !credentialIdentifier.isNullOrBlank()) {
        return CredentialAuthorizationResolution.Failure(
            CredentialError(
                CredentialErrorCodes.INVALID_CREDENTIAL_REQUEST,
                "credential_identifier and credential_configuration_id are mutually exclusive",
            )
        )
    }

    credentialIdentifier?.takeIf { it.isNotBlank() }?.let { requestedIdentifier ->
        val authorization = authorizations.singleOrNull { it.credentialIdentifier == requestedIdentifier }
            ?: return CredentialAuthorizationResolution.Failure(
                CredentialError(
                    CredentialErrorCodes.UNKNOWN_CREDENTIAL_IDENTIFIER,
                    "Unknown credential_identifier: $requestedIdentifier",
                )
            )
        return CredentialAuthorizationResolution.Success(authorization)
    }

    credentialConfigurationId?.takeIf { it.isNotBlank() }?.let { requestedConfigurationId ->
        val matchingAuthorizations = authorizations.filter {
            it.credentialConfigurationId == requestedConfigurationId
        }
        return when (matchingAuthorizations.size) {
            1 -> CredentialAuthorizationResolution.Success(matchingAuthorizations.single())
            0 -> CredentialAuthorizationResolution.Failure(
                CredentialError(
                    CredentialErrorCodes.UNKNOWN_CREDENTIAL_CONFIGURATION,
                    "Unknown credential_configuration_id: $requestedConfigurationId",
                )
            )
            else -> CredentialAuthorizationResolution.Failure(
                CredentialError(
                    CredentialErrorCodes.INVALID_CREDENTIAL_REQUEST,
                    "credential_configuration_id '$requestedConfigurationId' is ambiguous; use credential_identifier",
                )
            )
        }
    }

    return CredentialAuthorizationResolution.Failure(
        CredentialError(
            CredentialErrorCodes.INVALID_CREDENTIAL_REQUEST,
            "credential_identifier or credential_configuration_id is required",
        )
    )
}

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
