package id.walt.crypto2.signum

import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class AndroidSignumKeyBackendDeviceTest {
    @Test
    fun platformKeySurvivesProviderRestart() = runTest {
        exercisePlatformSignumBackend(AndroidSignumKeyBackend(), AndroidSignumKeyBackend())
    }
}
