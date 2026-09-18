package id.walt.proximity.physical

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.view.WindowManager
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import id.walt.mdoc.proximity.mobile.AndroidMdocHostApduService
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*
import java.io.File
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

class PhysicalActivity : Activity() {
    override fun onCreate(state: android.os.Bundle?) {
        super.onCreate(state)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(TextView(this).apply {
            text = "Disposable proximity test host\nFollow the local runner for positioning."
            textSize = 22f
            setPadding(24, 64, 24, 24)
        })
    }
}

class PhysicalMdocService : AndroidMdocHostApduService()

/** Local control files may contain public engagement material; shared evidence never includes them. */
internal class PhysicalRun(private val role: String) : AutoCloseable {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    val context = instrumentation.targetContext
    private val arguments = InstrumentationRegistry.getArguments()
    val configuration = requireNotNull(arguments.getString("configuration"))
    private val runId = UUID.fromString(requireNotNull(arguments.getString("runId"))).toString()
    private val directory = File(context.filesDir, "proximity-physical/$runId")
    lateinit var activity: PhysicalActivity
        private set

    init {
        check(arguments.getString("physicalOptIn") == "physical-local") { "Explicit physical opt-in is required" }
        check(arguments.getString("controllerIsLocal") == "true") { "CI cannot run physical tests" }
        check(!arguments.getString("selectedDeviceId").isNullOrBlank()) { "An explicit device selector is required" }
        check(arguments.getString("fixture") == "synthetic-ada-v1") { "Only the disposable synthetic fixture is permitted" }
        check(arguments.getString("peerRevision") == PEER_REVISION) { "Pinned reader revision is required" }
        check(Build.VERSION.SDK_INT >= 30 && !Build.FINGERPRINT.startsWith("generic")
            && !Build.MODEL.contains("sdk", ignoreCase = true)
            && !Build.HARDWARE.contains("goldfish") && !Build.HARDWARE.contains("ranchu")) {
            "Physical tests require supported real hardware"
        }
        check(context.packageName == "id.walt.proximity.physical") { "The isolated physical test APK is required" }
        check(configuration in setOf("ble-gatt-central", "ble-gatt-peripheral", "ble-l2cap-central",
            "ble-l2cap-peripheral", "nfc-direct-disconnect", "nfc-ble-continuation")) { "Unsupported peer configuration" }
        val features = if (configuration.startsWith("nfc")) {
            listOf(if (role == "holder") PackageManager.FEATURE_NFC_HOST_CARD_EMULATION else PackageManager.FEATURE_NFC)
        } else listOf(PackageManager.FEATURE_BLUETOOTH_LE)
        check(features.all(context.packageManager::hasSystemFeature)) { "Required physical capability is absent" }
        directory.mkdirs()
    }

    fun launch() {
        val permissions = if (Build.VERSION.SDK_INT >= 31) listOf(
            "android.permission.BLUETOOTH_SCAN", "android.permission.BLUETOOTH_ADVERTISE",
            "android.permission.BLUETOOTH_CONNECT") else listOf("android.permission.ACCESS_FINE_LOCATION")
        check(permissions.all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }) {
            "The selected disposable test APK must have its required runtime permissions before the run"
        }
        activity = instrumentation.startActivitySync(Intent(context, PhysicalActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as PhysicalActivity
        event("$role-started", "configuration" to configuration)
    }

    fun onMain(block: () -> Unit) = instrumentation.runOnMainSync(block)

    fun event(name: String, vararg values: Pair<String, String>) {
        val data = buildJsonObject {
            put("role", role)
            put("event", name)
            put("elapsedNanos", SystemClock.elapsedRealtimeNanos())
            values.forEach { (key, value) -> put(key, value) }
        }
        val temporary = File(directory, "$name.tmp")
        temporary.writeText(data.toString())
        check(temporary.renameTo(File(directory, "$name.json"))) { "Unable to persist phase result" }
    }

    suspend fun input(name: String): JsonObject = withTimeout(60.seconds) {
        val file = File(directory, "$name.json")
        while (!file.isFile) delay(50)
        Json.parseToJsonElement(file.readText()).jsonObject
    }

    override fun close() {
        if (::activity.isInitialized) onMain { activity.finish() }
    }

    companion object {
        const val PEER_REVISION = "7c0988bee3384d13a0732e0c33336ae0faf3b863"
    }
}
