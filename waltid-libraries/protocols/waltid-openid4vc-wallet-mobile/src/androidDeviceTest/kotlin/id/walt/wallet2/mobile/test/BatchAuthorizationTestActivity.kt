package id.walt.wallet2.mobile.test

import android.app.Activity
import android.os.Bundle
import kotlinx.coroutines.CompletableDeferred

/** Test-only custom-scheme receiver for the real browser authorization callback. */
class BatchAuthorizationTestActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent.dataString?.let { callback.complete(it) }
        finish()
    }

    companion object {
        var callback = CompletableDeferred<String>()
    }
}
