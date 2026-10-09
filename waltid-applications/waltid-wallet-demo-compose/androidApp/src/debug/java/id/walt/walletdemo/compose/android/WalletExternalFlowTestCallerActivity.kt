package id.walt.walletdemo.compose.android

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** Debug-only opaque caller used to exercise a real translucent Activity and its return stack. */
class WalletExternalFlowTestCallerActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            setPadding(24, 100, 24, 24)
            setBackgroundColor(Color.rgb(18, 61, 90))
            addView(TextView(context).apply {
                text = "External test caller"
                textSize = 24f
                setTextColor(Color.WHITE)
            })
            addView(Button(context).apply {
                isAllCaps = false
                text = "Open wallet request"
                setOnClickListener {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(intent.getStringExtra("offer")))
                        .setPackage(packageName)
                        .putExtra(WALLET_SIGNING_PROTECTION_MODE_EXTRA, "disabled"))
                }
            })
        })
    }
}
