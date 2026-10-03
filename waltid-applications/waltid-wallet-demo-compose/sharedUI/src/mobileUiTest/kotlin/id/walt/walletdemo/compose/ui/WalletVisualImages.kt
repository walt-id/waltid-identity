package id.walt.walletdemo.compose.ui

import coil3.EventListener
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.svg.SvgDecoder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** Observe actual local image decoding. Captures wait for success, never an arbitrary delay. */
@OptIn(DelicateCoilApi::class)
internal class WalletVisualImages {
    private val completed = MutableStateFlow(emptySet<String>())
    private val failures = MutableStateFlow(emptyList<String>())
    private var loader: ImageLoader? = null
    private val fixtures = mapOf(
        "portrait" to SyntheticCredentialImageFixtures.portraitBytes,
        "signature" to SyntheticCredentialImageFiles.read("synthetic-signature.png"),
    )

    fun install() {
        SingletonImageLoader.setUnsafe { context ->
            ImageLoader.Builder(context)
                .components { add(SvgDecoder.Factory()); add(walletImageDecoderFactory()) }
                .eventListenerFactory { object : EventListener() {
                    override fun onSuccess(request: ImageRequest, result: SuccessResult) {
                        val bytes = request.data as? ByteArray ?: return
                        fixtures.entries.firstOrNull { it.value.contentEquals(bytes) }?.key?.let { id ->
                            completed.update { it + id }
                        }
                    }

                    override fun onError(request: ImageRequest, result: ErrorResult) {
                        failures.update { it + result.throwable.message.orEmpty() }
                    }
                } }
                .build().also { loader = it }
        }
    }

    fun isReady(id: String): Boolean {
        check(failures.value.isEmpty()) { "Image fixture failed: ${failures.value}" }
        return id in completed.value
    }

    fun close() {
        loader?.shutdown()
        SingletonImageLoader.reset()
    }
}
