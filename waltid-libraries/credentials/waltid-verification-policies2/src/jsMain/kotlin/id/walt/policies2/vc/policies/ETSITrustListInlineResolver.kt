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
