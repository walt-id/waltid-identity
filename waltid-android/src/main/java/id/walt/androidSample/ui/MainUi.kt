package id.walt.androidSample.ui

import android.annotation.SuppressLint
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
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.walt.androidSample.ui.theme.WaltIdAndroidSampleTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun MainUi(viewModel: MainViewModel) {

    val plainText by viewModel.plainText.collectImmediatelyAsState()
    val encryptedText by viewModel.encryptedText.collectAsState()
    val didText by viewModel.didText.collectAsState()
    val verifiedCredential by viewModel.verifiedCredentialJSON.collectAsState()
    val signedVC by viewModel.signedVC.collectAsState()

    val systemKeyboard = LocalSoftwareKeyboardController.current

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
            label = { Text("Enter your plain text to encrypt") },
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
                    viewModel.onEncrypt(plainText)
                }
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
        )

        Button(
            onClick = {
                systemKeyboard?.hide()
                viewModel.onEncrypt(plainText)
            },
            enabled = plainText.isNotBlank(),
        ) {
            Text(text = "Encrypt PlainText")
        }

        if (encryptedText.isNotBlank()) {
            Text(
                text = encryptedText,
                color = Color.Blue,
                modifier = Modifier.padding(20.dp),
            )
        }

        Text(
            text = didText,
            modifier = Modifier.padding(20.dp)
        )

        Text(
            text = """
                |Signed Verified Certificate:
                |$signedVC
            """.trimMargin(),
            modifier = Modifier
                .padding(top = 40.dp, bottom = 20.dp)
                .padding(horizontal = 20.dp)
        )

        Text(
            text = verifiedCredential,
            modifier = Modifier.padding(20.dp),
            fontSize = 10.sp
        )
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