package id.walt.walletdemo.compose.android

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil3.SingletonImageLoader
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WalletImageLoaderInitializationTest {
    @Test
    fun imageUseBeforeMainActivityAndRecreationKeepsTheConfiguredLoader() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // Provider activities and offer-art prefetch can use Coil before the main wallet UI.
        val loader = SingletonImageLoader.get(context)

        ActivityScenario.launch(MainActivity::class.java).use { activity ->
            activity.onActivity { assertSame(loader, SingletonImageLoader.get(it)) }
            activity.recreate()
            activity.onActivity { assertSame(loader, SingletonImageLoader.get(it)) }
        }
    }
}
