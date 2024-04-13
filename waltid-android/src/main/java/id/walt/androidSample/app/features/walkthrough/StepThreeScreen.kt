package id.walt.androidSample.app.features.walkthrough

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import id.walt.androidSample.app.features.walkthrough.components.WalkthroughStep
import id.walt.androidSample.app.features.walkthrough.components.WaltPrimaryButton
import id.walt.androidSample.app.features.walkthrough.components.WaltSecondaryButton
import id.walt.androidSample.theme.WaltIdAndroidSampleTheme


@Composable
fun StepThreeScreen() {

    val methodOptions = listOf(MethodOption.Key, MethodOption.JWK)
    var selectedMethodOption: MethodOption by remember {
        mutableStateOf(MethodOption.Key)
    }

    WalkthroughStep(title = "Step 3 - Generate DID", description = "Generate a DID using the generated key pair.") {

        Spacer(modifier = Modifier.weight(1f))

        MethodRadioGroup(
            selectedOption = selectedMethodOption,
            options = methodOptions,
            onOptionSelected = { selectedMethodOption = it },
        )

        Spacer(modifier = Modifier.height(16.dp))

        WaltSecondaryButton(text = "Generate DID", onClick = { /*TODO*/ }, modifier = Modifier.fillMaxWidth())
        WaltPrimaryButton(text = "Next Step", onClick = { /*TODO*/ }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun MethodRadioGroup(
    selectedOption: MethodOption,
    options: List<MethodOption>,
    onOptionSelected: (MethodOption) -> Unit,
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

sealed interface MethodOption {
    data object Key : MethodOption
    data object JWK : MethodOption

    companion object {
        fun all() = listOf(Key, JWK)
    }
}

@Preview
@Composable
private fun Preview() {
    WaltIdAndroidSampleTheme {
        StepThreeScreen()
    }
}