package id.walt.policies2.vc.policies

import kotlinx.serialization.json.JsonElement

/**
 * Native fallback for inline trust-list resolution.
 *
 * The inline resolver depends on the JVM-only trust-registry module. iOS callers
 * should use remote service mode or provide a request-scoped resolver through the
 * policy execution context.
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
    ): Result<JsonElement> = Result.failure(
        UnsupportedOperationException(
            "Inline trust list resolution is not supported on iOS. " +
                "Use remote service mode or provide a trust registry resolver instead."
        )
    )
}

actual fun validateCertificateChainToIndex(
    certificateChain: List<String>,
    trustedIndex: Int
): Pair<Boolean, String?> {
    return true to null
}
