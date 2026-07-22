package id.walt.crypto2.signum

import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class IosSignumKeyBackendTest {
    @Test
    fun platformKeySurvivesProviderRestart() = runTest {
        exercisePlatformSignumBackend(IosSignumKeyBackend(), IosSignumKeyBackend())
    }
}
