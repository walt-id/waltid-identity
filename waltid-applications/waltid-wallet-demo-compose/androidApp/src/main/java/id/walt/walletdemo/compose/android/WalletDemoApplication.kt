package id.walt.walletdemo.compose.android

import android.app.Application
import id.walt.walletdemo.compose.ui.installWalletImageLoader

class WalletDemoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        installWalletImageLoader()
    }
}
