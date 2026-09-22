package id.walt.statuslist.codec

import id.walt.statuslist.StatusListFormat
import id.walt.statuslist.model.IndexedStatus

interface StatusListCodec {
    val format: StatusListFormat

    fun encode(statuses: List<IndexedStatus>, statusSize: Int = 1, listSizeBytes: Int = defaultListSizeBytes(statusSize)): String
    fun encodeBytes(statuses: List<IndexedStatus>, statusSize: Int = 1, listSizeBytes: Int = defaultListSizeBytes(statusSize)): ByteArray
    fun encode(byteArray: ByteArray): String

    fun decode(encoded: String): ByteArray
    fun readStatus(decoded: ByteArray, index: Int, bits: Int = 1): UInt

    fun defaultListSizeBytes(statusSize: Int): Int
}
