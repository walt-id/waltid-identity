package id.walt.mdoc.proximity.mobile

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.net.wifi.aware.AttachCallback
import android.net.wifi.aware.Characteristics
import android.net.wifi.aware.DiscoverySessionCallback
import android.net.wifi.aware.PeerHandle
import android.net.wifi.aware.PublishConfig
import android.net.wifi.aware.PublishDiscoverySession
import android.net.wifi.aware.WifiAwareDataPathSecurityConfig
import android.net.wifi.aware.WifiAwareManager
import android.net.wifi.aware.WifiAwareNetworkSpecifier
import android.net.wifi.aware.WifiAwareSession
import android.os.Build
import android.os.Handler
import android.os.Looper
import id.walt.mdoc.proximity.ProximityCloseReason
import id.walt.mdoc.proximity.ProximityError
import id.walt.mdoc.proximity.ProximityException
import id.walt.mdoc.proximity.ReaderSelectedTransportProvider
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.net.Inet6Address
import java.net.ServerSocket
import java.net.Socket

/** Android Wi-Fi Aware preflight and holder-publisher factory. */
public class AndroidWifiAwareProximityTransportFactory(
    context: Context,
) : WifiAwareProximityTransportFactory {
    private val applicationContext: Context = context.applicationContext

    override suspend fun capability(): WifiAwareProximityAvailability =
        AndroidWifiAwarePlatformAdapter(applicationContext).capability()

    override fun create(
        configuration: WifiAwareProximityTransportConfiguration,
    ): ReaderSelectedTransportProvider = DefaultWifiAwareProximityTransportProvider(
        configuration,
        AndroidWifiAwarePlatformAdapter(applicationContext),
    )
}

internal class AndroidWifiAwarePlatformAdapter(
    private val context: Context,
) : WifiAwarePlatformAdapter {
    private val awareManager: WifiAwareManager? = context.getSystemService(WifiAwareManager::class.java)
    private val wifiManager: WifiManager? = context.getSystemService(WifiManager::class.java)

    @SuppressLint("MissingPermission")
    override suspend fun capability(): WifiAwareProximityAvailability {
        if (Build.VERSION.SDK_INT < MINIMUM_EXPLICIT_CIPHER_API) {
            return unavailable(
                "wifi_aware_api_unsupported",
                "Android API 33 or newer is required for explicit Wi-Fi Aware cipher selection",
            )
        }
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE)) {
            return unavailable(
                "wifi_aware_feature_missing",
                "This Android device does not declare Wi-Fi Aware support",
            )
        }
        val manager = awareManager ?: return unavailable(
            "wifi_aware_manager_unavailable",
            "The Android Wi-Fi Aware system service is unavailable",
        )
        missingPermission()?.let { return it }
        if (!manager.isAvailable) {
            return unavailable(
                "wifi_aware_radio_unavailable",
                "Wi-Fi Aware is currently unavailable; Wi-Fi, location state, tethering, or another radio mode may be blocking it",
            )
        }
        manager.characteristics?.let { characteristics ->
            if (characteristics.supportedCipherSuites and Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_SK_128 == 0
            ) {
                return unavailable(
                    "wifi_aware_ncs_sk_128_unsupported",
                    "This Android device does not support the mandatory NCS-SK-128 cipher suite",
                )
            }
        }
        manager.availableAwareResources?.let { resources ->
            if (resources.availablePublishSessionsCount <= 0 || resources.availableDataPathsCount <= 0) {
                return unavailable(
                    "wifi_aware_resources_unavailable",
                    "No Wi-Fi Aware publish session or data path is currently available",
                )
            }
        }
        val wifi = wifiManager ?: return unavailable(
            "wifi_aware_manager_unavailable",
            "The Android Wi-Fi system service is unavailable",
        )
        if (!wifi.is24GHzBandSupported) {
            return unavailable(
                "wifi_aware_bands_unavailable",
                "The mandatory 2.4 GHz Wi-Fi Aware operating band is unavailable",
            )
        }
        return WifiAwareProximityAvailability.Available
    }

    @SuppressLint("MissingPermission")
    override suspend fun preparePublisher(
        serviceName: String,
        passphrase: String,
        sessionScope: CoroutineScope,
    ): WifiAwarePreparedPlatformPublisher {
        val availability = capability()
        if (availability !is WifiAwareProximityAvailability.Available) {
            val unavailable = availability as WifiAwareProximityAvailability.Unavailable
            throw ProximityException(ProximityError.Capability(unavailable.code, unavailable.message))
        }
        require(serviceName.length == 32 && serviceName.all { it in '0'..'9' || it in 'A'..'F' }) {
            "The ISO Wi-Fi Aware service name must be 32 uppercase hexadecimal characters"
        }
        WifiAwareProtocol.requireValidPassphrase(passphrase)
        return AndroidWifiAwarePreparedPublisher.create(
            context = context,
            awareManager = requireNotNull(awareManager),
            connectivityManager = requireNotNull(context.getSystemService(ConnectivityManager::class.java)),
            wifiManager = requireNotNull(wifiManager),
            serviceName = serviceName,
            passphrase = passphrase,
            sessionScope = sessionScope,
        )
    }

    private fun missingPermission(): WifiAwareProximityAvailability.Unavailable? {
        fun granted(permission: String): Boolean =
            context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        val normalPermissions = listOf(
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.CHANGE_NETWORK_STATE,
            Manifest.permission.INTERNET,
        )
        if (normalPermissions.any { !granted(it) }) {
            return unavailable(
                "wifi_aware_manifest_permission_missing",
                "A required Android Wi-Fi or network manifest permission is missing",
            )
        }
        if (!granted(Manifest.permission.NEARBY_WIFI_DEVICES)) {
            return unavailable(
                "wifi_aware_nearby_permission_missing",
                "Android Nearby Wi-Fi devices permission is not granted",
            )
        }
        if (Build.VERSION.SDK_INT >= LOCAL_NETWORK_PERMISSION_API && !granted(ACCESS_LOCAL_NETWORK)) {
            return unavailable(
                "wifi_aware_local_network_permission_missing",
                "Android local-network permission is not granted",
            )
        }
        return null
    }

    private fun unavailable(code: String, message: String) =
        WifiAwareProximityAvailability.Unavailable(implemented = true, code = code, message = message)

    private companion object {
        const val MINIMUM_EXPLICIT_CIPHER_API = 33
        const val LOCAL_NETWORK_PERMISSION_API = 37
        const val ACCESS_LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"
    }
}

private class AndroidWifiAwarePreparedPublisher private constructor(
    private val context: Context,
    private val connectivityManager: ConnectivityManager,
    override val supportedBands: WifiAwareSupportedBands,
    private val passphrase: String,
) : WifiAwarePreparedPlatformPublisher {
    private val lock = Any()
    private val closed = atomic(false)
    private val connectionAwaited = atomic(false)
    private val peer = CompletableDeferred<PeerHandle>()
    private val receiver = WifiAwareNativeResource<Closeable>()
    private val awareSession = WifiAwareNativeResource<WifiAwareSession>()
    private val publishSession = WifiAwareNativeResource<PublishDiscoverySession>()
    private var serverSocket: BlockingSocket<ServerSocket>? = null
    private var rawConnection: AndroidWifiAwareRawConnection? = null
    private val networkRegistration = WifiAwareNativeResource<Closeable>()
    private var sessionHandle: kotlinx.coroutines.DisposableHandle? = null

    @SuppressLint("MissingPermission")
    override suspend fun awaitConnection(): WifiAwareRawConnection = coroutineScope {
        check(connectionAwaited.compareAndSet(expect = false, update = true)) {
            "A prepared Wi-Fi Aware publisher accepts one reader"
        }
        ensureOpen()
        val peer = peer.await()
        val discovery = publishSession.await()
        val server = BlockingSocket(ServerSocket(0))
        synchronized(lock) {
            if (closed.value) {
                server.close()
                throw platformFailure("wifi_aware_closed", "Wi-Fi Aware publisher is closed")
            }
            serverSocket = server
        }
        val security = sharedKeySecurity(passphrase)
        val specifier = WifiAwareNetworkSpecifier.Builder(discovery, peer)
            .setDataPathSecurityConfig(security)
            .setPort(server.socket.localPort)
            .setTransportProtocol(TCP_PROTOCOL_NUMBER)
            .build()
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI_AWARE)
            .setNetworkSpecifier(specifier)
            .build()
        val available = CompletableDeferred<Unit>()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                available.complete(Unit)
            }

            override fun onUnavailable() {
                available.completeExceptionally(
                    platformFailure("wifi_aware_network_unavailable", "Android could not establish the Wi-Fi Aware data path")
                )
                close(ProximityCloseReason.PLATFORM_UNAVAILABLE)
            }

            override fun onLost(network: Network) {
                available.completeExceptionally(
                    platformFailure("wifi_aware_network_lost", "The Wi-Fi Aware data path was lost")
                )
                close(ProximityCloseReason.PEER_DISCONNECTED)
            }
        }
        var accepted: kotlinx.coroutines.Deferred<BlockingSocket<Socket>>? = null
        try {
            ensureOpen()
            connectivityManager.requestNetwork(request, callback)
            // If close raced registration, install disposes this exact registration immediately.
            networkRegistration.install(Closeable { connectivityManager.unregisterNetworkCallback(callback) })
            ensureOpen()
            accepted = async {
                server.run(disposeResult = { it.close() }) { listener ->
                    val raw = AndroidWifiAwareRawConnection(BlockingSocket(listener.accept()))
                    synchronized(lock) {
                        if (closed.value) {
                            raw.close(ProximityCloseReason.CANCELLED)
                            throw platformFailure("wifi_aware_closed", "Wi-Fi Aware publisher closed during accept")
                        }
                        rawConnection = raw
                    }
                    raw.ownedSocket
                }
            }
            discovery.sendMessage(peer, CONNECTION_READY_MESSAGE_ID, ByteArray(0))
            available.await()
            val ownedSocket = accepted.await()
            val socket = ownedSocket.socket
            require(socket.inetAddress is Inet6Address && socket.inetAddress.isLinkLocalAddress) {
                "Wi-Fi Aware TCP peer must use a link-local IPv6 address"
            }
            socket.tcpNoDelay = true
            server.close()
            synchronized(lock) {
                if (closed.value) {
                    ownedSocket.close()
                    throw platformFailure("wifi_aware_closed", "Wi-Fi Aware publisher closed while accepting the reader")
                }
                serverSocket = null
                checkNotNull(rawConnection)
            }
        } catch (failure: Throwable) {
            // Close accept/read resources before coroutineScope joins a blocked IO worker.
            close(if (failure is kotlinx.coroutines.CancellationException) ProximityCloseReason.CANCELLED
                else ProximityCloseReason.PLATFORM_UNAVAILABLE)
            accepted?.cancel()
            throw failure
        }
    }

    override fun close(reason: ProximityCloseReason) {
        if (!closed.compareAndSet(expect = false, update = true)) return
        sessionHandle?.dispose()
        peer.cancel()
        val exact = synchronized(lock) {
            Pair(serverSocket.also { serverSocket = null }, rawConnection.also { rawConnection = null })
        }
        exact.first?.close()
        exact.second?.close(reason)
        networkRegistration.close()
        publishSession.close()
        awareSession.close()
        receiver.close()
    }

    private fun ensureOpen() = check(!closed.value) { "Wi-Fi Aware publisher is closed" }

    companion object {
        private const val TCP_PROTOCOL_NUMBER = 6
        private const val CONNECTION_READY_MESSAGE_ID = 1

        @SuppressLint("MissingPermission", "UnspecifiedRegisterReceiverFlag")
        suspend fun create(
            context: Context,
            awareManager: WifiAwareManager,
            connectivityManager: ConnectivityManager,
            wifiManager: WifiManager,
            serviceName: String,
            passphrase: String,
            sessionScope: CoroutineScope,
        ): AndroidWifiAwarePreparedPublisher {
            val endpoint = AndroidWifiAwarePreparedPublisher(
                context = context,
                connectivityManager = connectivityManager,
                supportedBands = supportedBands(wifiManager),
                passphrase = passphrase,
            )
            endpoint.sessionHandle = sessionScope.coroutineContext[Job]?.invokeOnCompletion {
                endpoint.close(ProximityCloseReason.CANCELLED)
            }
            val stateReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (!awareManager.isAvailable) {
                        endpoint.peer.completeExceptionally(platformFailure(
                            "wifi_aware_radio_lost", "Wi-Fi Aware became unavailable during the session",
                        ))
                        endpoint.close(ProximityCloseReason.PLATFORM_UNAVAILABLE)
                    }
                }
            }
            try {
                val filter = IntentFilter(WifiAwareManager.ACTION_WIFI_AWARE_STATE_CHANGED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(stateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(stateReceiver, filter)
            }
                endpoint.receiver.install(Closeable { context.unregisterReceiver(stateReceiver) })
                endpoint.ensureOpen()
                if (!awareManager.isAvailable) {
                    throw platformFailure("wifi_aware_radio_unavailable", "Wi-Fi Aware is unavailable")
                }
                awareManager.attach(object : AttachCallback() {
                    override fun onAttached(session: WifiAwareSession) {
                        endpoint.awareSession.install(session)
                    }

                    override fun onAttachFailed() {
                        endpoint.awareSession.fail(
                            platformFailure("wifi_aware_attach_failed", "Android Wi-Fi Aware attach failed")
                        )
                    }
                }, Handler(Looper.getMainLooper()))
                val awareSession = endpoint.awareSession.await()
                val characteristics = awareManager.characteristics
                    ?: throw platformFailure(
                        "wifi_aware_characteristics_unavailable",
                        "Android did not expose Wi-Fi Aware characteristics after attach",
                    )
                require(characteristics.supportedCipherSuites and
                    Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_SK_128 != 0) {
                    "Android Wi-Fi Aware no longer reports NCS-SK-128"
                }
                awareManager.availableAwareResources?.let { resources ->
                    require(resources.availablePublishSessionsCount > 0 && resources.availableDataPathsCount > 0) {
                        "Android Wi-Fi Aware resources were exhausted before publish"
                    }
                }
                val security = sharedKeySecurity(passphrase)
                val config = PublishConfig.Builder()
                    .setServiceName(serviceName)
                    .setDataPathSecurityConfig(security)
                    .build()
                awareSession.publish(config, object : DiscoverySessionCallback() {
                    override fun onPublishStarted(session: PublishDiscoverySession) {
                        endpoint.publishSession.install(session)
                    }

                    override fun onSessionConfigFailed() {
                        endpoint.publishSession.fail(
                            platformFailure("wifi_aware_publish_failed", "Android Wi-Fi Aware publish failed")
                        )
                    }

                    override fun onSessionTerminated() {
                        endpoint.peer.completeExceptionally(platformFailure(
                            "wifi_aware_publish_lost", "Android Wi-Fi Aware publish session ended",
                        ))
                        endpoint.close(ProximityCloseReason.PLATFORM_UNAVAILABLE)
                    }

                    override fun onMessageReceived(peerHandle: PeerHandle, message: ByteArray) {
                        // This endpoint accepts one reader; later discovery notifications carry no payload to queue.
                        endpoint.peer.complete(peerHandle)
                    }
                }, Handler(Looper.getMainLooper()))
                endpoint.publishSession.await()
                endpoint.ensureOpen()
                return endpoint
            } catch (failure: Throwable) {
                withContext(NonCancellable) { endpoint.close(ProximityCloseReason.PLATFORM_UNAVAILABLE) }
                throw failure
            }
        }

        private fun supportedBands(wifiManager: WifiManager): WifiAwareSupportedBands {
            check(wifiManager.is24GHzBandSupported) { "The mandatory 2.4 GHz Wi-Fi Aware band is unavailable" }
            // Android exposes general 5 GHz Wi-Fi support but no public pre-data-path Aware band capability.
            // Advertise only NAN's mandatory 2.4 GHz baseline rather than overclaiming the optional 5 GHz band.
            return WifiAwareSupportedBands.fromBytes(byteArrayOf(0x04))
        }

        private fun sharedKeySecurity(passphrase: String): WifiAwareDataPathSecurityConfig =
            WifiAwareDataPathSecurityConfig.Builder(
                Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_SK_128,
            ).setPskPassphrase(passphrase).build()

        private fun platformFailure(code: String, message: String): ProximityException =
            ProximityException(ProximityError.Transport(code, message))
    }
}

internal class AndroidWifiAwareRawConnection(
    val ownedSocket: BlockingSocket<Socket>,
) : WifiAwareRawConnection {
    private val closed = atomic(false)
    private val closure = CompletableDeferred<ProximityCloseReason>()

    override suspend fun awaitClosed(): ProximityCloseReason = closure.await()

    override suspend fun read(maximumBytes: Int): ByteArray? = ownedSocket.run { socket ->
        require(maximumBytes > 0)
        val target = ByteArray(maximumBytes)
        val count = socket.getInputStream().read(target)
        when {
            count < 0 -> null
            count == target.size -> target
            else -> target.copyOf(count)
        }
    }

    override suspend fun write(bytes: ByteArray): Unit = ownedSocket.run { socket ->
        socket.getOutputStream().apply {
            write(bytes)
            flush()
        }
    }

    override fun close(reason: ProximityCloseReason) {
        if (closed.compareAndSet(expect = false, update = true)) {
            closure.complete(reason)
            ownedSocket.close()
        }
    }
}

private fun platformFailure(code: String, message: String): ProximityException =
    ProximityException(ProximityError.Transport(code, message))
