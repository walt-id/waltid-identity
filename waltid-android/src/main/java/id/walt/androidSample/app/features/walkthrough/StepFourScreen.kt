@file:OptIn(ExperimentalMaterial3Api::class)

package id.walt.androidSample.app.features.walkthrough

import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import id.walt.androidSample.R
import id.walt.androidSample.app.features.walkthrough.components.WalkthroughStep
import id.walt.androidSample.app.features.walkthrough.components.WaltPrimaryButton
import id.walt.androidSample.app.features.walkthrough.components.WaltSecondaryButton
import id.walt.androidSample.theme.WaltIdAndroidSampleTheme
import id.walt.androidSample.utils.ObserveAsEvents
import id.walt.androidSample.utils.collectImmediatelyAsStateWithLifecycle


@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun StepFourScreen(
    viewModel: WalkthroughViewModel,
    navController: NavController,
    modifier: Modifier = Modifier,
) {

    val ctx = LocalContext.current
    val systemKeyboard = LocalSoftwareKeyboardController.current

    val inputText by viewModel.plainText.collectImmediatelyAsStateWithLifecycle()
    val signOptions = viewModel.signOptions
    val selectedSignOption by viewModel.selectedSignOption.collectAsStateWithLifecycle()
    val signedText by viewModel.signedOutput.collectAsStateWithLifecycle()

    val biometricManager = remember { BiometricManager.from(ctx) }
    val isBiometricsAvailable =
        biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS

    ObserveAsEvents(flow = viewModel.events) { event ->
        when (event) {
            is WalkthroughEvent.NavigateEvent -> {}

            WalkthroughEvent.Biometrics.BiometricAuthenticationFailure -> Toast.makeText(
                ctx,
                ctx.getString(R.string.biometric_authentication_failure),
                Toast.LENGTH_SHORT
            ).show()

            WalkthroughEvent.Biometrics.BiometricsUnavailable -> Toast.makeText(
                ctx,
                ctx.getString(R.string.biometric_unavailable),
                Toast.LENGTH_SHORT
            ).show()

        }
    }

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
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
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
            enabled = inputText.isNotBlank(),
            onClick = {
                systemKeyboard?.hide()
                authenticateWithBiometric(
                    context = ctx as FragmentActivity,
                    onAuthenticated = viewModel::onSignTextClick,
                    onFailure = viewModel::onBiometricsAuthFailure
                )
            },
            modifier = Modifier.fillMaxWidth()
        )
        WaltPrimaryButton(
            text = "Next Step",
            onClick = viewModel::onGoToStepFiveClick,
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
    data object Raw : SignOption
    data object JWS : SignOption

    companion object {
        fun all() = listOf(Raw, JWS)
    }
}


private fun authenticateWithBiometric(
    context: FragmentActivity,
    onAuthenticated: () -> Unit,
    onFailure: () -> Unit,
) {
    val executor = context.mainExecutor
    val biometricPrompt = BiometricPrompt(
        context,
        executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onAuthenticated()
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                onFailure()
            }
        }
    )
    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK)
        .setTitle(context.getString(R.string.title_biometric_authentication))
        .setSubtitle(context.getString(R.string.subtitle_biometric_authentication))
        .setNegativeButtonText(context.getString(R.string.cancel))
        .build()

    biometricPrompt.authenticate(promptInfo)
}

@Preview
@Composable
private fun Preview() {
    WaltIdAndroidSampleTheme {
        StepFourScreen(WalkthroughViewModel.Fake(), rememberNavController())
    }
}