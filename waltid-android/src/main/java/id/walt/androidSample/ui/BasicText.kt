package id.walt.androidSample.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.walt.androidSample.R
import id.walt.androidSample.app.util.clickableWithoutRipple
import id.walt.androidSample.models.CopiedText
import id.walt.androidSample.theme.WaltIdAndroidSampleTheme

@Composable
internal fun BasicText(
    text: String,
    textToCopy: CopiedText? = null,
) {

    val ctx = LocalContext.current
    val interactionSource = remember { MutableInteractionSource() }

    Text(
        text = text,
        color = MaterialTheme.colorScheme.primary,
        fontSize = 12.sp,
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .padding(vertical = 10.dp)
            .clickableWithoutRipple(interactionSource) {
                textToCopy?.let { (label, toCopy) ->
                    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.apply {
                        val clip = ClipData.newPlainText(label, toCopy)
                        setPrimaryClip(clip)
                        Toast
                            .makeText(ctx, ctx.getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT)
                            .show()
                    }
                }
            }
    )
}

@Preview(showBackground = true)
@Composable
private fun Preview_BasicText() {
    WaltIdAndroidSampleTheme {
        BasicText(
            text = "Sample Text",
            textToCopy = CopiedText("Label", "Text to copy")
        )
    }
}