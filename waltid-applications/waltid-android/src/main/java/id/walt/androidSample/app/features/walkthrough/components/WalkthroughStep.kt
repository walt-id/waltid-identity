package id.walt.androidSample.app.features.walkthrough.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import id.walt.androidSample.theme.WaltIdAndroidSampleTheme

@Composable
fun WalkthroughStep(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp)
            .padding(top = 32.dp, bottom = 16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 2,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(32.dp))
            WaltLogo()
        }
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