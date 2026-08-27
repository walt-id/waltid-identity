package id.walt.mdoc.proximity

import id.walt.mdoc.objects.engagement.DeviceRetrievalMethod
import kotlinx.coroutines.CoroutineScope

enum class ProximityTransportKind { BLE, NFC, WIFI_AWARE, FAKE }

sealed interface MdocEngagementMode {
    data object Qr : MdocEngagementMode
    data class Nfc(
        /** Exact SessionEstablishment received during negotiated handover, when that path was used. */
        val negotiatedSessionEstablishment: ImmutableBytes? = null,
    ) : MdocEngagementMode
}

/** Four independent support dimensions; callers must not collapse these to one optimistic Boolean. */
data class ProximityCapability(
    val implemented: Boolean,
    val profilePermitted: Boolean,
    val runtimeAvailable: Boolean,
    val sessionSelected: Boolean = false,
    val unavailableReason: ProximityError? = null,
) {
    init {
        require(!sessionSelected || implemented && profilePermitted && runtimeAvailable) {
            "Only an implemented, profile-permitted, runtime-available transport can be selected"
        }
        require(unavailableReason == null || !implemented || !profilePermitted || !runtimeAvailable) {
            "An implemented, profile-permitted, runtime-available transport cannot carry an unavailable reason"
        }
    }

    val mayPrepare: Boolean get() = implemented && profilePermitted && runtimeAvailable && sessionSelected
}

data class EngagementContext(
    val profile: MdocProximityProfile,
    val maximumMessageBytes: Int,
    val engagementMode: MdocEngagementMode,
) {
    init {
        require(maximumMessageBytes > 0)
    }
}

interface ProximityTransportProvider {
    val kind: ProximityTransportKind

    /** Reports this provider's support dimensions without preparing radio resources. */
    suspend fun capability(context: EngagementContext): ProximityCapability

    /**
     * Prepares one session-scoped listener. Cancelling [sessionScope] must make every pending
     * provider operation complete and release resources; [PreparedTransport.close] remains the
     * authoritative idempotent cleanup operation.
     */
    suspend fun prepare(context: EngagementContext, sessionScope: CoroutineScope): PreparedTransport
}

interface PreparedTransport {
    val kind: ProximityTransportKind
    val connectionMethod: DeviceRetrievalMethod
    val sessionTranscriptFactory: SessionTranscriptFactory

    /** Awaits one connection. Cancellation must not leak a subsequently delivered connection. */
    suspend fun awaitConnection(): ProximityConnection
    /** Idempotently closes the listener and any connection returned by [awaitConnection]. */
    suspend fun close(reason: ProximityCloseReason)
}

interface ProximityConnection {
    val kind: ProximityTransportKind

    /** Receives one complete protocol message, or `null` after an orderly peer disconnect. */
    suspend fun receive(): ImmutableBytes?

    /** Sends one complete protocol message; ownership of [message] remains with the caller. */
    suspend fun send(message: ImmutableBytes)

    /** Idempotently closes this connection and releases its platform resources. */
    suspend fun close(reason: ProximityCloseReason)
}

enum class ProximityCloseReason {
    COMPLETED,
    CANCELLED,
    LOST_RACE,
    TIMEOUT,
    PEER_DISCONNECTED,
    PROTOCOL_ERROR,
    PLATFORM_UNAVAILABLE,
}

sealed interface ProximityError {
    val code: String
    val message: String

    data class Capability(override val code: String, override val message: String) : ProximityError
    data class Transport(override val code: String, override val message: String) : ProximityError
    data class Protocol(override val code: String, override val message: String) : ProximityError
    data class Security(override val code: String, override val message: String) : ProximityError
    data class Policy(override val code: String, override val message: String) : ProximityError
}

class ProximityException(val error: ProximityError, cause: Throwable? = null) : Exception(error.message, cause)
