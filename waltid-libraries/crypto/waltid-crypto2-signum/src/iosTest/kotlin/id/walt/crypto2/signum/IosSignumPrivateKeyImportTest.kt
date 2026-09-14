package id.walt.crypto2.signum

import kotlin.test.Test
import kotlin.test.assertFalse

class IosSignumPrivateKeyImportTest {
    @Test fun enclavePrivateImportIsNotOffered() {
        assertFalse(IosSignumKeyBackend().supportsImport(id.walt.crypto2.keys.KeySpec.Ec(id.walt.crypto2.keys.EcCurve.P256),
            setOf(id.walt.crypto2.keys.KeyUsage.SIGN, id.walt.crypto2.keys.KeyUsage.VERIFY),
            SignumKeyPolicy(hardware = SignumHardwarePolicy.REQUIRED)))
    }
}
