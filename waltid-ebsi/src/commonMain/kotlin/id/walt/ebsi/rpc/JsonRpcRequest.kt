package id.walt.ebsi.rpc

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.ebsi.eth.SignedTransaction
import id.walt.ebsi.eth.UnsignedTransaction
import id.walt.ebsi.eth.Utils
import io.ktor.utils.io.core.*
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.plus
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

enum class EbsiRpcMethod {
  insertDidDocument,
  updateBaseDocument,
  addController,
  revokeController,
  addVerificationMethod,
  addVerificationRelationship,
  revokeVerificationMethod,
  expireVerificationMethod,
  rollVerificationMethod,
  sendSignedTransaction
}

@Serializable
data class JsonRpcRequest(@EncodeDefault val jsonrpc: String = "2.0", val method: EbsiRpcMethod, val params: List<JsonRpcParams>, val id: Int)

@OptIn(ExperimentalStdlibApi::class)
object EbsiRpcRequests {

  suspend fun generateInsertDidDocumentRequest(
    requestId: Int,
    did: String, from: String, secp256k1Key: Key,
    baseDocument: JsonElement,
    notBefore: Instant = Clock.System.now(), notAfter: Instant = notBefore.plus(365*24, DateTimeUnit.HOUR)
  ) = JsonRpcRequest(
    method = EbsiRpcMethod.insertDidDocument,
    params = listOf(InsertDidDocumentParams(
      from, did,
      baseDocument.toString(), secp256k1Key.getThumbprint(),
      Utils.getPublicKeyXYRepresentation(secp256k1Key).toHexString().let { "0x04$it" },
      (secp256k1Key.keyType == KeyType.secp256k1).also {
        if(!it) throw IllegalArgumentException("Key for inserting Did document must have type Secp256k1")
      }, notBefore.epochSeconds, notAfter.epochSeconds
    )),
    id = requestId)

  suspend fun generateSendSignedTransactionRequest(
    requestId: Int,
    unsignedTransaction: UnsignedTransaction,
    signedTransaction: SignedTransaction
  ) = JsonRpcRequest(
    method = EbsiRpcMethod.sendSignedTransaction,
    params = listOf(SignedTransactionParams(
      "eth",
      unsignedTransaction,
      signedTransaction.r,
      signedTransaction.s,
      signedTransaction.v,
      signedTransaction.signedRawTransaction
    )),
    id = requestId
  )

  suspend fun generateAddVerificationMethodRequest(
    requestId: Int,
    did: String, from: String, secp256Key: Key
  ) = JsonRpcRequest(
    method = EbsiRpcMethod.addVerificationMethod,
    params = listOf(AddVerificationMethodParams(
      from, did,
      secp256Key.getThumbprint(), secp256Key.keyType == KeyType.secp256k1,
      when(secp256Key.keyType) {
        KeyType.secp256k1 -> Utils.getPublicKeyXYRepresentation(secp256Key).toHexString().let { "0x04$it" }
        else -> secp256Key.getPublicKey().exportJWK().toByteArray().toHexString().let { "0x$it" }
      }
    )),
    id = requestId)

  fun generateAddVerificationRelationshipRequest(
    requestId: Int,
    did: String, from: String, name: String, vMethodId: String,
    notBefore: Instant = Clock.System.now(), notAfter: Instant = notBefore.plus(365*24, DateTimeUnit.HOUR)
  ) = JsonRpcRequest(
    method = EbsiRpcMethod.addVerificationRelationship,
    params = listOf(AddVerificationRelationshipParams(
      from, did, name, vMethodId, notBefore.epochSeconds, notAfter.epochSeconds
    )),
    id = requestId)
}
