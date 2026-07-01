package id.walt.openid4vci.clientauth.attestation

suspend fun ClientAttestationVerificationConfig.toClientAttestationConfig(
    keyReferenceResolver: ClientAttestationKeyReferenceResolver? = null,
): ClientAttestationConfig =
    when (val method = verificationMethod) {
        is ClientAttestationVerificationMethod.StaticJwk ->
            ClientAttestationConfig(
                trustResolver = StaticJwkClientAttestationTrustResolver.fromJwk(method.jwk),
            )

        is ClientAttestationVerificationMethod.X509Chain ->
            ClientAttestationConfig(
                attestationVerifier = X509ChainClientAttestationVerifier(method.trustedRootCertificatesPem),
            )

        is ClientAttestationVerificationMethod.KeyReference -> {
            val resolver = requireNotNull(keyReferenceResolver) {
                "key-reference client attestation verification requires a key reference resolver"
            }
            ClientAttestationConfig(
                trustResolver = KeyReferenceClientAttestationTrustResolver(method.reference, resolver),
            )
        }
    }
