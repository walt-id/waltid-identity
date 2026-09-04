package id.walt.walletdemo.compose.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.browser.document
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLFormElement

@Composable
internal actual fun PlatformFormPostEffect(
    html: String,
    onCompleted: () -> Unit,
    onFailed: (String) -> Unit,
) {
    LaunchedEffect(html) {
        runCatching {
            val holder = document.createElement("div") as HTMLElement
            holder.style.display = "none"
            holder.innerHTML = html
            document.body?.appendChild(holder)
            val form = holder.querySelector("form") as? HTMLFormElement
                ?: error("form_post HTML did not contain a form")
            form.submit()
        }.onSuccess { onCompleted() }
            .onFailure { onFailed(it.message ?: "form_post continuation failed") }
    }
}
