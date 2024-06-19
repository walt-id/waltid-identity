package id.walt.androidSample.app.features.walkthrough.components

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import id.walt.androidSample.R
import id.walt.androidSample.theme.WaltIdAndroidSampleTheme

@Composable
fun WaltLogo() {
    Surface(
        shape = CircleShape,
        modifier = Modifier.size(80.dp)
    ) {
        Icon(
            painterResource(id = R.drawable.logo_waltid),
            contentDescription = "Logo",
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

@Preview
@Composable
private fun Preview() {
    WaltIdAndroidSampleTheme {
        WaltLogo()
    }
}