package id.walt.walletdemo.compose.ui

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

internal expect object SyntheticCredentialImageFiles {
    fun read(name: String): ByteArray
}

@OptIn(ExperimentalEncodingApi::class)
internal object SyntheticCredentialImageFixtures {
    val portraitBytes: ByteArray by lazy { read("synthetic-portrait.jpg") }
    val portraitDataUrl: String by lazy { dataUrl("image/jpeg", portraitBytes) }
    val signatureDataUrl: String by lazy { dataUrl("image/png", read("synthetic-signature.png")) }
    val verificationDocumentDataUrl: String by lazy {
        dataUrl("image/jpeg", read("synthetic-verification-document.jpg"))
    }
    val portraitByteArrayJson: String by lazy {
        portraitBytes.joinToString(prefix = "[", postfix = "]") { byte ->
            (byte.toInt() and 0xFF).toString()
        }
    }

    private fun read(name: String): ByteArray = SyntheticCredentialImageFiles.read(name)

    private fun dataUrl(mimeType: String, bytes: ByteArray): String =
        "data:$mimeType;base64,${Base64.Default.encode(bytes)}"
}
