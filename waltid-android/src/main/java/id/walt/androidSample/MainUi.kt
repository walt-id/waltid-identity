package id.walt.androidSample

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Base64
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import id.walt.androidSample.MainViewModel.Event.BiometricAuthenticationFailure
import id.walt.androidSample.MainViewModel.Event.BiometricsUnavailable
import id.walt.androidSample.MainViewModel.Event.SignatureInvalid
import id.walt.androidSample.MainViewModel.Event.SignatureVerified
import id.walt.androidSample.theme.WaltIdAndroidSampleTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun MainUi(viewModel: MainViewModel) {

    val plainText by viewModel.plainText.collectImmediatelyAsState()
    val signature by viewModel.signature.collectImmediatelyAsState()
    val publicKey by viewModel.publicKey.collectImmediatelyAsState()

    val context = LocalContext.current
    val systemKeyboard = LocalSoftwareKeyboardController.current

    val biometricManager = remember { BiometricManager.from(context) }
    val isBiometricsAvailable = biometricManager.canAuthenticate(BIOMETRIC_STRONG or BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            SignatureInvalid -> Toast.makeText(context, context.getString(R.string.signature_verification_failed), Toast.LENGTH_SHORT).show()
            SignatureVerified -> Toast.makeText(context, context.getString(R.string.signature_verified), Toast.LENGTH_SHORT).show()
            BiometricsUnavailable -> Toast.makeText(context, context.getString(R.string.biometric_unavailable), Toast.LENGTH_SHORT).show()
            BiometricAuthenticationFailure -> Toast.makeText(context, context.getString(R.string.biometric_authentication_failure), Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        if (!isBiometricsAvailable) viewModel.onBiometricsUnavailable()
    }

    val executor = remember { ContextCompat.getMainExecutor(context) }
    val biometricPrompt = BiometricPrompt(
        context as FragmentActivity,
        executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                viewModel.onSignRaw(plainText)
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                viewModel.onBiometricsAuthFailure()
            }
        }
    )
    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setAllowedAuthenticators(BIOMETRIC_STRONG or BIOMETRIC_WEAK)
        .setTitle(stringResource(R.string.title_biometric_authentication))
        .setSubtitle(stringResource(R.string.subtitle_biometric_authentication))
        .setNegativeButtonText(stringResource(R.string.cancel))
        .build()

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .padding(vertical = 10.dp)
            .animateContentSize()
            .verticalScroll(rememberScrollState())
    ) {

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
                    biometricPrompt.authenticate(promptInfo)
                },
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
        )

        Button(
            onClick = {
                systemKeyboard?.hide()
                biometricPrompt.authenticate(promptInfo)
            },
            enabled = plainText.isNotBlank() && isBiometricsAvailable,
        ) {
            Text(text = stringResource(R.string.label_sign))
        }

        Button(
            onClick = {
                systemKeyboard?.hide()
                viewModel.onVerifyPlainText(signature!!, plainText.toByteArray())
            },
            enabled = signature != null,
        ) {
            Text(text = stringResource(R.string.label_verify_signature))
        }

        Button(
            onClick = {
                systemKeyboard?.hide()
                viewModel.onRetrievePublicKey()
            },
            enabled = signature != null,
        ) {
            Text(text = stringResource(R.string.label_get_public_key))
        }

        if (publicKey != null) {
            Text(
                text = "PublicKey: $publicKey",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 14.sp,
                modifier = Modifier
                    .padding(20.dp)
                    .horizontalScroll(rememberScrollState()),
            )
        }

        if (signature != null) {
            Text(
                text = stringResource(R.string.signature, Base64.encodeToString(signature, Base64.DEFAULT)),
                color = MaterialTheme.colorScheme.primary,
                fontSize = 14.sp,
                modifier = Modifier
                    .padding(20.dp)
                    .horizontalScroll(rememberScrollState())
                    .clickable {
                        (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.apply {
                            val clip = ClipData.newPlainText("Cryptographic Signature", Base64.encodeToString(signature, Base64.DEFAULT))
                            setPrimaryClip(clip)
                            Toast
                                .makeText(context, context.getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT)
                                .show()
                        }
                    },
            )
        }
    }
}

@Preview
@Composable
private fun Preview_MainUi() {
    WaltIdAndroidSampleTheme {
        Surface(
            modifier = Modifier.fillMaxSize()
        ) {
            MainUi(MainViewModel.Fake())
        }
    }
}

@SuppressLint("StateFlowValueCalledInComposition")
@Composable
private fun <T> StateFlow<T>.collectImmediatelyAsState(): State<T> = collectAsState(value, Dispatchers.Main.immediate)

@Composable
private fun <T> ObserveAsEvents(flow: Flow<T>, onEvent: (T) -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(flow, lifecycleOwner.lifecycle) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            withContext(Dispatchers.Main.immediate) {
                flow.collect(onEvent)
            }
        }
    }
}