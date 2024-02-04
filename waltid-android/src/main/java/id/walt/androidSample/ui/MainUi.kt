package id.walt.androidSample.ui

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Base64
import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
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
    val signature by viewModel.signature.collectAsState()

    val context = LocalContext.current
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
            label = { Text("Enter plain text to sign") },
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
                    viewModel.onSignRaw(plainText)
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
                viewModel.onSignRaw(plainText)
            },
            enabled = plainText.isNotBlank(),
        ) {
            Text(text = "Sign Plain Text")
        }

        Button(
            onClick = {
                systemKeyboard?.hide()
                viewModel.onVerifyPlainText(signature!!, null)
            },
            enabled = signature != null,
        ) {
            Text(text = "Verify Signature")
        }

        if (signature != null) {
            Text(
                text = "Signature: ${Base64.encodeToString(signature, Base64.DEFAULT)}}",
                color = Color.Blue,
                fontSize = 14.sp,
                modifier = Modifier
                    .padding(20.dp)
                    .horizontalScroll(rememberScrollState())
                    .clickable {
                        (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.apply {
                            val clip = ClipData.newPlainText("Cryptographic Signature", Base64.encodeToString(signature, Base64.DEFAULT))
                            setPrimaryClip(clip)
                            Toast.makeText(context, "Copied signature to clipboard!", Toast.LENGTH_SHORT).show()
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