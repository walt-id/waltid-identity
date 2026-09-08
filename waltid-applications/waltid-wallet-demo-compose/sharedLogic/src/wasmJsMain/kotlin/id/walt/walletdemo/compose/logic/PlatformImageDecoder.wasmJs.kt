package id.walt.walletdemo.compose.logic

// Browser image decoding is asynchronous. The web demo keeps the existing signature-based
// classification; mobile targets validate candidates with the decoder used by their UI.
internal actual fun platformCanDecodeImage(bytes: ByteArray, maxPixelCount: Long): Boolean = true
