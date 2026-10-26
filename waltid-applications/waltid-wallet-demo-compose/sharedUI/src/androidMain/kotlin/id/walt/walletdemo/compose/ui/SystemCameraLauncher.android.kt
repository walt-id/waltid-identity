package id.walt.walletdemo.compose.ui

import android.content.Intent
import android.provider.MediaStore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
internal actual fun rememberSystemCameraLauncher(): (() -> Unit)? {
    val context = LocalContext.current
    return remember(context) {
        val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
        if (intent.resolveActivity(context.packageManager) == null) null
        else ({ context.startActivity(intent) })
    }
}
