package id.walt.androidSample.app.features.walkthrough

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import id.walt.androidSample.app.features.walkthrough.components.WalkthroughStep
import id.walt.androidSample.app.features.walkthrough.components.WaltPrimaryButton
import id.walt.androidSample.app.features.walkthrough.components.WaltSecondaryButton
import id.walt.androidSample.theme.WaltIdAndroidSampleTheme

@Composable
fun StepTwoScreen(
    viewModel: WalkthroughViewModel,
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    WalkthroughStep(title = "Step 2 - Retrieve Public Key", description = "Retrieve the public key from the generated key pair.") {
        Spacer(modifier = Modifier.weight(1f))
        WaltSecondaryButton(text = "Retrieve Public Key", onClick = { /*TODO*/ }, modifier = Modifier.fillMaxWidth())
        WaltPrimaryButton(text = "Next Step", onClick = { /*TODO*/ }, modifier = Modifier.fillMaxWidth())
    }
}

@Preview
@Composable
private fun Preview() {
    WaltIdAndroidSampleTheme {
        StepTwoScreen(WalkthroughViewModel.Fake(), rememberNavController())
    }
}