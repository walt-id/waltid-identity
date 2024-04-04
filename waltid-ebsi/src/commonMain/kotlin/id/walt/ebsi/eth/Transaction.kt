package id.walt.ebsi.eth
import kotlinx.serialization.Serializable

@Serializable
data class UnsignedTransaction(
  val from: String,
  val to: String,
  val data: String,
  val nonce: String,
  val chainId: String,
  val gasLimit: String,
  val gasPrice: String,
  val value: String
)

@Serializable
data class SignedTransaction(val r: String, val s: String, val v: String, val signedRawTransaction: String)