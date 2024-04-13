package id.walt.androidSample.app.features.walkthrough

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import id.walt.androidSample.app.features.walkthrough.components.WalkthroughStep
import id.walt.androidSample.app.features.walkthrough.components.WaltPrimaryButton
import id.walt.androidSample.app.features.walkthrough.components.WaltSecondaryButton
import id.walt.androidSample.theme.WaltIdAndroidSampleTheme


@Composable
fun StepFiveScreen() {
    WalkthroughStep(title = "Step 5 - Verify Signed Text", description = "Verify the signed text using the generated key pair.") {
        Spacer(modifier = Modifier.weight(1f))
        WaltSecondaryButton(text = "Verify", onClick = { /*TODO*/ }, modifier = Modifier.fillMaxWidth())
        WaltPrimaryButton(text = "Complete Walkthrough", onClick = { /*TODO*/ }, modifier = Modifier.fillMaxWidth())
    }
}

@Preview
@Composable
private fun Preview() {
    WaltIdAndroidSampleTheme {
        StepFiveScreen()
    }
}