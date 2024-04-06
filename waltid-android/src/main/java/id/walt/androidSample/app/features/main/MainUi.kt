package id.walt.androidSample.app.features.main

import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavHostController
import id.walt.androidSample.R
import id.walt.androidSample.app.features.main.MainViewModel.Event.*
import id.walt.androidSample.app.navigation.NavigationItem
import id.walt.androidSample.theme.WaltIdAndroidSampleTheme
import id.walt.androidSample.ui.KeyTypeOptions
import id.walt.androidSample.utils.ObserveAsEvents
import id.walt.androidSample.utils.collectImmediatelyAsState
import id.walt.crypto.keys.KeyType

@Composable
fun MainUi(
    viewModel: MainViewModel,
    navHostController: NavHostController,
) {

    val ctx = LocalContext.current

    val biometricManager = remember { BiometricManager.from(ctx) }
    val isBiometricsAvailable = biometricManager.canAuthenticate(BIOMETRIC_STRONG or BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            SignatureInvalid -> Toast.makeText(ctx, ctx.getString(R.string.signature_verification_failed), Toast.LENGTH_SHORT).show()
            SignatureVerified -> Toast.makeText(ctx, ctx.getString(R.string.signature_verified), Toast.LENGTH_SHORT).show()
            BiometricsUnavailable -> Toast.makeText(ctx, ctx.getString(R.string.biometric_unavailable), Toast.LENGTH_SHORT).show()
            BiometricAuthenticationFailure -> Toast.makeText(
                ctx,
                ctx.getString(R.string.biometric_authentication_failure),
                Toast.LENGTH_SHORT
            ).show()

            is SignedWithKey -> Toast.makeText(
                ctx,
                ctx.getString(R.string.signed_with_key, event.key.name),
                Toast.LENGTH_SHORT
            ).show()

            CredentialSignFailure -> Toast.makeText(ctx, ctx.getString(R.string.credential_sign_failure), Toast.LENGTH_SHORT).show()
            GeneralSuccess -> Toast.makeText(ctx, ctx.getString(R.string.success), Toast.LENGTH_SHORT).show()
            is NavigateToResult -> {
                navHostController.navigate(NavigationItem.Result.route)
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!isBiometricsAvailable) viewModel.onBiometricsUnavailable()
    }

    MainUiContent(viewModel, isBiometricsAvailable)
}


@OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun MainUiContent(
    viewModel: MainViewModel,
    isBiometricsAvailable: Boolean,
) {

    val plainText by viewModel.plainText.collectImmediatelyAsState()
    val signature by viewModel.signature.collectAsState()

    val context = LocalContext.current
    val systemKeyboard = LocalSoftwareKeyboardController.current

    val keyTypeOptions = listOf(KeyType.RSA, KeyType.secp256r1)
    var selectedKeyType: KeyType by rememberSaveable { mutableStateOf(keyTypeOptions[0]) }


    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .padding(vertical = 10.dp)
            .animateContentSize()
            .verticalScroll(rememberScrollState())
    ) {

        KeyTypeOptions(
            options = keyTypeOptions,
            selectedOption = selectedKeyType,
            modifier = Modifier
        ) { newSelection ->
            selectedKeyType = newSelection
        }

        OutlinedTextField(
            value = plainText,
            onValueChange = { viewModel.onPlainTextChange(it) },
            label = { Text(stringResource(R.string.label_enter_plaintext)) },
            singleLine = true,
            trailingIcon = {
                if (plainText.isNotBlank()) {
                    Icon(
                        Icons.Default.Clear,
                        contentDescription = null,
                        modifier = Modifier.clickable {
                            viewModel.onClearInput()
                        }
                    )
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = {
                    systemKeyboard?.hide()
                    authenticateWithBiometric(
                        context = context as FragmentActivity,
                        onAuthenticated = { viewModel.onSignRaw(plainText, selectedKeyType) },
                        onFailure = { viewModel.onBiometricsAuthFailure() }
                    )
                },
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
        )

       Column(
           modifier = Modifier.padding(horizontal = 50.dp)
       ) {
           Button(
               modifier = Modifier.fillMaxWidth(),
               onClick = {
                   systemKeyboard?.hide()
                   authenticateWithBiometric(
                       context = context as FragmentActivity,
                       onAuthenticated = { viewModel.onSignJWS(plainText, selectedKeyType) },
                       onFailure = { viewModel.onBiometricsAuthFailure() }
                   )
               },
               enabled = plainText.isNotBlank() && isBiometricsAvailable,
           ) {
               Text(text = stringResource(R.string.label_sign_jws))
           }

           Button(
               modifier = Modifier.fillMaxWidth(),
               onClick = {
                   systemKeyboard?.hide()
                   authenticateWithBiometric(
                       context = context as FragmentActivity,
                       onAuthenticated = { viewModel.onSignRaw(plainText, selectedKeyType) },
                       onFailure = { viewModel.onBiometricsAuthFailure() }
                   )
               },
               enabled = plainText.isNotBlank() && isBiometricsAvailable,
           ) {
               Text(text = stringResource(R.string.label_sign))
           }

           Button(
               modifier = Modifier.fillMaxWidth(),
               onClick = {
                   systemKeyboard?.hide()
                   viewModel.onVerifyPlainText(signature!!, plainText.toByteArray())
               },
               enabled = signature != null,
           ) {
               Text(text = stringResource(R.string.label_verify_signature))
           }

           Button(
               modifier = Modifier.fillMaxWidth(),
               onClick = {
                   systemKeyboard?.hide()
                   viewModel.onRetrievePublicKey()
               },
               enabled = signature != null,
           ) {
               Text(text = stringResource(R.string.label_get_public_key))
           }

           Button(
               modifier = Modifier.fillMaxWidth(),
               onClick = {
                   systemKeyboard?.hide()
                   viewModel.onGenerateDid()
               },
               enabled = signature != null,
           ) {
               Text(text = stringResource(R.string.label_generate_did))
           }

           Button(
               modifier = Modifier.fillMaxWidth(),
               onClick = {
                   systemKeyboard?.hide()
                   authenticateWithBiometric(
                       context = context as FragmentActivity,
                       onAuthenticated = { viewModel.onSignCredential() },
                       onFailure = { viewModel.onBiometricsAuthFailure() }
                   )
               },
               enabled = signature != null,
           ) {
               Text(text = stringResource(R.string.label_sign_credential))
           }

           Button(
               modifier = Modifier.fillMaxWidth(),
               onClick = {
                   systemKeyboard?.hide()
                   authenticateWithBiometric(
                       context = context as FragmentActivity,
                       onAuthenticated = { viewModel.onCreateAndSignSDJWT() },
                       onFailure = { viewModel.onBiometricsAuthFailure() }
                   )
               }
           ) {
               Text(text = stringResource(R.string.label_create_and_sign_sdjwt))
           }
       }
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
        .setAllowedAuthenticators(BIOMETRIC_STRONG or BIOMETRIC_WEAK)
        .setTitle(context.getString(R.string.title_biometric_authentication))
        .setSubtitle(context.getString(R.string.subtitle_biometric_authentication))
        .setNegativeButtonText(context.getString(R.string.cancel))
        .build()

    biometricPrompt.authenticate(promptInfo)
}

@Preview
@Composable
private fun Preview_MainUiContent() {
    WaltIdAndroidSampleTheme {
        Surface(
            modifier = Modifier.fillMaxSize()
        ) {
            MainUiContent(
                MainViewModel.Fake(),
                isBiometricsAvailable = true,
            )
        }
    }
}
