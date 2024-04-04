package id.walt.ebsi.rpc
import id.walt.ebsi.eth.UnsignedTransaction
import kotlinx.serialization.Serializable

interface JsonRpcResponse

@Serializable
data class UnsignedTransactionResponse(val jsonrpc: String, val id: Int, val result: UnsignedTransaction) :
  JsonRpcResponse

@Serializable
data class SignedTransactionResponse(val jsonrpc: String, val id: Int, val result: String) : JsonRpcResponse