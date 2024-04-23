package id.walt.ebsi.rpc
import id.walt.ebsi.eth.UnsignedTransaction
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
sealed class JsonRpcParams

@Serializable
data class InsertDidDocumentParams(
  val from: String,
  val did: String,
  val baseDocument: String,
  val vMethodId: String,
  val publicKey: String,
  val isSecp256k1: Boolean,
  val notBefore: Long,
  val notAfter: Long
) : JsonRpcParams()

@Serializable
data class TimestampHashesParams(
  val from: String,
  val hashAlgorithmIds: List<Int>,
  val hashValues: List<String>,
  val timestampData: List<String?>
) : JsonRpcParams()

@Serializable
data class SignedTransactionParams(
  val protocol: String,
  val unsignedTransaction: UnsignedTransaction,
  val r: String,
  val s: String,
  val v: String,
  val signedRawTransaction: String
) : JsonRpcParams()

data class AddVerificationMethodParams(
  val from: String,
  val did: String,
  val vMethodId: String,
  val isSecp256k1: Boolean,
  val publicKey: String
) : JsonRpcParams()

data class AddVerificationRelationshipParams(
  val from: String,
  val did: String,
  val name: String,
  val vMethodId: String,
  val notBefore: Long,
  val notAfter: Long
) : JsonRpcParams()
