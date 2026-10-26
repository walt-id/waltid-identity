package id.walt.walletdemo.compose.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.ui.LocalWalletDemoBranding
import id.walt.walletdemo.compose.ui.WalletUiTestTags
import id.walt.walletdemo.compose.ui.components.SettingsNotice
import id.walt.walletdemo.compose.ui.components.WalletAction
import id.walt.walletdemo.compose.ui.components.WalletActions
import id.walt.walletdemo.compose.ui.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun AccountAuthScreen(
    isBusy: Boolean,
    error: String?,
    onLogin: (email: String, password: String) -> Unit,
    onRegister: (email: String, password: String) -> Unit,
    allowRegister: Boolean = true,
) {
    val branding = LocalWalletDemoBranding.current
    val emailLabel = stringResource(Res.string.account_email)
    val passwordLabel = stringResource(Res.string.account_password)
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val canSubmit = email.contains('@') && password.length >= 4 && !isBusy

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth()
                    .verticalScroll(rememberScrollState()).padding(20.dp)
                    .testTag(WalletUiTestTags.AccountAuthScreen),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    branding.appTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    stringResource(if (allowRegister) Res.string.account_welcome else Res.string.account_sign_in_welcome),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(emailLabel) },
                    singleLine = true,
                    enabled = !isBusy,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = emailLabel }
                        .testTag(WalletUiTestTags.AccountEmailInput),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(passwordLabel) },
                    singleLine = true,
                    enabled = !isBusy,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { if (canSubmit) onLogin(email.trim(), password) },
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = passwordLabel }
                        .testTag(WalletUiTestTags.AccountPasswordInput),
                )
                if (error != null) {
                    SettingsNotice(error, error = true, modifier = Modifier.testTag(WalletUiTestTags.AccountAuthError))
                }
                WalletActions(
                    primary = WalletAction(
                        stringResource(if (isBusy) Res.string.account_working else Res.string.account_sign_in),
                        { onLogin(email.trim(), password) }, canSubmit,
                        WalletUiTestTags.AccountLoginButton,
                    ),
                    secondary = if (allowRegister) WalletAction(stringResource(Res.string.account_create),
                        { onRegister(email.trim(), password) }, canSubmit,
                        WalletUiTestTags.AccountRegisterButton) else null,
                )
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Account · expired session", widthDp = 393, heightDp = 600)
@Composable
private fun AccountExpiredPreview() = id.walt.walletdemo.compose.ui.WalletDemoTheme {
    AccountAuthScreen(false, "Your session has expired. Sign in again to continue.", { _, _ -> }, { _, _ -> })
}
