package id.walt.androidSample.app.features.walkthrough.components

import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import id.walt.androidSample.theme.WaltIdAndroidSampleTheme

// TODO handle max width via MaterialTheme.spacing
@Composable
fun WaltPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick, modifier = modifier,
        colors = ButtonDefaults.textButtonColors(
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        enabled = enabled
    ) {
        Text(text)
    }
}


@Preview
@Composable
private fun Preview() {
    WaltIdAndroidSampleTheme {
        WaltPrimaryButton(text = "Button text", onClick = { })
    }
}