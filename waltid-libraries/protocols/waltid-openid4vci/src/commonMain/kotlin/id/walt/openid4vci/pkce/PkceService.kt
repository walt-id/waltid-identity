package id.walt.openid4vci.pkce

import id.walt.openid4vci.errors.OAuthError
import id.walt.openid4vci.errors.OAuthErrorCodes

sealed interface PkceAuthorizationResult {
    data class Success(val binding: PkceBinding) : PkceAuthorizationResult
    data class Failure(val error: OAuthError) : PkceAuthorizationResult
}

sealed interface PkceVerificationResult {
    data object Success : PkceVerificationResult
    data class Failure(val error: OAuthError) : PkceVerificationResult
}

/** Owns PKCE protocol validation. Method implementations only handle their transformation. */
class PkceService(private val config: PkceServiceConfig = PkceServiceConfig()) {
    private val methods = config.methods.associateBy { it.name }

    suspend fun validateAuthorizationRequest(parameters: Map<String, List<String>>): PkceAuthorizationResult {
        for (name in listOf("code_challenge", "code_challenge_method")) {
            if (parameters.containsKey(name) && parameters.getValue(name).size != 1) {
                return invalidAuthorization("Exactly one $name value is required when supplied")
            }
        }
        // RFC 6749 section 3.1: empty values are omitted, after rejecting repeated parameters.
        val challenge = parameters["code_challenge"]?.single()?.takeIf(String::isNotEmpty)
        val methodName = parameters["code_challenge_method"]?.single()?.takeIf(String::isNotEmpty)
        if (challenge == null) {
            if (methodName != null) return invalidAuthorization("code_challenge is required with code_challenge_method")
            if (config.requirePkce()) return invalidAuthorization("PKCE code_challenge is required")
            return PkceAuthorizationResult.Success(PkceBinding.NotUsed)
        }

        // RFC 7636 defaults an omitted method to plain; it must still be explicitly configured.
        val effectiveMethod = methodName ?: "plain"
        val method = methods[effectiveMethod]
            ?: return invalidAuthorization("Unsupported PKCE code_challenge_method")
        if (!Pkce.isValidVerifier(challenge) || !method.isValidChallenge(challenge)) {
            return invalidAuthorization("Invalid PKCE code_challenge")
        }
        return PkceAuthorizationResult.Success(PkceBinding.Challenge(challenge, effectiveMethod))
    }

    suspend fun verifyTokenRequest(
        parameters: Map<String, List<String>>,
        binding: PkceBinding?,
    ): PkceVerificationResult {
        if (binding == null) return invalidGrant("Authorization code has no recorded PKCE state; restart authorization")
        val values = parameters["code_verifier"]
        if (parameters.containsKey("code_verifier") && values?.size != 1) {
            return PkceVerificationResult.Failure(
                OAuthError(OAuthErrorCodes.INVALID_REQUEST, "Exactly one code_verifier value is required when supplied"),
            )
        }
        // RFC 6749 section 3.2 applies the same empty-value rule at the token endpoint.
        val verifier = values?.single()?.takeIf(String::isNotEmpty)
        return when (binding) {
            PkceBinding.NotUsed -> when {
                verifier != null -> invalidGrant("code_verifier was supplied without an authorization code_challenge")
                config.requirePkce() -> invalidGrant("PKCE is required for this authorization code")
                else -> PkceVerificationResult.Success
            }

            is PkceBinding.Challenge -> {
                val method = methods[binding.codeChallengeMethod]
                    ?: return invalidGrant("Authorization code uses an unsupported PKCE method")
                if (!Pkce.isValidVerifier(binding.codeChallenge) || !method.isValidChallenge(binding.codeChallenge)) {
                    return invalidGrant("Invalid stored PKCE challenge")
                }
                if (verifier == null) {
                    return PkceVerificationResult.Failure(
                        OAuthError(
                            OAuthErrorCodes.INVALID_REQUEST,
                            "PKCE was used in the authorization request, but no code_verifier was provided",
                        ),
                    )
                }
                if (!Pkce.isValidVerifier(verifier)) return invalidGrant("Invalid PKCE code_verifier format")
                if (method.calculateChallenge(verifier) != binding.codeChallenge) {
                    return invalidGrant("PKCE verification failed")
                }
                PkceVerificationResult.Success
            }
        }
    }

    private fun invalidAuthorization(description: String) =
        PkceAuthorizationResult.Failure(OAuthError(OAuthErrorCodes.INVALID_REQUEST, description))

    private fun invalidGrant(description: String) =
        PkceVerificationResult.Failure(OAuthError(OAuthErrorCodes.INVALID_GRANT, description))
}
