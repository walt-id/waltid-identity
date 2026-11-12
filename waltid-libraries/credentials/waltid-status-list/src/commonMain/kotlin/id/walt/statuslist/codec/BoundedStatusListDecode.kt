package id.walt.statuslist.codec

import id.walt.statuslist.compression.GzipCompressor
import id.walt.statuslist.compression.ZlibCompressor
import id.walt.statuslist.encoding.StatusListEncoding
import id.walt.statuslist.encoding.StatusListStringEncoding

/** Bounded decoding for untrusted lists; the one-argument codec API remains compatible. */
fun StatusListCodec.decodeBounded(encoded: String, maxOutputBytes: Int): ByteArray = when (this) {
    TokenStatusListCodec -> ZlibCompressor.decompress(
        StatusListEncoding.decode(encoded, StatusListStringEncoding.Base64Url), maxOutputBytes,
    )
    BitstringStatusListCodec -> GzipCompressor.decompress(
        StatusListEncoding.decode(encoded, StatusListStringEncoding.MultibaseBase64Url), maxOutputBytes,
    )
    StatusList2021Codec, RevocationList2020Codec -> GzipCompressor.decompress(
        StatusListEncoding.decode(encoded, StatusListStringEncoding.Base64Url), maxOutputBytes,
    )
    else -> throw IllegalArgumentException("Unsupported status-list codec")
}
