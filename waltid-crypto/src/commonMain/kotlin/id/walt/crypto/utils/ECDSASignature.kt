package id.walt.crypto.utils

data class ECDSASignature(
  val r: ByteArray,
  val s: ByteArray
)        {
   companion object {
     fun fromECDSAConcat(rsConcat: ByteArray): ECDSASignature {
      return ECDSASignature(
        rsConcat.copyOfRange(0, rsConcat.size/2),
        rsConcat.copyOfRange(rsConcat.size/2, rsConcat.size)
      )
     }
   }
}
