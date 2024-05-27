package id.walt.androidSample.app.features.walkthrough

import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import id.walt.androidSample.R
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
        title = stringResource(R.string.label_step_2_title),
        description = stringResource(R.string.description_step_2),
        modifier = modifier,
    ) {

        if (publicKey != null) {
            Text(
                text = publicKey.toString(),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            )
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }

        WaltSecondaryButton(
            text = stringResource(R.string.label_retrieve_public_key),
            onClick = viewModel::onRetrievePublicKeyClick,
            modifier = Modifier.fillMaxWidth()
        )
        WaltSecondaryButton(
            text = stringResource(R.string.label_go_back),
            onClick = viewModel::onBackClick,
            modifier = Modifier.fillMaxWidth()
        )
        WaltPrimaryButton(
            text = stringResource(R.string.label_next_step),
            enabled = publicKey != null,
            onClick = viewModel::onGoToStepThreeClick,
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