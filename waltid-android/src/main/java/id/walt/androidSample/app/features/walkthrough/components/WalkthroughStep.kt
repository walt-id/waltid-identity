package id.walt.androidSample.app.features.walkthrough.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import id.walt.androidSample.theme.WaltIdAndroidSampleTheme

@Composable
fun WalkthroughStep(
    title: String,
    description: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp)
            .padding(top = 32.dp, bottom = 12.dp),
    ) {
        WaltLogo()
        Spacer(Modifier.height(32.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(Modifier.height(32.dp))
        Text(
            text = description,
            textAlign = TextAlign.Start,
        )
        content()
    }
}


@Preview
@Composable
private fun Preview() {
    WaltIdAndroidSampleTheme {
        WalkthroughStep("Step 1 - Generate a Key", "Choose between using either the RSA or ECDSA algorithm to generate a key pair.") {
            // Content
        }
    }
}