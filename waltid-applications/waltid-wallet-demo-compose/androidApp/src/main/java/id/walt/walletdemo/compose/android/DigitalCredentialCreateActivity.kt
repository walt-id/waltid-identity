package id.walt.walletdemo.compose.android

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import id.walt.walletdemo.compose.ui.WalletDemoOfferCreateScreen
import id.walt.walletdemo.compose.ui.WalletReviewPresentation

/** Translucent provider host. Its retained model owns the request, drafts, browser return and result. */
class DigitalCredentialCreateActivity : FragmentActivity() {
    private lateinit var model: DigitalCredentialCreateModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        model = ViewModelProvider(this, viewModelFactory {
            initializer { DigitalCredentialCreateModel(applicationContext) }
        })[DigitalCredentialCreateModel::class.java]
        model.attach(this)
        model.start(intent, restored = savedInstanceState != null)
        setContent {
            LaunchedEffect(model.result) {
                model.takeResult()?.let { setResult(it.code, it.data); finish() }
            }
            WalletDemoOfferCreateScreen(
                state = model.state, draft = model.draft, presentation = WalletReviewPresentation.Sheet,
                onAccept = model::accept, onDecline = model::cancel, onDismiss = model::back,
                onCancelAuthorization = model::cancel, onDone = model::done,
                onResumeDeferred = model::resume, onRefresh = model::refresh,
            )
        }
    }

    override fun onDestroy() {
        if (::model.isInitialized) model.detach(this)
        super.onDestroy()
    }
}
