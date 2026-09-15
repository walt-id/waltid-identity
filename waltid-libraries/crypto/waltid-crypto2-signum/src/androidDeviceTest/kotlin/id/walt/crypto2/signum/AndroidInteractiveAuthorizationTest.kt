package id.walt.crypto2.signum

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.test.platform.app.InstrumentationRegistry
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.*
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import java.util.UUID
import kotlin.test.*

/** Opt-in operator test: pass `-e wallet.authorization biometric|credential|cancel` to instrumentation. */
class AndroidInteractiveAuthorizationTest {
    @Test
    fun generatedAndImportedKeysUseTheSelectedSystemAuthorization() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val route = InstrumentationRegistry.getArguments().getString("wallet.authorization")
        assumeTrue("Requires an operator to approve or cancel the native prompt", route in setOf("biometric", "credential", "cancel"))
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, KeyAuthorizationTestActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as KeyAuthorizationTestActivity
        instrumentation.waitForIdleSync()
        try {
            val backend = AndroidSignumKeyBackend { activity }
            val sourceAlias = "authorization-source-${UUID.randomUUID()}"
            val spec = KeySpec.Ec(EcCurve.P256)
            val usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY)
            // Fixed test material is imported only after testing a separately generated key.
            val source = id.walt.crypto2.CryptoRuntime(id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders())
                .generateSoftwareKey(id.walt.crypto2.providers.GenerateSoftwareKeyRequest(KeyId(sourceAlias), spec, usages))
            val material = assertIs<EncodedKey.Jwk>(assertNotNull(source.capabilities.privateKeyExporter).exportPrivateKey())
            for (importing in listOf(false, true)) {
                val alias = "authorization-${UUID.randomUUID()}"
                val policy = SignumKeyPolicy(hardware = SignumHardwarePolicy.PREFERRED,
                    platform = SignumPlatformPolicy.AndroidKeystore(strongBox = SignumHardwarePolicy.DISCOURAGED),
                    authentication = SignumAuthenticationPolicy.UserPresence(allowNewBiometrics = true,
                        prompt = "WAL-749 ${if (importing) "imported" else "generated"}: $route"))
                try {
                    if (importing) backend.importPrivateKey(alias, material, spec, usages, policy)
                    else backend.create(alias, spec, usages, policy)
                    val reopened = assertNotNull(if (importing) backend.loadImportedKey(alias, spec, usages, policy)
                        else backend.load(alias, spec, usages, policy))
                    val data = "protected signing".encodeToByteArray()
                    val algorithm = SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256)
                    if (route == "cancel") assertFailsWith<SignumUserCancelledException> { reopened.sign(data, algorithm) }
                    else assertTrue(reopened.verify(data, reopened.sign(data, algorithm), algorithm))
                } finally { backend.delete(alias, policy) }
            }
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }
}

class KeyAuthorizationTestActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(android.widget.TextView(this).apply { text = "WAL-749 temporary-key authorization test" })
    }
}
