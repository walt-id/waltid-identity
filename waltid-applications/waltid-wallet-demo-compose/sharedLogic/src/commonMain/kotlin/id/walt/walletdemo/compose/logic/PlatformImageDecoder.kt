package id.walt.walletdemo.compose.logic

internal expect fun platformCanDecodeImage(bytes: ByteArray, maxPixelCount: Long): Boolean
