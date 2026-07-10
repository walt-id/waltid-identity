package id.walt.walletdemo.compose.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import id.walt.walletdemo.compose.logic.DisplayValue

@Composable
internal fun CredentialPortrait(
    portrait: DisplayValue.Image?,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp)),
        color = MaterialTheme.colorScheme.surface,
    ) {
        if (portrait == null) {
            Box(contentAlignment = Alignment.Center) {
                Text("ID", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            AsyncImage(
                model = portrait.bytes,
                contentDescription = "Credential portrait",
                contentScale = ContentScale.Crop,
            )
        }
    }
}
