package id.walt.mdoc.proximity

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

data class MdocProximityLimits(
    val maximumEngagementOrHandoverBytes: Int = 64 * 1024,
    val maximumRequestBytes: Int = 1024 * 1024,
    val maximumResponseBytes: Int = 8 * 1024 * 1024,
    val maximumSessionMessageBytes: Int = 8 * 1024 * 1024,
    val maximumCumulativeSessionBytes: Long = 64L * 1024 * 1024,
    val maximumDocuments: Int = 16,
    val maximumNamespacesPerDocument: Int = 64,
    val maximumElementsPerNamespace: Int = 256,
    val maximumReaderCertificateChainLength: Int = 10,
    val maximumReaderCertificateBytes: Int = 512 * 1024,
    val maximumCborDepth: Int = 32,
    val maximumCborItems: Int = 1_000_000,
    val maximumExchanges: Int = 8,
) {
    init {
        require(maximumEngagementOrHandoverBytes > 0 && maximumRequestBytes > 0)
        require(maximumResponseBytes > 0 && maximumSessionMessageBytes > 0 && maximumCumulativeSessionBytes > 0)
        require(maximumDocuments > 0 && maximumNamespacesPerDocument > 0)
        require(maximumElementsPerNamespace > 0 && maximumReaderCertificateChainLength > 0)
        require(maximumReaderCertificateBytes > 0 && maximumCborDepth > 0 && maximumCborItems > 0)
        require(maximumExchanges > 0)
    }

    fun requireEngagementOrHandover(bytes: ImmutableBytes) = requireSize(
        bytes, maximumEngagementOrHandoverBytes, "engagement_too_large", "Engagement or handover data exceeds the configured limit"
    )

    fun requireRequest(bytes: ImmutableBytes) = requireSize(
        bytes, maximumRequestBytes, "request_too_large", "DeviceRequest exceeds the configured limit"
    )

    fun requireResponse(bytes: ImmutableBytes) = requireSize(
        bytes, maximumResponseBytes, "response_too_large", "DeviceResponse exceeds the configured limit"
    )

    fun requireSessionMessage(bytes: ImmutableBytes) = requireSize(
        bytes, maximumSessionMessageBytes, "message_too_large", "Proximity session message exceeds the configured limit"
    )

    private fun requireSize(bytes: ImmutableBytes, maximum: Int, code: String, message: String) {
        if (bytes.size > maximum) throw ProximityException(ProximityError.Protocol(code, message))
    }
}

data class MdocProximityTimeouts(
    val qrEngagementLifetime: Duration = 120.seconds,
    val qrSessionEstablishment: Duration = 60.seconds,
    val nfcSessionEstablishment: Duration = 30.seconds,
    val transportConnection: Duration = 30.seconds,
    val request: Duration = 60.seconds,
    val consent: Duration = 120.seconds,
    val keyAuthorization: Duration = 60.seconds,
    val gracefulTermination: Duration = 5.seconds,
    val totalSession: Duration = 5.minutes,
) {
    init {
        require(
            listOf(
                qrEngagementLifetime,
                qrSessionEstablishment,
                nfcSessionEstablishment,
                transportConnection,
                request,
                consent,
                keyAuthorization,
                gracefulTermination,
                totalSession,
            ).all { it.isPositive() }
        ) { "Proximity timeouts must be positive" }
    }
}
