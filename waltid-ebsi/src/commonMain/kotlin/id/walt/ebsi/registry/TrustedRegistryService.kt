package id.walt.ebsi.registry

import id.walt.ebsi.eth.SignedTransaction
import id.walt.ebsi.eth.UnsignedTransaction
import id.walt.ebsi.rpc.JsonRpcRequest
import kotlinx.serialization.json.JsonElement

object TrustedRegistryService {
  fun getAccessToken(scope: TrustedRegistryScope, credential: JsonElement?): String {
    TODO()
  }

  fun getTransactionToSign(rpcRequest: JsonRpcRequest): UnsignedTransaction {
    TODO()
  }

  fun submitSignedTransaction(signedTransaction: SignedTransaction, unsignedTransaction: UnsignedTransaction): String {
    TODO()
  }
}