package id.walt.wallet2.mobile

import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import id.walt.certificate.x509.X509CertificateUtil
import id.walt.certificate.x509.truststore.InMemoryTrustStore
import id.walt.crypto2.keys.*
import id.walt.itb.*
import id.walt.openid4vp.clientidprefix.ClientIdTrustConfiguration
import id.walt.wallet2.data.Wallet
import id.walt.wallet2.persistence.keys.*
import id.walt.wallet2.stores.inmemory.InMemoryCredentialStore
import id.walt.wallet2.stores.inmemory.InMemoryKeyStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.github.oshai.kotlinlogging.DirectLoggerFactory
import io.github.oshai.kotlinlogging.KotlinLoggingConfiguration
import io.github.oshai.kotlinlogging.Level
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.util.UUID

/** Manual live ITB fixture; never opted in by ordinary device CI. All disposable keys are deleted. */
class ItbNativeWalletDeviceTest {
    @Test
    fun runOwnedItbSessions() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val token = args.getString("wallet.itb.token")
        assumeTrue("Requires an explicitly configured ITB operator", token != null)
        require(token!!.matches(Regex("[0-9a-f]{64}")))
        val port = requireNotNull(args.getString("wallet.itb.port")).toInt()
        require(port in 1024..65535)
        // Do not log offers, tokens, keys or request bodies.
        KotlinLoggingConfiguration.loggerFactory = DirectLoggerFactory
        KotlinLoggingConfiguration.direct.logLevel = Level.OFF
        KotlinLoggingConfiguration.logStartupMessage = false
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, ScaAuthorizationTestActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        ) as ScaAuthorizationTestActivity
        instrumentation.runOnMainSync {
            activity.setContentView(android.widget.TextView(activity).apply {
                text = "WAL-1423 live ITB test. Approve only the synthetic test operations requested by the operator."
            })
        }
        val provider = AndroidPlatformKeyProvider(activity) { activity }
        var generated: ManagedKey? = null
        try {
            withTimeout(30 * 60_000L) {
                ServerSocket(port, 1, InetAddress.getByName("127.0.0.1")).use { server ->
                    server.soTimeout = 250
                    val socket = withContext(Dispatchers.IO) {
                        while (true) {
                            ensureActive()
                            try { return@withContext server.accept() } catch (_: SocketTimeoutException) { }
                        }
                        error("Unreachable")
                    }
                    socket.use {
                        val init = withTimeout(10_000) { ItbDeviceWire.read(socket) }
                        ItbDeviceWire.authenticate(init, token)
                        val origin = ItbDeviceWire.approvedOrigin(init.getValue("origin").jsonPrimitive.content)
                        val pem = init.getValue("trustPem").jsonPrimitive.content
                        val certificates = Regex("-----BEGIN CERTIFICATE-----[\\s\\S]*?-----END CERTIFICATE-----")
                            .findAll(pem).map { X509CertificateUtil.parseCertificatePem(it.value) }.toList()
                        require(certificates.isNotEmpty())
                        val requirements = WalletKeyRequirements(
                            KeySpec.Ec(EcCurve.P256), setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
                            KeyUseAuthorizationPolicy.BiometricCurrentSet, WalletKeyProtection.HardwareRequired,
                        )
                        check(provider.preflight(requirements) is KeyUseAuthorizationSupport.Supported)
                        val key = provider.generateManagedKey(WalletKeyCreationRequest(
                            KeyId("wal1423-itb-${UUID.randomUUID()}"), requirements,
                            KeyUseAuthorizationPrompt("Authorize this WAL-1423 ITB test operation"),
                        )).also { generated = it }
                        val restored = provider.restoreManagedKey(key.storedKey) as PlatformManagedKeyRestoration.Restored
                        val wallet = Wallet(
                            id = "itb-native-${UUID.randomUUID()}",
                            keyStores = listOf(InMemoryKeyStore().apply { addCrypto2Key(restored.key) }),
                            credentialStores = listOf(InMemoryCredentialStore()),
                        )
                        HttpClient(Android) {
                            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
                            install(HttpTimeout) { requestTimeoutMillis = 30_000; connectTimeoutMillis = 10_000 }
                        }.use { client ->
                            val driver = ItbWalletDriver(
                                wallet, client, origin, ClientIdTrustConfiguration(x509TrustAnchors = InMemoryTrustStore(certificates)),
                                authorize = { url, callback -> ItbReferenceAuthorization.resolve(client, origin, url, callback) },
                                scaAuthorizer = NativeScaPresentationAuthorizer(provider),
                            )
                            ItbDeviceWire.write(socket, buildJsonObject {
                                put("ready", true); put("provider", "android-native-biometric")
                            })
                            try { ItbDeviceWire.serve(socket, driver::execute) }
                            catch (ended: CancellationException) {
                                currentCoroutineContext().ensureActive() // A host disconnect ends this fixture; outer timeout still fails.
                            }
                        }
                    }
                }
            }
        } finally {
            withContext(NonCancellable) {
                generated?.let { provider.deleteManagedKey(it.storedKey) }
                instrumentation.runOnMainSync { activity.finish() }
            }
        }
    }
}
