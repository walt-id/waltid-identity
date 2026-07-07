package id.walt.wallet2.mobile

import id.walt.wallet2.data.WalletSessionEvent
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * High-level protocol flow that emitted a [MobileWalletEvent].
 */
public enum class MobileWalletEventPhase {
    /** Credential issuance flow. */
    issuance,

    /** Credential presentation flow. */
    presentation,
}

/**
 * Progress state represented by a [MobileWalletEvent].
 */
public enum class MobileWalletEventStatus {
    /** The flow is still running. */
    progress,

    /** The flow completed successfully. */
    completed,

    /** The flow failed. */
    failed,
}

/**
 * Observable mobile wallet session event.
 *
 * @property name Stable event name emitted by the underlying wallet flow.
 * @property phase Issuance or presentation flow that emitted the event.
 * @property status Current progress state for the event.
 */
public data class MobileWalletEvent(
    val name: String,
    val phase: MobileWalletEventPhase,
    val status: MobileWalletEventStatus,
)

internal class MobileWalletEventStream(
    replay: Int = 10,
    extraBufferCapacity: Int = 32,
) {
    private val mutableEvents = MutableSharedFlow<MobileWalletEvent>(
        replay = replay,
        extraBufferCapacity = extraBufferCapacity,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val events: Flow<MobileWalletEvent> = mutableEvents.asSharedFlow()

    fun tryEmit(event: MobileWalletEvent): Boolean = mutableEvents.tryEmit(event)
}

internal fun WalletSessionEvent.toMobileWalletEvent() = MobileWalletEvent(
    name = name,
    phase = when {
        name.startsWith("issuance_") -> MobileWalletEventPhase.issuance
        else -> MobileWalletEventPhase.presentation
    },
    status = when (this) {
        WalletSessionEvent.issuance_completed,
        WalletSessionEvent.presentation_completed -> MobileWalletEventStatus.completed

        WalletSessionEvent.issuance_failed,
        WalletSessionEvent.presentation_failed -> MobileWalletEventStatus.failed

        else -> MobileWalletEventStatus.progress
    },
)
