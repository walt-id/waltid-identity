@file:OptIn(ExperimentalMaterial3Api::class)

package id.walt.androidSample.app.features.walkthrough

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import id.walt.androidSample.theme.WaltIdAndroidSampleTheme

@Composable
fun StepOneScreen(
    viewModel: WalkthroughViewModel,
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    val algorithmOptions = viewModel.keyAlgorithmOptions
    val selectedAlgorithmOption by viewModel.selectedKeyAlgorithm.collectAsStateWithLifecycle()
    val generatedKey by viewModel.generatedKey.collectAsStateWithLifecycle()

    WalkthroughStep(
        title = stringResource(R.string.label_step_1_title),
        description = stringResource(R.string.description_step_1),
        modifier = modifier,
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