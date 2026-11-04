package id.walt.policies.policies

import id.walt.credentials.keyresolver.resolvers.X5cTrustAnchors

/**
 * Policy context key for the trusted root CAs (`List<String>`, PEM or base64 DER) that an issuer `x5c`
 * certificate chain must chain to before its key is used.
 */
const val TRUSTED_ROOT_CAS_CONTEXT_KEY = "trustedRootCAs"

/**
 * Fails unless [x5c] chains to one of the trusted root CAs in [context]. The chain is chosen by the
 * credential's signer, so its key proves nothing until the chain is anchored.
 */
internal suspend fun requireTrustedIssuerX5c(x5c: List<String>, context: Map<String, Any>) {
    val trustRoots = (context[TRUSTED_ROOT_CAS_CONTEXT_KEY] as? List<*>)?.filterIsInstance<String>().orEmpty()
    require(trustRoots.isNotEmpty()) {
        "The issuer key is taken from the credential's x5c certificate chain, which is not trusted by itself. " +
                "Configure trusted root CAs for this verification."
    }
    X5cTrustAnchors.validate(x5c, trustRoots).getOrThrow()
}
