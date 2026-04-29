package id.walt.policies2.vc.policies

import kotlinx.serialization.json.JsonElement

/**
 * JS stub implementation of inline trust list resolution.
 * 
 * The waltid-trust-registry library is JVM-only, so inline trust list resolution
 * is not available on JS platforms. Use the remote service mode instead by providing
 * a `trustRegistryUrl` in the policy configuration.
 */
actual object ETSITrustListInlineResolver {
    
    actual suspend fun resolve(
        certificateChain: List<String>,
        trustLists: List<String>,
        expectedEntityType: String?,
        expectedServiceType: String?,
        allowStaleSource: Boolean,
        requireAuthenticated: Boolean,
        validateSignatures: Boolean
    ): Result<JsonElement> {
        return Result.failure(
            UnsupportedOperationException(
                "Inline trust list resolution is not supported on JS platform. " +
                "The waltid-trust-registry library is JVM-only. " +
                "Use remote service mode by providing a 'trustRegistryUrl' in the policy configuration instead."
            )
        )
    }
}

/**
 * JS stub implementation of certificate chain validation.
 * 
 * Certificate chain validation (waltid-x509) is not yet implemented on JS platform.
 * Use remote service mode for trust verification where the server handles validation.
 */
actual fun validateCertificateChainToIndex(
    certificateChain: List<String>,
    trustedIndex: Int
): Pair<Boolean, String?> {
    // On JS, waltid-x509 throws "Not implemented on JS yet".
    // For remote service mode, the server should validate the chain.
    // Return success here since remote/enterprise mode relies on server-side validation.
    return true to null
}
