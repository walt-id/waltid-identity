package id.walt.androidSample.app.features.walkthrough.components

import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import id.walt.androidSample.theme.WaltIdAndroidSampleTheme

@Composable
fun WaltSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick, modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.textButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurface,
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Text(text)
    }
}

@Preview
@Composable
private fun Preview() {
    WaltIdAndroidSampleTheme {
        WaltSecondaryButton(text = "Button text", onClick = { })
    }
}