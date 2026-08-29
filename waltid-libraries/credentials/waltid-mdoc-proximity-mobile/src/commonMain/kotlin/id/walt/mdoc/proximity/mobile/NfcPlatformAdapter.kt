package id.walt.mdoc.proximity.mobile

import id.walt.mdoc.proximity.ProximityCloseReason
import kotlinx.coroutines.CoroutineScope

/** Side-effect-free host-card-emulation availability. */
public sealed interface NfcHostAvailability {
    /** Every prerequisite for starting an NFC host session is currently satisfied. */
    public data object Available : NfcHostAvailability

    /**
     * NFC hosting cannot currently start.
     *
     * @property code Stable machine-readable reason.
     * @property message Non-empty user-facing explanation that contains no sensitive diagnostics.
     */
    public data class Unavailable(
        public val code: String,
        public val message: String,
    ) : NfcHostAvailability {
        init {
            require(code.isNotBlank() && message.isNotBlank())
        }
    }
}

/** Session-scoped platform host for APDU delivery and field-deactivation callbacks. */
public interface PreparedNfcHostSession {
    /** Idempotently disables routing and releases every platform resource. */
    public suspend fun close(reason: ProximityCloseReason)
}

/** Result of arming one platform NFC host session after a successful capability check. */
public sealed interface NfcHostPreparation {
    /**
     * The platform routing and lifecycle resources are armed for exactly this session.
     *
     * @property session Session-scoped resource owner that must be closed by the caller.
     */
    public data class Ready(public val session: PreparedNfcHostSession) : NfcHostPreparation

    /**
     * A runtime prerequisite changed or session-scoped platform setup could not be acquired.
     *
     * @property availability Typed reason why preparation did not arm any platform resource.
     */
    public data class Unavailable(
        public val availability: NfcHostAvailability.Unavailable,
    ) : NfcHostPreparation
}

/** Narrow Android HCE / Apple CardSession boundary. */
public interface NfcHostPlatformAdapter {
    /** Checks hardware, OS, service routing, and runtime prerequisites without arming a session. */
    public suspend fun capability(): NfcHostAvailability

    /**
     * Arms exactly one generation-bound router.
     *
     * Known prerequisite races and session-scoped acquisition failures are returned as a typed
     * [NfcHostPreparation.Unavailable]. Unexpected implementation failures may still throw.
     */
    public suspend fun prepare(
        router: NfcHostApduRouter,
        sessionScope: CoroutineScope,
    ): NfcHostPreparation
}
