package id.walt.crypto.keys.aws

import aws.sdk.kotlin.services.kms.KmsClient
import aws.sdk.kotlin.services.kms.model.KmsException

/**
 * A failover-aware wrapper for AWS KMS operations that automatically retries
 * operations in replica regions when the primary region is unavailable.
 *
 * This enables disaster recovery for multi-region KMS keys by falling back
 * to replica keys when the primary region experiences an outage.
 *
 * @param primaryRegion The primary AWS region for the key
 * @param failoverRegions List of replica regions to try if primary fails
 * @param enableFailover Whether to actually perform failover (can be disabled for testing)
 */
class FailoverKmsClient(
    private val primaryRegion: String,
    private val failoverRegions: List<String>,
    private val enableFailover: Boolean = true,
) {
    /**
     * Ordered list of regions to try: primary first, then failover regions.
     */
    private val regionOrder: List<String> = listOf(primaryRegion) + failoverRegions.filter { it != primaryRegion }

    /**
     * The region that was last successfully used.
     * Useful for logging and debugging failover behavior.
     */
    @Volatile
    var lastSuccessfulRegion: String? = null
        private set

    /**
     * Execute a KMS operation with automatic failover.
     *
     * Tries each region in order (primary first, then replicas) until one succeeds.
     * Only fails over on region-level failures (timeouts, connection errors, service unavailable).
     * Key-specific errors (invalid key, wrong algorithm, etc.) are thrown immediately.
     *
     * @param operation The KMS operation to execute
     * @return The result of the operation from whichever region succeeded
     * @throws Exception if all regions fail
     */
    suspend fun <T> execute(operation: suspend (KmsClient) -> T): T {
        var lastException: Exception? = null

        for (region in regionOrder) {
            try {
                val result = KmsClient { this.region = region }.use { kms ->
                    operation(kms)
                }
                lastSuccessfulRegion = region
                if (region != primaryRegion) {
                    // Log that we used a failover region
                    System.err.println("AWS KMS: Successfully used failover region $region (primary: $primaryRegion)")
                }
                return result
            } catch (e: Exception) {
                lastException = e

                // Check if this is a region-level failure that warrants failover
                if (!isRegionFailure(e) || !enableFailover) {
                    // This is a key-specific error or failover is disabled - don't try other regions
                    throw e
                }

                // Log the failure and try the next region
                System.err.println("AWS KMS: Region $region unavailable, trying next region: ${e.message}")
            }
        }

        // All regions failed
        throw lastException ?: IllegalStateException("No regions available for KMS operation")
    }

    /**
     * Determine if an exception represents a region-level failure that should trigger failover.
     *
     * Region failures include:
     * - Connection timeouts
     * - Network errors
     * - Service unavailable (503)
     * - Throttling (may indicate regional issues)
     *
     * Key-specific errors that should NOT trigger failover:
     * - Key not found
     * - Invalid key state
     * - Wrong algorithm
     * - Access denied
     */
    private fun isRegionFailure(e: Exception): Boolean {
        val message = e.message?.lowercase() ?: ""

        // Connection/network errors - definitely failover
        if (message.contains("timeout") ||
            message.contains("connection") ||
            message.contains("socket") ||
            message.contains("network") ||
            message.contains("unreachable")) {
            return true
        }

        // Service availability errors - failover
        if (message.contains("service unavailable") ||
            message.contains("503") ||
            message.contains("internal server error") ||
            message.contains("500")) {
            return true
        }

        // Throttling might indicate regional issues - consider failover
        if (e is KmsException && message.contains("throttl")) {
            return true
        }

        // Everything else (key not found, access denied, invalid state, etc.) - don't failover
        return false
    }

    /**
     * Get information about the failover configuration.
     */
    fun getFailoverInfo(): FailoverInfo = FailoverInfo(
        primaryRegion = primaryRegion,
        failoverRegions = failoverRegions,
        enableFailover = enableFailover,
        lastSuccessfulRegion = lastSuccessfulRegion,
        regionOrder = regionOrder
    )

    /**
     * Information about the failover configuration and state.
     */
    data class FailoverInfo(
        val primaryRegion: String,
        val failoverRegions: List<String>,
        val enableFailover: Boolean,
        val lastSuccessfulRegion: String?,
        val regionOrder: List<String>
    )
}
