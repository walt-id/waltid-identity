package id.walt.androidSample.app.features.walkthrough

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import id.walt.androidSample.app.features.walkthrough.components.WalkthroughStep
import id.walt.androidSample.app.features.walkthrough.components.WaltPrimaryButton
import id.walt.androidSample.app.features.walkthrough.components.WaltSecondaryButton
import id.walt.androidSample.theme.WaltIdAndroidSampleTheme


@Composable
fun StepFiveScreen(
    viewModel: WalkthroughViewModel,
    navController: NavController,
    modifier: Modifier = Modifier,
) {

    val verifiedText by viewModel.verifiedText.collectAsStateWithLifecycle()

    WalkthroughStep(
        title = "Step 5 - Verify Signed Text",
        description = "Verify the signed text using the generated key pair.",
        modifier = modifier,
    ) {

        if (verifiedText != null) {
            Text(
                text = verifiedText.toString(),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .weight(1f),
            )
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }

        WaltSecondaryButton(
            text = "Verify",
            onClick = viewModel::onVerifyClick,
            modifier = Modifier.fillMaxWidth()
        )
        WaltPrimaryButton(
            text = "Complete Walkthrough",
            onClick = { /*TODO*/ },
            enabled = verifiedText != null,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Preview
@Composable
private fun Preview() {
    WaltIdAndroidSampleTheme {
        StepFiveScreen(WalkthroughViewModel.Fake(), rememberNavController())
    }
}