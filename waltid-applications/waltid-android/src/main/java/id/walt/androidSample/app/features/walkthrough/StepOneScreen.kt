@file:OptIn(ExperimentalMaterial3Api::class)

package id.walt.androidSample.app.features.walkthrough

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import id.walt.androidSample.R
import id.walt.androidSample.app.features.walkthrough.components.WalkthroughStep
import id.walt.androidSample.app.features.walkthrough.components.WaltPrimaryButton
import id.walt.androidSample.app.features.walkthrough.components.WaltSecondaryButton
import id.walt.androidSample.app.features.walkthrough.model.KeyAlgorithmOption
import id.walt.androidSample.app.features.walkthrough.model.WalkthroughEvent
import id.walt.androidSample.theme.WaltIdAndroidSampleTheme
import id.walt.androidSample.utils.ObserveAsEvents
import kotlinx.coroutines.launch

@Composable
fun StepOneScreen(
    viewModel: WalkthroughViewModel,
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    val algorithmOptions = viewModel.keyAlgorithmOptions
    val selectedAlgorithmOption by viewModel.selectedKeyAlgorithm.collectAsStateWithLifecycle()
    val generatedKey by viewModel.generatedKey.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            WalkthroughEvent.Biometrics.SecureLockScreenNotEnabled -> {
                scope.launch {
                    val snackbarResult = snackbarHostState.showSnackbar(
                        message = ctx.getString(R.string.secure_lock_screen_not_enabled),
                        actionLabel = ctx.getString(R.string.label_enable_now),
                        withDismissAction = false,
                        duration = SnackbarDuration.Short
                    )

                    when (snackbarResult) {
                        SnackbarResult.ActionPerformed -> {
                            Intent(Settings.ACTION_SECURITY_SETTINGS).also {
                                ctx.startActivity(it)
                            }
                        }
                        SnackbarResult.Dismissed -> {}
                    }
                }
            }

            else -> {}
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { innerPadding ->
        WalkthroughStep(
            title = stringResource(R.string.label_step_1_title),
            description = stringResource(R.string.description_step_1),
            modifier = Modifier.padding(innerPadding),
        ) {
            Spacer(modifier = Modifier.weight(1f))

            AlgorithmRadioGroup(
                selectedOption = selectedAlgorithmOption,
                options = algorithmOptions,
                onOptionSelected = viewModel::onKeyAlgorithmSelected,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            WaltSecondaryButton(
                text = stringResource(R.string.label_generate_key),
                onClick = viewModel::onGenerateKeyClick,
                modifier = Modifier.fillMaxWidth()
            )
            WaltPrimaryButton(
                text = stringResource(R.string.label_next_step),
                onClick = viewModel::onGoToStepTwoClick,
                enabled = generatedKey != null,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun AlgorithmRadioGroup(
    selectedOption: KeyAlgorithmOption,
    options: List<KeyAlgorithmOption>,
    onOptionSelected: (KeyAlgorithmOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.selectableGroup()) {
        options.forEach { option ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .selectable(
                        selected = (option == selectedOption),
                        onClick = { onOptionSelected(option) },
                        role = Role.RadioButton
                    )
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = (option == selectedOption),
                    onClick = null
                )
                Text(
                    text = option.toString(),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 16.dp)
                )
            }
        }
    }
}

@Preview
@Composable
private fun StepOneScreenPreview() {
    WaltIdAndroidSampleTheme {
        StepOneScreen(WalkthroughViewModel.Fake(), rememberNavController())
    }
}