package id.walt.walletdemo.compose.ui

import coil3.decode.Decoder

internal actual fun walletImageDecoderFactory(): Decoder.Factory = RasterGateDecoderFactory()
