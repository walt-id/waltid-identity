package id.walt.crypto

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.KeyUseAuthorizationPolicy
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AndroidKeyInteractionContextTestActivity : FragmentActivity()

@RunWith(AndroidJUnit4::class)
class AndroidKeyInteractionContextTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(AndroidKeyInteractionContextTestActivity::class.java)

    @Test
    fun protectedInteractionContextRequiresResumedActivity() {
        val scenario = activityRule.scenario
        val activity = scenario.withActivity()
        val options = AndroidKey.Options(
            keyType = KeyType.secp256r1,
            keyUseAuthorizationPolicy = KeyUseAuthorizationPolicy.BiometricCurrentSet,
            interactionContextProvider = { activity },
        )

        scenario.moveToState(Lifecycle.State.CREATED)
        assertNull(options.interactionContext)

        scenario.moveToState(Lifecycle.State.STARTED)
        assertNull(options.interactionContext)

        scenario.moveToState(Lifecycle.State.RESUMED)
        assertEquals(activity, options.interactionContext)
    }

    private fun <A : FragmentActivity> androidx.test.core.app.ActivityScenario<A>.withActivity(): A {
        lateinit var activity: A
        onActivity { activity = it }
        return activity
    }
}
