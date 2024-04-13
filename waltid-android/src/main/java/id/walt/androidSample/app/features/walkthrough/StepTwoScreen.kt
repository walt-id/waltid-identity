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
fun StepTwoScreen(
    viewModel: WalkthroughViewModel,
    navController: NavController,
    modifier: Modifier = Modifier,
) {

    val publicKey by viewModel.publicKey.collectAsStateWithLifecycle()

    WalkthroughStep(
        title = "Step 2 - Retrieve Public Key",
        description = "Retrieve the public key from the generated key pair.",
        modifier = modifier,
    ) {

        if (publicKey != null) {
            Text(
                text = publicKey.toString(),
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
            text = "Retrieve Public Key",
            onClick = viewModel::onRetrievePublicKeyClick,
            modifier = Modifier.fillMaxWidth()
        )
        WaltPrimaryButton(
            text = "Next Step",
            enabled = publicKey != null,
            onClick = viewModel::onNextStepClick,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Preview
@Composable
private fun Preview() {
    WaltIdAndroidSampleTheme {
        StepTwoScreen(WalkthroughViewModel.Fake(), rememberNavController())
    }
}