package id.walt.verifier2.events

import id.walt.webdatafetching.ssrf.PrivateNetworkGuardSettings
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse

class Verifier2WebhookRecorderGuardRestoreTest {

    @AfterTest
    fun releaseLoopbackChoice() {
        System.clearProperty("walt.ssrf.allowLoopback")
        PrivateNetworkGuardSettings.clearAllowLoopbackChoice()
    }

    @Test
    fun `closing the recorder restores an unset loopback choice`() {
        System.setProperty("walt.ssrf.allowLoopback", "true")
        PrivateNetworkGuardSettings.clearAllowLoopbackChoice()
        Verifier2WebhookRecorder().start().close()
        assertFalse(PrivateNetworkGuardSettings.allowLoopbackExplicit)
    }
}
