package id.walt.androidSample.app.features.result

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import id.walt.androidSample.app.features.main.MainViewModel
import id.walt.androidSample.models.CopiedText
import id.walt.androidSample.theme.WaltIdAndroidSampleTheme
import id.walt.androidSample.ui.BasicText
import id.walt.androidSample.utils.collectImmediatelyAsState

@Composable
fun ResultUi(
    viewModel: MainViewModel,
    navHostController: NavHostController,
) {

    val textToShow = viewModel.displayText.collectImmediatelyAsState()

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .padding(all = 15.dp)
            .animateContentSize()
            .verticalScroll(rememberScrollState())
    ) {
        val text = textToShow.value ?: "No text to display"
        BasicText(
            text = text,
            textToCopy = CopiedText("WaltId copied text", text)
        )
    }
}

@Preview
@Composable
private fun ResultUi_Preview() {
    WaltIdAndroidSampleTheme {
        Surface(
            modifier = Modifier.fillMaxSize()
        ) {
            ResultUi(
                viewModel = MainViewModel.Fake(),
                navHostController = rememberNavController()
            )
        }
    }
}