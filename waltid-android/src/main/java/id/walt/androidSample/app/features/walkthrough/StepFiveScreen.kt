package id.walt.androidSample.app.features.walkthrough

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import id.walt.androidSample.R
import id.walt.androidSample.app.features.walkthrough.components.WalkthroughStep
import id.walt.androidSample.app.features.walkthrough.components.WaltPrimaryButton
import id.walt.androidSample.app.features.walkthrough.components.WaltSecondaryButton
import id.walt.androidSample.app.features.walkthrough.model.VerificationResult
import id.walt.androidSample.theme.WaltIdAndroidSampleTheme


@Composable
fun StepFiveScreen(
    viewModel: WalkthroughViewModel,
    navController: NavController,
    modifier: Modifier = Modifier,
) {

    val ctx = LocalContext.current

    val verificationResult by viewModel.verificationResult.collectAsStateWithLifecycle()

    WalkthroughStep(
        title = stringResource(R.string.label_step_5_title),
        description = stringResource(R.string.description_step_5),
        modifier = modifier,
    ) {


        when(verificationResult) {
            VerificationResult.Success -> {
                Text(
                    text = stringResource(id = R.string.success),
                    color = Color.Green.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(vertical = 8.dp)
                        .fillMaxWidth()
                        .weight(1f),
                )
            }
            VerificationResult.Failed -> {
                Text(
                    text = stringResource(id = R.string.description_verification_failed),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(vertical = 8.dp)
                        .fillMaxWidth()
                        .weight(1f),
                )
            }
            null ->  Spacer(modifier = Modifier.weight(1f))
        }

        WaltSecondaryButton(
            text = stringResource(R.string.label_verify),
            onClick = viewModel::onVerifyClick,
            modifier = Modifier.fillMaxWidth()
        )
        WaltSecondaryButton(
            text = stringResource(R.string.label_go_back),
            onClick = viewModel::onBackClick,
            modifier = Modifier.fillMaxWidth()
        )
        WaltPrimaryButton(
            text = stringResource(R.string.label_reset),
            onClick = viewModel::onCompleteWalkthroughClick,
            enabled = verificationResult != null,
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