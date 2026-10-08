package id.walt.ktorauthnz

import id.walt.ktorauthnz.security.PasswordHashingConfiguration
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PepperWarningTest {
    @Test
    fun `the public default pepper is warned about, an own one is not`() {
        assertNotNull(pepperWarning(PasswordHashingConfiguration()))
        assertNull(pepperWarning(PasswordHashingConfiguration(pepper = "my-deployment-secret")))
    }
}
