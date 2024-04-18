package id.walt.ebsi.eth

import kotlin.math.pow

object RlpEncoder {
  fun encode(input: ByteArray): ByteArray? {
    return if (input.size == 1) input
    else if (input.size in 2..55 || input.isEmpty()) encodeMedium(input)
    else if (input.size < 256.toDouble().pow(8.toDouble())) encodeLong(input)
    else throw Exception("Long input provided!")
  }

  fun encode(inputList: List<ByteArray>): ByteArray? {
    val payloadSize = payloadSize(inputList)
    return if (payloadSize in 0..55) encodeSmallList(inputList, payloadSize)
    else encodeLargeList(inputList, payloadSize)
  }

  private fun encodeLargeList(inputList: List<ByteArray>, payloadSize: Int): ByteArray {
    val prefix = 0xf7 + payloadSize.toString().length - 1
    val concatInput: ByteArray = concatByteArray(inputList)
    return ByteArray(payloadSize + 2) { i ->
      when (i) {
        0 -> prefix.toByte()
        1 -> concatInput.size.toByte()
        else -> concatInput[i - 2]
      }
    }
  }

  private fun encodeSmallList(inputList: List<ByteArray>, payloadSize: Int): ByteArray {
    val prefix = 0xc0 + payloadSize
    val concatInput: ByteArray = concatByteArray(inputList)
    return ByteArray(payloadSize + 1) { i ->
      when (i) {
        0 -> prefix.toByte()
        else -> concatInput[i - 1]
      }
    }
  }

  private fun concatByteArray(inputList: List<ByteArray>): ByteArray {
    val concat = arrayListOf<Byte>()
    concat.addAll(inputList.map { byteArray -> byteArray.toList() }.flatten())
    return concat.toByteArray()
  }

  private fun payloadSize(input: List<ByteArray>): Int {
    return input.map { it.size }.sum()
  }

  private fun encodeLong(input: ByteArray): ByteArray {
    val prefix = 0xb7 + input.size.toString().length - 1
    return ByteArray(
      input.size + 2
    ) { i ->
      when (i) {
        0 -> prefix.toByte()
        1 -> input.size.toByte()
        else -> input[i - 2]
      }
    }
  }

  private fun encodeMedium(input: ByteArray): ByteArray {
    val prefix = 0x80 + input.size
    return ByteArray(
      input.size + 1
    ) { i ->
      if (i == 0) prefix.toByte()
      else input[i - 1]
    }
  }
}
