package id.walt.crypto2.signum

import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class AndroidSignumKeyBackendDeviceTest {
    @Test
    fun importedP256RetainsOriginalKeyAfterDeletionAndReimport() = runTest {
        exerciseNativePrivateImport(AndroidSignumKeyBackend(), SignumKeyPolicy(hardware = SignumHardwarePolicy.REQUIRED,
            platform = SignumPlatformPolicy.AndroidKeystore(strongBox = SignumHardwarePolicy.DISCOURAGED)))
    }

    @Test
    fun platformKeySurvivesProviderRestart() = runTest {
        exercisePlatformSignumBackend(AndroidSignumKeyBackend(), AndroidSignumKeyBackend())
    }
}
