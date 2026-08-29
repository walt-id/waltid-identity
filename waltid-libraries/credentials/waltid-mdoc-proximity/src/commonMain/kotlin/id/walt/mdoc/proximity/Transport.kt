package id.walt.mdoc.proximity

import id.walt.mdoc.objects.engagement.DeviceRetrievalMethod
import kotlinx.coroutines.CoroutineScope

enum class ProximityTransportKind { BLE, NFC, WIFI_AWARE, FAKE }

/** Stable identity for one prepared bearer; several bearers may have the same [ProximityTransportKind]. */
data class PreparedTransportId(val value: String) {
    init {
        require(value.isNotBlank()) { "A prepared transport identifier must not be blank" }
    }
}

sealed interface MdocEngagementMode {
    data object Qr : MdocEngagementMode
    data object Nfc : MdocEngagementMode
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
    val id: PreparedTransportId get() = PreparedTransportId(kind.name)

    /** Reports this provider's support dimensions without preparing radio resources. */
    suspend fun capability(context: EngagementContext): ProximityCapability

    /**
     * Prepares one session-scoped listener. Cancelling [sessionScope] must make every pending
     * provider operation complete and release resources; [PreparedTransport.close] remains the
     * authoritative idempotent cleanup operation.
     */
    suspend fun prepare(context: EngagementContext, sessionScope: CoroutineScope): PreparedTransport
}

/** A validated method or capability offered by a reader during NFC handover. */
sealed interface ReaderSelectedTransportOffer {
    /** A complete retrieval method, including reader-owned endpoint parameters when applicable. */
    data class Method(val value: DeviceRetrievalMethod) : ReaderSelectedTransportOffer

    /** Conventional Handover offer allowing the holder to publish its own BLE peripheral endpoint. */
    data object BlePeripheralServer : ReaderSelectedTransportOffer
}

/**
 * Optional provider boundary for retrieval selected only after the NFC reader's handover offer.
 * Static and QR engagement continue to use [ProximityTransportProvider.prepare].
 */
interface ReaderSelectedTransportProvider : ProximityTransportProvider {
    /**
     * Returns whether this provider can derive one holder endpoint from [offer] without preparing
     * radio resources. Combined BLE methods are narrowed according to the provider's configured
     * holder roles; a reader-owned endpoint must retain its exact parameters.
     */
    fun acceptsReaderOffer(offer: ReaderSelectedTransportOffer): Boolean

    /**
     * Prepares the single endpoint selected from [offer]. This operation initializes or publishes
     * the endpoint only; it must not wait for the reader to connect because the NFC handover is
     * still waiting for the selected method.
     */
    suspend fun prepareReaderSelected(
        offer: ReaderSelectedTransportOffer,
        context: EngagementContext,
        sessionScope: CoroutineScope,
    ): PreparedTransport
}

interface PreparedTransport {
    val kind: ProximityTransportKind
    val id: PreparedTransportId get() = PreparedTransportId(kind.name)
    val connectionMethod: DeviceRetrievalMethod
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

/** Why a prepared bearer, active connection, or platform host is being released. */
enum class ProximityCloseReason {
    /** The complete proximity presentation finished successfully. */
    COMPLETED,

    /** Engagement succeeded and retrieval continues on a different bearer. */
    HANDOVER_COMPLETED,

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
