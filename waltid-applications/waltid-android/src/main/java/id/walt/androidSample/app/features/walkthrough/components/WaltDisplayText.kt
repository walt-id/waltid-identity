package id.walt.androidSample.app.features.walkthrough.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import id.walt.androidSample.theme.WaltIdAndroidSampleTheme

@Composable
fun WaltDisplayText(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier
            .padding(vertical = 8.dp)
            .verticalScroll(rememberScrollState())
    )
}

@Preview
@Composable
private fun Preview() {
    WaltIdAndroidSampleTheme {
        WaltDisplayText(text = "Hello, World!")
    }
}