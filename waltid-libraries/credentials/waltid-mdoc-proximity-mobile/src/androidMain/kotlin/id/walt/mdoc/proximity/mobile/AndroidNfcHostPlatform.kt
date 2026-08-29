package id.walt.mdoc.proximity.mobile

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.nfc.cardemulation.CardEmulation
import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import id.walt.mdoc.proximity.ImmutableBytes
import id.walt.mdoc.proximity.ProximityCloseReason
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser

internal val ANDROID_MDOC_REQUIRED_AIDS: Set<String> = setOf(
    "D2760000850101",
    "A0000002480400",
    "A0000002480401",
)

internal data class AndroidNfcAidGroupMetadata(
    val category: String?,
    val hasDescription: Boolean,
    val aids: Set<String>,
)

internal data class AndroidNfcServiceMetadata(
    val requiresDeviceUnlock: Boolean,
    val aidGroups: List<AndroidNfcAidGroupMetadata>,
)

internal fun validateAndroidMdocNfcServiceMetadata(
    metadata: AndroidNfcServiceMetadata,
): NfcHostAvailability.Unavailable? {
    if (!metadata.requiresDeviceUnlock) {
        return NfcHostAvailability.Unavailable(
            "nfc_service_invalid",
            "The mdoc NFC host service must require device unlock",
        )
    }
    if (metadata.aidGroups.size != 1) {
        return NfcHostAvailability.Unavailable(
            "nfc_service_invalid",
            "The mdoc NFC host service must declare one dedicated application-identifier group",
        )
    }
    val group = metadata.aidGroups.single()
    if (group.category != CardEmulation.CATEGORY_OTHER || !group.hasDescription) {
        return NfcHostAvailability.Unavailable(
            "nfc_service_invalid",
            "The mdoc NFC application-identifier group must be user-described and use category other",
        )
    }
    val missing = ANDROID_MDOC_REQUIRED_AIDS - group.aids
    if (missing.isNotEmpty()) {
        return NfcHostAvailability.Unavailable(
            "nfc_aids_missing",
            "The mdoc NFC host service is missing required application identifiers",
        )
    }
    if (group.aids != ANDROID_MDOC_REQUIRED_AIDS) {
        return NfcHostAvailability.Unavailable(
            "nfc_service_invalid",
            "The mdoc NFC host service application-identifier group must contain only the supported mdoc applications",
        )
    }
    return null
}

internal sealed interface AndroidNfcHostSystemState {
    data class Ready(val allApplicationsRouted: Boolean) : AndroidNfcHostSystemState
    data class Unavailable(
        val availability: NfcHostAvailability.Unavailable,
    ) : AndroidNfcHostSystemState
}

internal fun interface AndroidNfcHostEnvironment {
    fun inspect(): AndroidNfcHostSystemState
}

internal interface AndroidNfcForegroundRoute {
    fun enable(): Boolean
    fun disable(): Boolean
}

/** Serializes Android's process-wide preferred-service route by NFC session generation. */
internal object AndroidNfcForegroundRouteRegistry {
    private data class Lease(
        val generation: Long,
        val route: AndroidNfcForegroundRoute,
    )

    private val mutex = Mutex()
    private var lease: Lease? = null

    suspend fun acquire(generation: Long, route: AndroidNfcForegroundRoute): Boolean = mutex.withLock {
        if (!route.enable()) return@withLock false
        lease = Lease(generation, route)
        true
    }

    suspend fun release(generation: Long) = mutex.withLock {
        val owned = lease?.takeIf { it.generation == generation } ?: return@withLock
        lease = null
        owned.route.disable()
        Unit
    }

    suspend fun resetForTest() = mutex.withLock {
        val owned = lease
        lease = null
        owned?.route?.disable()
        Unit
    }
}

/** Foreground HCE routing owned by the Activity displaying the proximity session. */
private class AndroidNfcForegroundRouting(
    private val activity: Activity,
    private val service: ComponentName,
) : AndroidNfcForegroundRoute {
    private val cardEmulation: CardEmulation?
        get() = NfcAdapter.getDefaultAdapter(activity)?.let(CardEmulation::getInstance)

    override fun enable(): Boolean = cardEmulation?.setPreferredService(activity, service) == true
    override fun disable(): Boolean = cardEmulation?.unsetPreferredService(activity) == true
}

internal class AndroidSystemNfcHostEnvironment(
    context: Context,
    private val service: ComponentName,
) : AndroidNfcHostEnvironment {
    private val applicationContext = context.applicationContext

    override fun inspect(): AndroidNfcHostSystemState {
        val manager = applicationContext.packageManager
        if (!manager.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION)) {
            return unavailable("nfc_hce_unsupported", "This Android device does not support NFC host card emulation")
        }
        val adapter = NfcAdapter.getDefaultAdapter(applicationContext)
            ?: return unavailable("nfc_adapter_unavailable", "This Android device has no NFC adapter")
        if (!adapter.isEnabled) return unavailable("nfc_powered_off", "NFC is powered off")
        val info = try {
            manager.getServiceInfo(service, PackageManager.GET_META_DATA)
        } catch (_: PackageManager.NameNotFoundException) {
            return unavailable("nfc_service_missing", "The mdoc NFC host service is not declared")
        }
        if (!info.enabled || !info.exported || info.permission != Manifest.permission.BIND_NFC_SERVICE) {
            return unavailable("nfc_service_invalid", "The mdoc NFC host service declaration is invalid")
        }
        val metadata = runCatching { serviceMetadata(info) }.getOrNull()
            ?: return unavailable("nfc_service_invalid", "The mdoc NFC host service metadata is invalid")
        validateAndroidMdocNfcServiceMetadata(metadata)?.let {
            return AndroidNfcHostSystemState.Unavailable(it)
        }
        val cardEmulation = runCatching { CardEmulation.getInstance(adapter) }.getOrNull()
            ?: return unavailable(
                "nfc_system_unavailable",
                "Android NFC card emulation is temporarily unavailable",
            )
        return AndroidNfcHostSystemState.Ready(
            allApplicationsRouted = ANDROID_MDOC_REQUIRED_AIDS.all {
                cardEmulation.isDefaultServiceForAid(service, it)
            },
        )
    }

    private fun serviceMetadata(info: android.content.pm.ServiceInfo): AndroidNfcServiceMetadata {
        val parser = info.loadXmlMetaData(applicationContext.packageManager, HostApduService.SERVICE_META_DATA)
            ?: error("The mdoc NFC host service metadata is missing")
        return parser.use {
            var requiresDeviceUnlock = false
            val groups = mutableListOf<AndroidNfcAidGroupMetadata>()
            var currentCategory: String? = null
            var currentHasDescription = false
            var currentAids: MutableSet<String>? = null
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when {
                    event == XmlPullParser.START_TAG && parser.name == "host-apdu-service" -> {
                        requiresDeviceUnlock = parser.getAttributeBooleanValue(
                            ANDROID_NAMESPACE,
                            "requireDeviceUnlock",
                            false,
                        )
                    }
                    event == XmlPullParser.START_TAG && parser.name == "aid-group" -> {
                        check(currentAids == null) { "Nested NFC application-identifier groups are invalid" }
                        currentCategory = parser.getAttributeValue(ANDROID_NAMESPACE, "category")
                        currentHasDescription = !parser.getAttributeValue(
                            ANDROID_NAMESPACE,
                            "description",
                        ).isNullOrBlank()
                        currentAids = linkedSetOf()
                    }
                    event == XmlPullParser.START_TAG && parser.name == "aid-filter" -> {
                        val group = requireNotNull(currentAids) {
                            "An NFC application identifier must belong to a group"
                        }
                        val value = requireNotNull(parser.getAttributeValue(ANDROID_NAMESPACE, "name")) {
                            "An NFC application identifier cannot be empty"
                        }
                        check(group.add(value.uppercase())) {
                            "Duplicate NFC application identifier"
                        }
                    }
                    event == XmlPullParser.END_TAG && parser.name == "aid-group" -> {
                        groups += AndroidNfcAidGroupMetadata(
                            category = currentCategory,
                            hasDescription = currentHasDescription,
                            aids = requireNotNull(currentAids).toSet(),
                        )
                        currentCategory = null
                        currentHasDescription = false
                        currentAids = null
                    }
                }
                event = parser.next()
            }
            check(currentAids == null) { "The NFC application-identifier group is incomplete" }
            AndroidNfcServiceMetadata(requiresDeviceUnlock, groups)
        }
    }

    private fun unavailable(code: String, message: String) = AndroidNfcHostSystemState.Unavailable(
        NfcHostAvailability.Unavailable(code, message),
    )

    private companion object {
        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
    }
}

private sealed interface AndroidNfcHostReadiness {
    data class Ready(val foregroundRoute: AndroidNfcForegroundRoute?) : AndroidNfcHostReadiness
    data class Unavailable(
        val availability: NfcHostAvailability.Unavailable,
    ) : AndroidNfcHostReadiness
}

/** Android HCE eligibility and generation-bound session registry adapter. */
public class AndroidNfcHostPlatformAdapter internal constructor(
    private val environment: AndroidNfcHostEnvironment,
    private val foregroundRoutingProvider: () -> AndroidNfcForegroundRoute?,
) : NfcHostPlatformAdapter {
    /**
     * Creates the Android HCE adapter for one app-owned mdoc service.
     *
     * [foregroundActivityProvider] supplies the currently resumed Activity only when Android needs
     * a session-scoped preferred-service route. The adapter always binds that route to [service].
     */
    public constructor(
        context: Context,
        service: ComponentName,
        foregroundActivityProvider: () -> Activity? = { null },
    ) : this(
        environment = AndroidSystemNfcHostEnvironment(context, service),
        foregroundRoutingProvider = {
            foregroundActivityProvider()?.let { AndroidNfcForegroundRouting(it, service) }
        },
    )

    override suspend fun capability(): NfcHostAvailability = when (val readiness = readiness()) {
        is AndroidNfcHostReadiness.Ready -> NfcHostAvailability.Available
        is AndroidNfcHostReadiness.Unavailable -> readiness.availability
    }

    override suspend fun prepare(
        router: NfcHostApduRouter,
        sessionScope: CoroutineScope,
    ): NfcHostPreparation {
        val readiness = when (val current = readiness()) {
            is AndroidNfcHostReadiness.Ready -> current
            is AndroidNfcHostReadiness.Unavailable -> return NfcHostPreparation.Unavailable(current.availability)
        }
        val parentJob = sessionScope.coroutineContext[Job]
            ?: return unavailablePreparation(
                "nfc_session_scope_invalid",
                "The NFC card presentation requires a session-owned coroutine scope",
            )
        if (!parentJob.isActive) {
            return unavailablePreparation(
                "nfc_session_scope_inactive",
                "The NFC card presentation session is no longer active",
            )
        }
        val foregroundRouting = readiness.foregroundRoute
        val generation = try {
            AndroidNfcSessionRegistry.arm(router, parentJob)
        } catch (failure: AndroidNfcSessionAlreadyActiveException) {
            return unavailablePreparation(
                "nfc_session_already_active",
                "An NFC card presentation is already active",
            )
        } catch (failure: AndroidNfcGenerationExhaustedException) {
            return unavailablePreparation(
                "nfc_system_unavailable",
                "The NFC card presentation service is temporarily unavailable",
            )
        }
        return try {
            if (foregroundRouting != null &&
                !AndroidNfcForegroundRouteRegistry.acquire(generation, foregroundRouting)
            ) {
                AndroidNfcSessionRegistry.disarm(generation, ProximityCloseReason.PLATFORM_UNAVAILABLE)
                return unavailablePreparation(
                    "nfc_foreground_routing_failed",
                    "Android could not select the mdoc NFC service for foreground routing",
                )
            }
            if (!AndroidNfcSessionRegistry.isCurrent(generation)) {
                AndroidNfcForegroundRouteRegistry.release(generation)
                return unavailablePreparation(
                    "nfc_session_scope_inactive",
                    "The NFC card presentation session is no longer active",
                )
            }
            NfcHostPreparation.Ready(AndroidPreparedNfcHostSession(generation))
        } catch (failure: Throwable) {
            withContext(NonCancellable) {
                AndroidNfcSessionRegistry.disarm(generation, ProximityCloseReason.CANCELLED)
                AndroidNfcForegroundRouteRegistry.release(generation)
            }
            throw failure
        }
    }

    private fun readiness(): AndroidNfcHostReadiness = when (val state = environment.inspect()) {
        is AndroidNfcHostSystemState.Unavailable -> AndroidNfcHostReadiness.Unavailable(state.availability)
        is AndroidNfcHostSystemState.Ready -> if (state.allApplicationsRouted) {
            AndroidNfcHostReadiness.Ready(null)
        } else {
            foregroundRoutingProvider()?.let(AndroidNfcHostReadiness::Ready)
                ?: AndroidNfcHostReadiness.Unavailable(
                    NfcHostAvailability.Unavailable(
                        "nfc_foreground_routing_required",
                        "The mdoc NFC service requires foreground routing for this session",
                    ),
                )
        }
    }

    private fun unavailablePreparation(code: String, message: String) = NfcHostPreparation.Unavailable(
        NfcHostAvailability.Unavailable(code, message),
    )
}

private class AndroidPreparedNfcHostSession(
    private val generation: Long,
) : PreparedNfcHostSession {
    private val closed = AtomicBoolean(false)

    override suspend fun close(reason: ProximityCloseReason) {
        if (!closed.compareAndSet(false, true)) return
        AndroidNfcSessionRegistry.disarm(generation, reason)
    }
}

/** Base service used by app-owned dedicated mdoc HostApduService declarations. */
public abstract class AndroidMdocHostApduService : HostApduService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val commands = Channel<PendingCommand>(capacity = 1)
    private val fieldGeneration = AtomicLong(NO_FIELD_GENERATION)

    init {
        serviceScope.launch {
            for (pending in commands) {
                val response = try {
                    pending.session.process(pending.command)
                } catch (cancelled: CancellationException) {
                    if (serviceScope.coroutineContext[Job]?.isActive != true) throw cancelled
                    ImmutableBytes.of(STATUS_UNKNOWN_ERROR)
                } catch (_: Throwable) {
                    ImmutableBytes.of(STATUS_UNKNOWN_ERROR)
                }
                if (AndroidNfcSessionRegistry.isCurrent(pending.session.generation)) {
                    sendResponse(response.copy())
                }
            }
        }
    }

    /**
     * Enqueues one framework command for the currently armed generation.
     *
     * A `null` return delegates the response to [sendResponseApdu]; immediate status responses are
     * returned only when the command cannot safely enter the active session.
     */
    final override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray? {
        if (commandApdu == null) return STATUS_WRONG_LENGTH.copyOf()
        val session = AndroidNfcSessionRegistry.current()
            ?: return STATUS_CONDITIONS_NOT_SATISFIED.copyOf()
        val boundGeneration = fieldGeneration.get()
        if (boundGeneration == NO_FIELD_GENERATION) {
            if (!fieldGeneration.compareAndSet(NO_FIELD_GENERATION, session.generation) &&
                fieldGeneration.get() != session.generation
            ) {
                return STATUS_CONDITIONS_NOT_SATISFIED.copyOf()
            }
        } else if (boundGeneration != session.generation) {
            return STATUS_CONDITIONS_NOT_SATISFIED.copyOf()
        }
        if (commands.trySend(PendingCommand(session, commandApdu.copyOf())).isFailure) {
            return STATUS_UNKNOWN_ERROR.copyOf()
        }
        return null
    }

    /** Test seam around the final framework response API; production subclasses do not override it. */
    internal open fun sendResponse(response: ByteArray) {
        sendResponseApdu(response)
    }

    /** Invalidates the current generation when Android reports that the NFC field was removed. */
    final override fun onDeactivated(reason: Int) {
        val generation = fieldGeneration.getAndSet(NO_FIELD_GENERATION)
        if (generation == NO_FIELD_GENERATION) return
        AndroidNfcSessionRegistry.requestDisarm(
            generation,
            ProximityCloseReason.PEER_DISCONNECTED,
        )
    }

    /** Releases the service worker and invalidates any generation still owned by this service. */
    override fun onDestroy() {
        fieldGeneration.set(NO_FIELD_GENERATION)
        AndroidNfcSessionRegistry.current()?.let { session ->
            AndroidNfcSessionRegistry.requestDisarm(
                session.generation,
                ProximityCloseReason.PLATFORM_UNAVAILABLE,
            )
        }
        serviceScope.cancel()
        commands.close()
        super.onDestroy()
    }

    private data class PendingCommand(
        val session: AndroidNfcSessionRegistry.Session,
        val command: ByteArray,
    )

    private companion object {
        const val NO_FIELD_GENERATION = 0L
        val STATUS_WRONG_LENGTH = byteArrayOf(0x67, 0x00)
        val STATUS_CONDITIONS_NOT_SATISFIED = byteArrayOf(0x69, 0x85.toByte())
        val STATUS_UNKNOWN_ERROR = byteArrayOf(0x6f, 0x00)
    }
}

internal object AndroidNfcSessionRegistry {
    internal interface Router {
        suspend fun process(command: ByteArray): ImmutableBytes
        suspend fun deactivate(reason: ProximityCloseReason)
    }

    private class CommonRouter(private val delegate: NfcHostApduRouter) : Router {
        override suspend fun process(command: ByteArray): ImmutableBytes = delegate.process(command)
        override suspend fun deactivate(reason: ProximityCloseReason) = delegate.deactivate(reason)
    }

    class Session(
        val generation: Long,
        private val router: Router,
        parentJob: Job?,
    ) {
        private val parentCompleted = AtomicBoolean(parentJob?.isActive == false)
        private val completion = parentJob?.invokeOnCompletion {
            parentCompleted.set(true)
            requestDisarm(generation, ProximityCloseReason.CANCELLED)
        }

        val isParentCompleted: Boolean get() = parentCompleted.get()

        suspend fun process(command: ByteArray): ImmutableBytes {
            check(isCurrent(generation)) { "The Android NFC session is stale" }
            return router.process(command)
        }

        suspend fun close(reason: ProximityCloseReason) {
            completion?.dispose()
            try {
                router.deactivate(reason)
            } finally {
                AndroidNfcForegroundRouteRegistry.release(generation)
            }
        }

        fun disposeParentCompletion() {
            completion?.dispose()
        }
    }

    private val nextGeneration = AtomicLong(1)
    private val active = AtomicReference<Session?>(null)
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    suspend fun arm(router: NfcHostApduRouter, parentJob: Job?): Long = arm(CommonRouter(router), parentJob)

    internal suspend fun arm(router: Router, parentJob: Job?): Long {
        val generation = nextGeneration.getAndIncrement()
        if (generation <= 0) throw AndroidNfcGenerationExhaustedException()
        val session = Session(generation, router, parentJob)
        if (!active.compareAndSet(null, session)) {
            session.disposeParentCompletion()
            throw AndroidNfcSessionAlreadyActiveException()
        }
        if (session.isParentCompleted && active.compareAndSet(session, null)) {
            session.close(ProximityCloseReason.CANCELLED)
            throw CancellationException("The Android NFC host session scope is not active")
        }
        return generation
    }

    fun current(): Session? = active.get()
    fun isCurrent(generation: Long): Boolean = active.get()?.generation == generation

    fun requestDisarm(generation: Long, reason: ProximityCloseReason) {
        val session = take(generation) ?: return
        cleanupScope.launch { session.close(reason) }
    }

    suspend fun disarm(generation: Long, reason: ProximityCloseReason) {
        take(generation)?.close(reason)
    }

    private fun take(generation: Long): Session? {
        val session = active.get() ?: return null
        if (session.generation != generation || !active.compareAndSet(session, null)) return null
        return session
    }

    internal suspend fun resetForTest() {
        try {
            active.getAndSet(null)?.close(ProximityCloseReason.CANCELLED)
        } finally {
            AndroidNfcForegroundRouteRegistry.resetForTest()
        }
    }
}

private class AndroidNfcSessionAlreadyActiveException : IllegalStateException(
    "An Android NFC host session is already armed",
)

private class AndroidNfcGenerationExhaustedException : IllegalStateException(
    "The Android NFC generation counter is exhausted",
)
