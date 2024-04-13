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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import id.walt.androidSample.app.features.walkthrough.components.WalkthroughStep
import id.walt.androidSample.app.features.walkthrough.components.WaltPrimaryButton
import id.walt.androidSample.app.features.walkthrough.components.WaltSecondaryButton
import id.walt.androidSample.theme.WaltIdAndroidSampleTheme
import id.walt.androidSample.utils.collectImmediatelyAsStateWithLifecycle


@Composable
fun StepFourScreen(
    viewModel: WalkthroughViewModel,
    navController: NavController,
    modifier: Modifier = Modifier,
) {

    val inputText by viewModel.plainText.collectImmediatelyAsStateWithLifecycle()
    val signOptions = viewModel.signOptions
    val selectedSignOption by viewModel.selectedSignOption.collectAsStateWithLifecycle()
    val signedText by viewModel.signedOutput.collectAsStateWithLifecycle()

    WalkthroughStep(
        title = "Step 4 - Sign Input",
        description = "Sign the input using the generated key pair.",
        modifier = modifier,
    ) {

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = inputText,
            onValueChange = viewModel::onPlainTextChanged,
            label = { Text("Input text to sign here") },
        )

        if (signedText != null) {
            Text(
                text = signedText.toString(),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .weight(1f),
            )
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }

        SignRadioGroup(
            selectedOption = selectedSignOption,
            options = signOptions,
            onOptionSelected = viewModel::onSignOptionSelected,
        )

        Spacer(modifier = Modifier.height(16.dp))

        WaltSecondaryButton(
            text = "Sign Input",
            onClick = viewModel::onSignTextClick,
            modifier = Modifier.fillMaxWidth()
        )
        WaltPrimaryButton(
            text = "Next Step",
            onClick = viewModel::onNextStepClick,
            enabled = signedText != null,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SignRadioGroup(
    selectedOption: SignOption,
    options: List<SignOption>,
    onOptionSelected: (SignOption) -> Unit,
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

sealed interface SignOption {
    data object Plain : SignOption
    data object PlainJWS : SignOption
    data object JWSCredential : SignOption
    data object SelectiveDisclosureJWT : SignOption

    companion object {
        fun all() = listOf(Plain, PlainJWS, JWSCredential, SelectiveDisclosureJWT)
    }
}

@Preview
@Composable
private fun Preview() {
    WaltIdAndroidSampleTheme {
        StepFourScreen(WalkthroughViewModel.Fake(), rememberNavController())
    }
}