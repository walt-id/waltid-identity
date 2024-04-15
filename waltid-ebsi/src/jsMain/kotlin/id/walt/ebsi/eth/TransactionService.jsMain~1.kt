package id.walt.ebsi.eth

import id.walt.crypto.keys.Key
import id.walt.crypto.utils.ECDSASignature

actual object TransactionService {
  actual suspend fun signTransaction(
    key: Key,
    unsignedTransaction: UnsignedTransaction
  ): SignedTransaction {
    TODO("Not yet implemented")
  }
}