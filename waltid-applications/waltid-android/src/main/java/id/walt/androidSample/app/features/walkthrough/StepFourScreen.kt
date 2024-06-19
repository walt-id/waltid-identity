@file:OptIn(ExperimentalMaterial3Api::class)

package id.walt.androidSample.app.features.walkthrough

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
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
import id.walt.androidSample.app.features.walkthrough.model.SignOption
import id.walt.androidSample.app.features.walkthrough.model.WalkthroughEvent
import id.walt.androidSample.app.util.authenticateWithBiometric
import id.walt.androidSample.theme.WaltIdAndroidSampleTheme
import id.walt.androidSample.utils.ObserveAsEvents
import id.walt.androidSample.utils.collectImmediatelyAsStateWithLifecycle
import kotlinx.coroutines.launch


@OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class)
@Composable
fun StepFourScreen(
    viewModel: WalkthroughViewModel,
    navController: NavController,
    modifier: Modifier = Modifier,
) {

    val ctx = LocalContext.current
    val systemKeyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current

    val inputText by viewModel.plainText.collectImmediatelyAsStateWithLifecycle()
    val signOptions = viewModel.signOptions
    val selectedSignOption by viewModel.selectedSignOption.collectAsStateWithLifecycle()
    val signedText by viewModel.signedOutput.collectAsStateWithLifecycle()

    val biometricManager = remember { BiometricManager.from(ctx) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

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

            is WalkthroughEvent.Biometrics.BiometricError -> {
                scope.launch {
                    val snackbarResult = snackbarHostState.showSnackbar(
                        message = event.msg,
                        actionLabel = ctx.getString(R.string.label_enroll_now),
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
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        WalkthroughStep(
            title = stringResource(R.string.label_step_4_title),
            description = stringResource(R.string.description_step_4),
            modifier = Modifier.padding(innerPadding),
        ) {

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = inputText,
                onValueChange = viewModel::onPlainTextChanged,
                label = { Text(stringResource(R.string.description_plain_text_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        systemKeyboard?.hide()
                        focus.clearFocus()
                    }
                ),
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
                text = stringResource(R.string.label_sign_input),
                enabled = inputText.isNotBlank(),
                onClick = {
                    systemKeyboard?.hide()
                    val isBiometricAvailable =
                        biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK)
                    authenticateWithBiometric(
                        context = ctx as FragmentActivity,
                        onAuthenticated = viewModel::onSignTextClick,
                        onFailure = viewModel::onBiometricsAuthFailure,
                        isBiometricsAvailable = isBiometricAvailable,
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
            WaltSecondaryButton(
                text = stringResource(R.string.label_go_back),
                onClick = viewModel::onBackClick,
                modifier = Modifier.fillMaxWidth()
            )
            WaltPrimaryButton(
                text = stringResource(R.string.label_next_step),
                onClick = viewModel::onGoToStepFiveClick,
                enabled = signedText != null,
                modifier = Modifier.fillMaxWidth(),
            )
        }
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

@Preview
@Composable
private fun Preview() {
    WaltIdAndroidSampleTheme {
        StepFourScreen(WalkthroughViewModel.Fake(), rememberNavController())
    }
}