package id.walt.walletdemo.compose.android

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import id.walt.walletdemo.compose.ui.WalletDemoSharingReviewScreen
import id.walt.walletdemo.compose.ui.WalletProviderStatusScreen

/** Credential Manager owns the result; navigation and request work belong to a retained model. */
class DigitalCredentialProviderActivity : FragmentActivity() {
    private lateinit var model: DigitalCredentialProviderModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        model = ViewModelProvider(this, viewModelFactory {
            initializer { DigitalCredentialProviderModel(applicationContext) }
        })[DigitalCredentialProviderModel::class.java]
        model.attach(this)
        model.start(intent, restored = savedInstanceState != null)
        setContent {
            LaunchedEffect(model.result) {
                model.takeResult()?.let { setResult(it.code, it.data); finish() }
            }
            val owner = model.reviewController
            if (owner != null && model.failure == null) {
                WalletDemoSharingReviewScreen(
                    review = owner.review, controller = owner, title = model.title,
                    enabled = !model.submitting,
                    onSubmit = model::submit, onCancel = model::cancel,
                    onBackAtRoot = model::back,
                )
            } else WalletProviderStatusScreen(
                title = if (model.failure != null) "Unable to share" else if (model.submitting) "Sharing credentials…" else "Preparing request…",
                message = model.failure, enabled = !model.submitting, onClose = model::cancel, onDismiss = model::back,
            )
        }
    }

    override fun onDestroy() {
        if (::model.isInitialized) model.detach(this)
        super.onDestroy()
    }
}
