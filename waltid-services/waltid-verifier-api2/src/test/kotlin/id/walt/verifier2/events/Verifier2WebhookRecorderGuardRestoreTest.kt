package id.walt.verifier2.events

import id.walt.webdatafetching.ssrf.PrivateNetworkGuardSettings
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse

class Verifier2WebhookRecorderGuardRestoreTest {

    private val previousAllowLoopback = System.getProperty("walt.ssrf.allowLoopback")

    @AfterTest
    fun releaseLoopbackChoice() {
        PrivateNetworkGuardSettings.clearAllowLoopbackChoice()
        if (previousAllowLoopback == null) {
            System.clearProperty("walt.ssrf.allowLoopback")
        } else {
            System.setProperty("walt.ssrf.allowLoopback", previousAllowLoopback)
        }
    }

    @Test
    fun `closing the recorder restores an unset loopback choice`() {
        System.setProperty("walt.ssrf.allowLoopback", "true")
        PrivateNetworkGuardSettings.clearAllowLoopbackChoice()
        Verifier2WebhookRecorder().start().close()
        assertFalse(PrivateNetworkGuardSettings.allowLoopbackExplicit)
    }
}
