package id.walt.walletdemo.compose.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId

internal actual fun Modifier.exportTestTagsForPlatformAutomation(): Modifier =
    semantics {
        testTagsAsResourceId = true
    }
