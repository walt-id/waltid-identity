package id.walt.wallet2.mobile

import id.walt.wallet2.data.WalletSessionEvent
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

enum class MobileWalletEventPhase {
    issuance,
    presentation,
}

enum class MobileWalletEventStatus {
    progress,
    completed,
    failed,
}

data class MobileWalletEvent(
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
