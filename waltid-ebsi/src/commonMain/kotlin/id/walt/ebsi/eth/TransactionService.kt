package id.walt.ebsi.eth

import id.walt.crypto.keys.Key
import id.walt.crypto.utils.ECDSASignature

expect object TransactionService {
  suspend fun signTransaction(key: Key, unsignedTransaction: UnsignedTransaction): SignedTransaction
}